package com.valor.auth;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "technician_application_documents")
class TechnicianApplicationDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "application_id") TechnicianApplication application;
    @Column(name = "document_type", nullable = false, length = 50) String documentType;
    @Column(name = "original_filename", nullable = false, length = 255) String originalFilename;
    @Column(name = "content_type", nullable = false, length = 120) String contentType;
    @Column(name = "file_size", nullable = false) Long fileSize;
    @Column(name = "storage_key", nullable = false, unique = true, length = 500) String storageKey;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}
