package com.valor.tracking;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

final class TrackingDtos {
    private TrackingDtos() {}
    interface StrictInput {
        @JsonAnySetter default void rejectUnknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported tracking field"); }
    }
    record LocationUpdate(@NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        LocalDateTime timestamp) implements StrictInput {}
    record LocationView(BigDecimal latitude, BigDecimal longitude, LocalDateTime timestamp, boolean stale, String trackingState) {}
}
