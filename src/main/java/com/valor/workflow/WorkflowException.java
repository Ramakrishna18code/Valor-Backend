package com.valor.workflow;
class WorkflowException extends RuntimeException {
    final int status;
    WorkflowException(int status, String message) { super(message); this.status = status; }
}
