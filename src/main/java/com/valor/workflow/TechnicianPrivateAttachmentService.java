package com.valor.workflow;

import com.valor.auth.*;
import java.io.*;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
class TechnicianPrivateAttachmentService {
    private static final long MAX_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    private final TechnicianPrivateAttachmentRepository attachments; private final AssetIdentityAccess identities;
    private final WorkflowIdentityAccess profiles; private final RequestAttachmentStorage storage;
    TechnicianPrivateAttachmentService(TechnicianPrivateAttachmentRepository attachments, AssetIdentityAccess identities,
            WorkflowIdentityAccess profiles, RequestAttachmentStorage storage) {
        this.attachments = attachments; this.identities = identities; this.profiles = profiles; this.storage = storage;
    }
    List<View> mine() { return list(profiles.technician(identities.actor()).getId()); }
    List<View> list(Long technicianId) { authorize(technicianId); return attachments.findByTechnicianIdOrderByCreatedAtAscIdAsc(technicianId).stream().map(this::view).toList(); }
    View uploadMine(MultipartFile file) { return upload(profiles.technician(identities.actor()).getId(), file); }
    View upload(Long technicianId, MultipartFile file) {
        TechnicianProfile technician = authorize(technicianId); validate(file);
        try {
            byte[] bytes = file.getBytes(); String type = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
            var stored = storage.store(extension(type), new ByteArrayInputStream(bytes));
            TechnicianPrivateAttachment row = new TechnicianPrivateAttachment(); row.technician = technician; row.uploadedBy = identities.actor();
            row.originalFilename = filename(file.getOriginalFilename()); row.contentType = type; row.fileSize = file.getSize(); row.storageKey = stored.key();
            return view(attachments.saveAndFlush(row));
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
    Download download(Long technicianId, Long id) {
        authorize(technicianId); TechnicianPrivateAttachment row = row(technicianId, id);
        return new Download(row.originalFilename, row.contentType, row.fileSize, storage.load(row.storageKey));
    }
    void delete(Long technicianId, Long id) {
        authorize(technicianId); TechnicianPrivateAttachment row = row(technicianId, id); attachments.delete(row);
        try { storage.delete(row.storageKey); } catch (IOException ignored) {}
    }
    private TechnicianProfile authorize(Long technicianId) {
        User actor = identities.actor();
        if (actor.getRole() == Role.TECHNICIAN) {
            TechnicianProfile own = profiles.technician(actor); if (!own.getId().equals(technicianId)) throw denied(); return own;
        }
        if (actor.getRole() == Role.ADMIN || actor.getRole() == Role.SUPER_ADMIN) return profiles.activeTechnician(technicianId);
        throw denied();
    }
    private TechnicianPrivateAttachment row(Long technicianId, Long id) {
        TechnicianPrivateAttachment row = attachments.findById(id).orElseThrow(TechnicianPrivateAttachmentService::missing);
        if (!row.technician.getId().equals(technicianId)) throw missing(); return row;
    }
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new WorkflowException(400, "Attachment file is required");
        String type = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new WorkflowException(400, "Unsupported attachment type");
        if (file.getSize() <= 0 || file.getSize() > MAX_BYTES) throw new WorkflowException(400, "Attachment exceeds size limit");
    }
    private View view(TechnicianPrivateAttachment row) { return new View(row.id, row.technician.getId(), row.originalFilename, row.contentType, row.fileSize, row.uploadedBy.getId(), row.createdAt); }
    private String extension(String type) { return switch (type) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".pdf"; }; }
    private String filename(String value) { String name = value == null || value.isBlank() ? "attachment" : value.replaceAll("[\\\\/\\r\\n]", "_").trim(); return name.length() > 255 ? name.substring(name.length() - 255) : name; }
    private static WorkflowException missing() { return new WorkflowException(404, "Private attachment not found"); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
    record View(Long id, Long technicianProfileId, String originalFilename, String contentType, Long fileSize, Long uploadedByUserId, java.time.LocalDateTime createdAt) {}
    record Download(String filename, String contentType, Long size, Resource resource) {}
}
