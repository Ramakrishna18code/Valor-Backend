package com.valor.auth;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Component;

@Component
class TechnicianApplicationStorage {
    private final Path root;
    TechnicianApplicationStorage(@Value("${app.technician-applications.local-root:./data/technician-applications}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }
    String store(String extension, InputStream input) throws IOException {
        Files.createDirectories(root);
        String key = UUID.randomUUID() + extension;
        Files.copy(input, root.resolve(key), StandardCopyOption.REPLACE_EXISTING);
        return key;
    }
    Resource load(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return new FileSystemResource(path);
    }
}
