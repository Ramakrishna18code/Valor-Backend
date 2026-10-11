package com.valor.parts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

final class PartsDtos {
    private PartsDtos() {}
    record CategoryWrite(@NotBlank @Size(max=120) String name, @Size(max=500) String description, Boolean active) {}
    record ItemWrite(@NotNull @Positive Long categoryId, @NotBlank @Size(max=180) String name, @NotBlank @Size(max=80) String sku,
            @Size(max=1000) String description, @NotBlank @Size(max=40) String unit, @PositiveOrZero Integer reorderThreshold,
            Boolean active, @Size(max=500) String compatibilityMetadata) {}
    record StockMovementWrite(@NotNull StockMovementType movementType, @Positive Integer quantity, @Size(max=500) String reason) {}
    record LineWrite(@NotNull @Positive Long partItemId, @Positive Integer quantity) {}
    record RequestWrite(@NotNull @Positive Long serviceRequestId, @Size(max=1000) String notes, @NotEmpty List<@Valid LineWrite> lines) {}
    record DecisionWrite(@Size(max=1000) String reason) {}
    record CategoryView(Long id, String name, String description, boolean active) {}
    record ItemView(Long id, Long categoryId, String categoryName, String name, String sku, String description, String unit,
            int quantityOnHand, int reorderThreshold, boolean active, String compatibilityMetadata, boolean available) {}
    record LineView(Long id, Long partItemId, String itemName, String sku, int requestedQuantity, int approvedQuantity, int issuedQuantity) {}
    record RequestView(Long id, Long serviceRequestId, Long technicianProfileId, String technicianName, PartRequestStatus status,
            String notes, LocalDateTime createdAt, LocalDateTime submittedAt, LocalDateTime approvedAt, LocalDateTime rejectedAt,
            LocalDateTime reservedAt, LocalDateTime issuedAt, List<LineView> lines, List<EventView> events) {}
    record EventView(Long id, PartRequestStatus previousStatus, PartRequestStatus newStatus, Long actorUserId, String reason, LocalDateTime createdAt) {}
        record CustomerStatusView(Long requestId, PartRequestStatus status, String label) {}
}
