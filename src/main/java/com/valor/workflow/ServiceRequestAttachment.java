package com.valor.workflow;

import com.valor.assets.AssetRecord;
import com.valor.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "ServiceRequestAttachment")
@Table(name = "service_request_attachments")
@Getter @Setter
public class ServiceRequestAttachment extends AssetRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedBy;
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    private String storageKey;
}
