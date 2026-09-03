package com.valor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InventoryRequest(
        @NotBlank(message = "Item name is required") String itemName,
        String sku,
        @NotNull(message = "Stock quantity is required") Integer stockQuantity,
        Integer reorderLevel,
        String unit,
        String location
) {
}