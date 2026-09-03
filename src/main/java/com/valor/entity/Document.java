package com.valor.entity;

import com.valor.audit.AuditableEntity;
import com.valor.enums.DocumentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private String filePath;
    private String mimeType;
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    private String relatedEntityType;
    private Long relatedEntityId;
}