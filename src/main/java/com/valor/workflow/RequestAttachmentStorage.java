package com.valor.workflow;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;

interface RequestAttachmentStorage {
    StoredFile store(String extension, InputStream input) throws IOException;
    Resource load(String key);
    void delete(String key) throws IOException;
    record StoredFile(String key) {}
}
