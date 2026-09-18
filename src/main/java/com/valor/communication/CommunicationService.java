package com.valor.communication;

import com.valor.auth.*;
import com.valor.notifications.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.valor.communication.CommunicationDtos.*;

@Service
@Transactional
public class CommunicationService {
    private static final Logger log = LoggerFactory.getLogger(CommunicationService.class);
    private final CommunicationTemplateRepository templates; private final CommunicationPreferenceRepository preferences;
    private final CommunicationEventRepository events; private final CommunicationMessageRepository messages; private final CommunicationUserRepository users;
    private final EmailProvider email; private final SmsProvider sms; private final WhatsAppProvider whatsapp; private final NotificationService notifications;
    private final AssetIdentityAccess identities; private final Clock clock;

    public CommunicationService(CommunicationTemplateRepository templates, CommunicationPreferenceRepository preferences,
            CommunicationEventRepository events, CommunicationMessageRepository messages, CommunicationUserRepository users,
            EmailProvider email, SmsProvider sms, WhatsAppProvider whatsapp, NotificationService notifications,
            AssetIdentityAccess identities, Clock clock) {
        this.templates = templates; this.preferences = preferences; this.events = events; this.messages = messages; this.users = users;
        this.email = email; this.sms = sms; this.whatsapp = whatsapp; this.notifications = notifications; this.identities = identities; this.clock = clock;
    }

    public List<MessageView> enqueue(SendRequest input) {
        identities.requireAdmin();
        User recipient = users.findById(input.recipientUserId()).orElseThrow(() -> new CommunicationException(404, "Recipient not found"));
        CommunicationEvent event = events.findByIdempotencyKey(input.idempotencyKey()).orElseGet(() -> {
            CommunicationEvent e = new CommunicationEvent(); e.eventType = input.eventType(); e.recipient = recipient;
            e.idempotencyKey = input.idempotencyKey(); e.payload = payload(input.variables()); return events.save(e);
        });
        List<MessageView> result = new ArrayList<>();
        for (CommunicationChannel channel : input.channels()) if (allowed(recipient, channel)) {
            Optional<CommunicationMessage> existing = messages.findByEventIdAndChannel(event.id, channel);
            if (existing.isPresent()) { result.add(view(existing.get())); continue; }
            Optional<CommunicationTemplate> template = templates.findTopByEventTypeAndChannelAndActiveTrueOrderByUpdatedAtDesc(input.eventType(), channel);
            CommunicationMessage m = new CommunicationMessage(); m.event = event; m.recipient = recipient; m.channel = channel;
            m.template = template.orElse(null); m.templateKey = input.templateKey() != null ? input.templateKey() : template.map(t -> t.templateKey).orElse(input.eventType());
            m.subject = render(template.map(t -> t.subject).orElse(input.eventType()), input.variables());
            m.body = render(template.map(t -> t.body).orElse("{{eventType}}"), withEvent(input.variables(), input.eventType()));
            m.recipientMasked = masked(recipient, channel);
            result.add(view(messages.saveAndFlush(m)));
        }
        if (result.isEmpty()) result.addAll(messages.list(null, PageRequest.of(0, 100)).stream()
                .filter(m -> m.event.id.equals(event.id)).map(this::view).toList());
        return result;
    }

    public MessageView process(Long id) {
        CommunicationMessage message = messages.detail(id).orElseThrow(() -> new CommunicationException(404, "Communication message not found"));
        if (message.status == CommunicationStatus.SENT || message.status == CommunicationStatus.DELIVERED || message.status == CommunicationStatus.CANCELLED) return view(message);
        message.status = CommunicationStatus.PROCESSING;
        ProviderResult result = send(message);
        LocalDateTime now = LocalDateTime.now(clock);
        message.provider = result.provider(); message.providerMessageId = result.providerMessageId();
        if (result.success()) {
            message.status = CommunicationStatus.SENT; message.sentAt = now; message.failureReason = null; message.nextRetryAt = null;
        } else {
            message.status = CommunicationStatus.FAILED; message.failedAt = now; message.failureReason = safeFailure(result.failureReason());
            message.retryCount++; message.nextRetryAt = message.retryCount < message.maxRetryCount ? now.plusMinutes(Math.min(60, 5L * message.retryCount)) : null;
        }
        log.info("communication status event={} channel={} status={} provider={} messageId={} recipient={} retry={} failure={}",
                message.event.eventType, message.channel, message.status, message.provider, message.providerMessageId, message.recipientMasked, message.retryCount, message.failureReason);
        return view(message);
    }

    public int processDueRetries(int limit) {
        var due = messages.dueRetries(LocalDateTime.now(clock), PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
        due.forEach(m -> process(m.id)); return due.getNumberOfElements();
    }

    @Transactional(readOnly = true) public PageView list(CommunicationStatus status, int page, int size) {
        identities.requireAdmin(); if (page < 0 || size < 1 || size > 100) throw new CommunicationException(400, "Invalid page");
        var rows = messages.list(status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new PageView(rows.getContent().stream().map(this::view).toList(), rows.getNumber(), rows.getSize(), rows.getTotalElements(), rows.getTotalPages());
    }

    public PreferenceView preferences(Long userId, PreferenceRequest input) {
        identities.requireAdmin(); User user = users.findById(userId).orElseThrow(() -> new CommunicationException(404, "User not found"));
        CommunicationPreference pref = preferences.findByUserId(userId).orElseGet(() -> { CommunicationPreference p = new CommunicationPreference(); p.user = user; return p; });
        pref.emailEnabled = input.emailEnabled(); pref.smsEnabled = input.smsEnabled(); pref.whatsappEnabled = input.whatsappEnabled(); pref.inAppEnabled = input.inAppEnabled();
        return view(preferences.saveAndFlush(pref));
    }

    @Transactional(readOnly = true) public PreferenceView preferences(Long userId) {
        identities.requireAdmin(); users.findById(userId).orElseThrow(() -> new CommunicationException(404, "User not found"));
        return preferences.findByUserId(userId).map(this::view).orElse(new PreferenceView(userId, true, true, true, true));
    }

    private ProviderResult send(CommunicationMessage m) {
        ProviderRequest request = new ProviderRequest(recipient(m.recipient, m.channel), m.subject, m.body, m.event.idempotencyKey + ":" + m.channel);
        return switch (m.channel) {
            case EMAIL -> email.send(request);
            case SMS -> sms.send(request);
            case WHATSAPP -> whatsapp.send(request);
            case IN_APP -> { notifications.createSystem(m.recipient.getId(), m.subject == null ? m.event.eventType : m.subject, m.body); yield ProviderResult.ok("IN_APP"); }
        };
    }
    private boolean allowed(User user, CommunicationChannel channel) {
        CommunicationPreference p = preferences.findByUserId(user.getId()).orElse(null);
        if (p == null) return true;
        return switch (channel) { case EMAIL -> p.emailEnabled; case SMS -> p.smsEnabled; case WHATSAPP -> p.whatsappEnabled; case IN_APP -> p.inAppEnabled; };
    }
    private static String recipient(User user, CommunicationChannel channel) { return channel == CommunicationChannel.EMAIL ? user.getEmail() : user.getPhone(); }
    static String masked(User user, CommunicationChannel channel) {
        String value = recipient(user, channel);
        if (value == null || value.isBlank()) return "unavailable";
        if (channel == CommunicationChannel.EMAIL) {
            int at = value.indexOf('@'); if (at <= 1) return "***" + (at >= 0 ? value.substring(at) : "");
            return value.charAt(0) + "***" + value.substring(at);
        }
        String digits = value.replaceAll("\\D", ""); if (digits.length() <= 4) return "****";
        return "+" + digits.substring(0, Math.min(2, digits.length() - 4)) + "******" + digits.substring(digits.length() - 4);
    }
    private static String render(String text, Map<String,String> variables) {
        String out = text == null ? "" : text;
        for (var e : Optional.ofNullable(variables).orElse(Map.of()).entrySet()) out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        return out;
    }
    private static Map<String,String> withEvent(Map<String,String> input, String eventType) { Map<String,String> map = new HashMap<>(Optional.ofNullable(input).orElse(Map.of())); map.putIfAbsent("eventType", eventType); return map; }
    private static String payload(Map<String,String> variables) { return Optional.ofNullable(variables).orElse(Map.of()).toString(); }
    private static String safeFailure(String reason) { return reason == null ? "Provider failed" : Pattern.compile("(?i)(key|secret|token|password)=\\S+").matcher(reason).replaceAll("$1=***"); }
    private MessageView view(CommunicationMessage m) { return new MessageView(m.id, m.event.id, m.event.eventType, m.recipient.getId(), m.recipientMasked, m.channel, m.status, m.provider, m.providerMessageId, m.templateKey, m.retryCount, m.maxRetryCount, m.nextRetryAt, m.failureReason, m.sentAt, m.deliveredAt, m.failedAt, m.createdAt, m.updatedAt); }
    private PreferenceView view(CommunicationPreference p) { return new PreferenceView(p.user.getId(), p.emailEnabled, p.smsEnabled, p.whatsappEnabled, p.inAppEnabled); }
}

class CommunicationException extends RuntimeException { final int status; CommunicationException(int status, String message) { super(message); this.status = status; } }
