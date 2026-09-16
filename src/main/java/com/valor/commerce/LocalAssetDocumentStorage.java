package com.valor.commerce;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Component;

@Component
class LocalAssetDocumentStorage implements AssetDocumentStorage {
    private final Path root;
    LocalAssetDocumentStorage(@Value("${app.asset-documents.local-root:./data/asset-documents}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }
    public StoredAssetDocument store(String extension, InputStream input) throws IOException {
        Files.createDirectories(root);
        String key = UUID.randomUUID().toString() + extension;
        Files.copy(input, root.resolve(key), StandardCopyOption.REPLACE_EXISTING);
        return new StoredAssetDocument(key);
    }
    public Resource load(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return new FileSystemResource(path);
    }
    public void delete(String key) throws IOException {
        Path path = root.resolve(key).normalize();
        if (path.startsWith(root)) Files.deleteIfExists(path);
    }
}
