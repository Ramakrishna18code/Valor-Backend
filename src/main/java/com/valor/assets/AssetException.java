package com.valor.assets;

class AssetException extends RuntimeException {
    private final int status;
    AssetException(int status, String message) { super(message); this.status = status; }
    int status() { return status; }
}
