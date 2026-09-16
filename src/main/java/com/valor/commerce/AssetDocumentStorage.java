package com.valor.commerce;

import java.io.*;
import org.springframework.core.io.Resource;

interface AssetDocumentStorage {
    StoredAssetDocument store(String extension, InputStream input) throws IOException;
    Resource load(String key);
    void delete(String key) throws IOException;
}
record StoredAssetDocument(String key) {}
