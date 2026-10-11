package com.valor.workflow;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** File signatures are a type check, not a malware or document-safety scan. */
final class AttachmentContent {
    private AttachmentContent() {}

    static boolean matches(String type, byte[] bytes) {
        return switch (type) {
            case "image/png" -> startsWith(bytes, new byte[]{(byte)137,80,78,71,13,10,26,10});
            case "image/jpeg" -> startsWith(bytes, new byte[]{(byte)255,(byte)216,(byte)255});
            case "application/pdf" -> startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "image/webp" -> bytes.length >= 12
                    && startsWith(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && Arrays.equals(Arrays.copyOfRange(bytes, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII));
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        return bytes.length >= signature.length
                && Arrays.equals(Arrays.copyOf(bytes, signature.length), signature);
    }
}
