package com.valor.workflow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
class LocalRequestAttachmentStorage implements RequestAttachmentStorage {
    private final Path root;
    LocalRequestAttachmentStorage(@Value("${app.attachments.local-root:./data/request-attachments}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }
    public StoredFile store(String extension, InputStream input) throws IOException {
        Files.createDirectories(root);
        String suffix = extension == null || extension.isBlank() ? "" : extension;
        Path target = root.resolve(UUID.randomUUID() + suffix).normalize();
        if (!target.startsWith(root)) throw new IOException("Invalid storage path");
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        return new StoredFile(root.relativize(target).toString().replace('\\', '/'));
    }
    public Resource load(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return new FileSystemResource(target);
    }
    public void delete(String key) throws IOException {
        Path target = root.resolve(key).normalize();
        if (target.startsWith(root)) Files.deleteIfExists(target);
    }
}
