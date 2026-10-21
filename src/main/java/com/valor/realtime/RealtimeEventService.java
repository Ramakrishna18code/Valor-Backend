package com.valor.realtime;

import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class RealtimeEventService {
    private final ApplicationEventPublisher events;
    private final SimpMessagingTemplate messaging;
    public RealtimeEventService(ApplicationEventPublisher events, SimpMessagingTemplate messaging) { this.events = events; this.messaging = messaging; }
    public void publish(String type, String entityType, Long entityId, String status, Set<Long> recipients) { events.publishEvent(new RealtimeEvent(type, entityType, entityId, status, null, recipients)); }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(RealtimeEvent event) {
        messaging.convertAndSend("/topic/admin", event);
        event.recipientUserIds().forEach(userId -> messaging.convertAndSendToUser(String.valueOf(userId), "/queue/valor-events", event));
    }
}
