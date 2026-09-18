package com.valor.workflow;

import com.valor.auth.*;
import jakarta.persistence.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "technician_private_attachments")
class TechnicianPrivateAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "technician_profile_id") TechnicianProfile technician;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "uploaded_by_user_id") User uploadedBy;
    @Column(name = "original_filename", nullable = false, length = 255) String originalFilename;
    @Column(name = "content_type", nullable = false, length = 100) String contentType;
    @Column(name = "file_size", nullable = false) Long fileSize;
    @Column(name = "storage_key", nullable = false, unique = true, length = 500) String storageKey;
    @Column(name = "created_at", nullable = false) java.time.LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) java.time.LocalDateTime updatedAt;
    @PrePersist void create() { createdAt = java.time.LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = java.time.LocalDateTime.now(); }
}

interface TechnicianPrivateAttachmentRepository extends JpaRepository<TechnicianPrivateAttachment, Long> {
    List<TechnicianPrivateAttachment> findByTechnicianIdOrderByCreatedAtAscIdAsc(Long technicianId);
}
