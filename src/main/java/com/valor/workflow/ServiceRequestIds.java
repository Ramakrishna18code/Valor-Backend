package com.valor.workflow;

/** Public reference for a persisted service request. */
public final class ServiceRequestIds {
    private ServiceRequestIds() {}

    public static String forRequest(Long liftId, Long requestId) {
        if (requestId == null) throw new IllegalArgumentException("A saved request ID is required");
        return liftId == null ? "SR-NEW-" + requestId : "SR-LFT-" + liftId + "-" + requestId;
    }
}
