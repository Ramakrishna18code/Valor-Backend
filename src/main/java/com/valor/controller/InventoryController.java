package com.valor.controller;

import com.valor.entity.Inventory;
import com.valor.entity.InventoryTransaction;

import com.valor.request.InventoryRequest;
import com.valor.request.InventoryTransactionRequest;
import com.valor.response.ApiResponse;
import com.valor.response.InventoryResponse;
import com.valor.response.InventoryTransactionResponse;
import com.valor.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventory")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Inventory", description = "Inventory items, stock, and transactions")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Operation(summary = "Create inventory item")
    @PostMapping
    public ResponseEntity<ApiResponse<InventoryResponse>> createInventory(@Valid @RequestBody InventoryRequest request) {
        Inventory inventory = inventoryService.createInventory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Inventory item created", toResponse(inventory), HttpStatus.CREATED.value()));
    }

    @Operation(summary = "Update inventory item")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InventoryResponse>> updateInventory(@PathVariable Long id, @Valid @RequestBody InventoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Inventory item updated", toResponse(inventoryService.updateInventory(id, request)), HttpStatus.OK.value()));
    }

    @Operation(summary = "Get inventory item")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Inventory item fetched", toResponse(inventoryService.getInventory(id)), HttpStatus.OK.value()));
    }

    @Operation(summary = "List inventory items")
    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getAllInventory() {
        return ResponseEntity.ok(ApiResponse.success("Inventory items fetched", inventoryService.getAllInventories().stream().map(this::toResponse).collect(Collectors.toList()), HttpStatus.OK.value()));
    }

    @Operation(summary = "Search inventory")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<InventoryResponse>>> searchInventory(
            @RequestParam String term,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "itemName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<InventoryResponse> response = inventoryService.searchInventories(term, pageable).map(this::toResponse);
        return ResponseEntity.ok(ApiResponse.success("Inventory search completed", response, HttpStatus.OK.value()));
    }

    @Operation(summary = "Get low stock inventory")
    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getLowStockInventory() {
        return ResponseEntity.ok(ApiResponse.success("Low stock items fetched", inventoryService.getLowStockInventories().stream().map(this::toResponse).collect(Collectors.toList()), HttpStatus.OK.value()));
    }

    @Operation(summary = "Add inventory transaction")
    @PostMapping("/{id}/transactions")
    public ResponseEntity<ApiResponse<InventoryTransactionResponse>> addTransaction(@PathVariable Long id, @Valid @RequestBody InventoryTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Inventory transaction created", toResponse(inventoryService.addTransaction(id, request)), HttpStatus.CREATED.value()));
    }

    @Operation(summary = "List inventory transactions")
    @GetMapping("/{id}/transactions")
    public ResponseEntity<ApiResponse<List<InventoryTransactionResponse>>> getTransactions(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Inventory transactions fetched", inventoryService.getTransactions(id).stream().map(this::toResponse).collect(Collectors.toList()), HttpStatus.OK.value()));
    }

    private InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getItemName(),
                inventory.getSku(),
                inventory.getStockQuantity(),
                inventory.getReorderLevel(),
                inventory.getUnit(),
                inventory.getLocation()
        );
    }

    private InventoryTransactionResponse toResponse(InventoryTransaction transaction) {
        return new InventoryTransactionResponse(
                transaction.getId(),
                transaction.getInventory() == null ? null : transaction.getInventory().getId(),
                transaction.getTransactionType(),
                transaction.getQuantity(),
                transaction.getReferenceNumber(),
                transaction.getTransactionDateTime(),
                transaction.getRemarks()
        );
    }
}