package com.valor.tracking;

class TrackingException extends RuntimeException {
    final int status;
    TrackingException(int status, String message) { super(message); this.status = status; }
}
