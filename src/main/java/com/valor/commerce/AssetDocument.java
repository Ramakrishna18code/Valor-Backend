package com.valor.commerce;

import com.valor.auth.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "AssetDocument")
@Table(name = "asset_documents")
@Getter @Setter
class AssetDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "owner_type", nullable = false, length = 20)
    private AssetDocumentOwnerType ownerType;
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedBy;
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    @Column(name = "storage_key", nullable = false, unique = true, length = 160)
    private String storageKey;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @PrePersist void pre() { createdAt = LocalDateTime.now(); }
}
