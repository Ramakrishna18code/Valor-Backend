package com.valor.workflow;

import com.valor.auth.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import static com.valor.workflow.WorkflowDtos.*;

@Service
@Transactional
class ServiceRequestEngagementService {
    private static final long MAX_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    private final RequestRepository requests;
    private final ServiceRequestAttachmentRepository attachments;
    private final ServiceRequestFeedbackRepository feedback;
    private final AssetIdentityAccess identities;
    private final WorkflowIdentityAccess profiles;
    private final RequestAttachmentStorage storage;
    ServiceRequestEngagementService(RequestRepository requests, ServiceRequestAttachmentRepository attachments,
            ServiceRequestFeedbackRepository feedback, AssetIdentityAccess identities, WorkflowIdentityAccess profiles,
            RequestAttachmentStorage storage) {
        this.requests = requests; this.attachments = attachments; this.feedback = feedback;
        this.identities = identities; this.profiles = profiles; this.storage = storage;
    }

    @Transactional(readOnly = true)
    List<AttachmentView> listAttachments(Long requestId) {
        ServiceRequest request = authorizedRead(requestId, identities.actor());
        return attachments.findByRequestIdOrderByCreatedAtAscIdAsc(request.getId()).stream().map(this::view).toList();
    }

    AttachmentView upload(Long requestId, MultipartFile file) {
        User actor = identities.actor();
        ServiceRequest request = authorizedRead(requestId, actor);
        validateCustomerOwnerForWrite(request, actor);
        if (file == null || file.isEmpty()) throw new WorkflowException(400, "Attachment file is required");
        String type = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new WorkflowException(400, "Unsupported attachment type");
        if (file.getSize() <= 0 || file.getSize() > MAX_BYTES) throw new WorkflowException(400, "Attachment exceeds size limit");
        try {
            byte[] bytes = file.getBytes();
            if (!looksLike(type, bytes)) throw new WorkflowException(400, "Attachment content does not match supported file type");
            var stored = storage.store(extension(type), new java.io.ByteArrayInputStream(bytes));
            ServiceRequestAttachment row = new ServiceRequestAttachment();
            row.setRequest(request); row.setUploadedBy(actor);
            row.setOriginalFilename(filename(file.getOriginalFilename()));
            row.setContentType(type); row.setFileSize(file.getSize()); row.setStorageKey(stored.key());
            return view(attachments.saveAndFlush(row));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Transactional(readOnly = true)
    Download download(Long requestId, Long attachmentId) {
        ServiceRequest request = authorizedRead(requestId, identities.actor());
        ServiceRequestAttachment row = attachments.findById(attachmentId).orElseThrow(ServiceRequestEngagementService::missing);
        if (!row.getRequest().getId().equals(request.getId())) throw missing();
        return new Download(row.getOriginalFilename(), row.getContentType(), row.getFileSize(), storage.load(row.getStorageKey()));
    }

    void delete(Long requestId, Long attachmentId) {
        User actor = identities.actor();
        ServiceRequest request = authorizedRead(requestId, actor);
        validateCustomerOwnerForWrite(request, actor);
        ServiceRequestAttachment row = attachments.findById(attachmentId).orElseThrow(ServiceRequestEngagementService::missing);
        if (!row.getRequest().getId().equals(request.getId()) || !row.getUploadedBy().getId().equals(actor.getId())) throw missing();
        attachments.delete(row);
        try { storage.delete(row.getStorageKey()); } catch (IOException ignored) { }
    }

    FeedbackView upsertFeedback(Long requestId, FeedbackWrite input) {
        User actor = identities.actor();
        CustomerProfile customer = profiles.customer(actor);
        ServiceRequest request = requests.findById(requestId).orElseThrow(ServiceRequestEngagementService::missing);
        if (!request.getCustomer().getId().equals(customer.getId())) throw denied();
        if (request.getStatus() != RequestStatus.COMPLETED) throw new WorkflowException(409, "Feedback is available after completion");
        ServiceRequestFeedback row = feedback.findByRequestId(requestId).orElseGet(ServiceRequestFeedback::new);
        row.setRequest(request); row.setCustomer(customer); row.setRating(input.rating());
        row.setComment(input.comment() == null || input.comment().isBlank() ? null : input.comment().trim());
        return view(feedback.saveAndFlush(row));
    }

    @Transactional(readOnly = true)
    FeedbackView ownFeedback(Long requestId) {
        User actor = identities.actor();
        CustomerProfile customer = profiles.customer(actor);
        ServiceRequest request = requests.findById(requestId).orElseThrow(ServiceRequestEngagementService::missing);
        if (!request.getCustomer().getId().equals(customer.getId())) throw denied();
        return feedback.findByRequestId(requestId).map(this::view).orElse(null);
    }

    private ServiceRequest authorizedRead(Long id, User actor) {
        ServiceRequest request = requests.findById(id).orElseThrow(ServiceRequestEngagementService::missing);
        if (actor.getRole() == Role.CUSTOMER) {
            CustomerProfile customer = profiles.customer(actor);
            if (!request.getCustomer().getId().equals(customer.getId())) throw denied();
        } else if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN) throw denied();
        return request;
    }
    private void validateCustomerOwnerForWrite(ServiceRequest request, User actor) {
        if (actor.getRole() != Role.CUSTOMER) throw denied();
        CustomerProfile customer = profiles.customer(actor);
        if (!request.getCustomer().getId().equals(customer.getId())) throw denied();
    }
    private AttachmentView view(ServiceRequestAttachment row) {
        return new AttachmentView(row.getId(), row.getRequest().getId(), row.getOriginalFilename(), row.getContentType(),
                row.getFileSize(), row.getUploadedBy().getId(), row.getCreatedAt());
    }
    private FeedbackView view(ServiceRequestFeedback row) {
        return new FeedbackView(row.getId(), row.getRequest().getId(), row.getCustomer().getId(), row.getRating(),
                row.getComment(), row.getCreatedAt(), row.getUpdatedAt());
    }
    private boolean looksLike(String type, byte[] bytes) {
        if (bytes.length < 4) return false;
        if (type.equals("application/pdf")) return bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
        if (type.equals("image/png")) return bytes.length >= 8 && bytes[0] == (byte)0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
        if (type.equals("image/jpeg")) return bytes[0] == (byte)0xFF && bytes[1] == (byte)0xD8;
        if (type.equals("image/webp")) return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        return false;
    }
    private String extension(String type) { return switch (type) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".pdf"; }; }
    private String filename(String value) { String name = value == null || value.isBlank() ? "attachment" : value.replaceAll("[\\\\/\\r\\n]", "_").trim(); return name.length() > 255 ? name.substring(name.length() - 255) : name; }
    private static WorkflowException missing() { return new WorkflowException(404, "Request attachment or feedback not found"); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
    record Download(String filename, String contentType, Long size, Resource resource) {}
}
