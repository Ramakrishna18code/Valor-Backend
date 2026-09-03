package com.valor.request;

import com.valor.enums.InventoryTransactionType;
import jakarta.validation.constraints.NotNull;

public record InventoryTransactionRequest(
        @NotNull(message = "Transaction type is required") InventoryTransactionType transactionType,
        @NotNull(message = "Quantity is required") Integer quantity,
        String referenceNumber,
        String remarks
) {
}