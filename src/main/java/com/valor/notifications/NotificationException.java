package com.valor.notifications;
class NotificationException extends RuntimeException {
    final int status;
    NotificationException(int status, String message) { super(message); this.status = status; }
}
