package com.valor.response;

public record InventoryResponse(
        Long id,
        String itemName,
        String sku,
        Integer stockQuantity,
        Integer reorderLevel,
        String unit,
        String location
) {
}