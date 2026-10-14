package com.valor.installation;

import com.valor.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "installation_request_documents")
@Getter @Setter
public class InstallationRequestDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "installation_request_id", nullable = false) private InstallationRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "uploaded_by_user_id", nullable = false) private User uploadedBy;
    @Column(name = "document_type", nullable = false, length = 80) private String documentType;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "file_size", nullable = false) private Long fileSize;
    @Column(name = "storage_key", nullable = false, unique = true, length = 500) private String storageKey;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist void created() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
