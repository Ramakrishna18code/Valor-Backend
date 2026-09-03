package com.valor.response;

import com.valor.enums.InventoryTransactionType;

import java.time.LocalDateTime;

public record InventoryTransactionResponse(
        Long id,
        Long inventoryId,
        InventoryTransactionType transactionType,
        Integer quantity,
        String referenceNumber,
        LocalDateTime transactionDateTime,
        String remarks
) {
}