package com.valor.commerce;

class CommerceException extends RuntimeException {
    final int status;
    CommerceException(int status, String message) { super(message); this.status = status; }
}
