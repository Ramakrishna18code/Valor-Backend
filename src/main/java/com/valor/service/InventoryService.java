package com.valor.service;

import com.valor.entity.Inventory;
import com.valor.entity.InventoryTransaction;
import com.valor.request.InventoryRequest;
import com.valor.request.InventoryTransactionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface InventoryService {
    Inventory createInventory(InventoryRequest request);
    Inventory updateInventory(Long id, InventoryRequest request);
    Inventory getInventory(Long id);
    List<Inventory> getAllInventories();
    Page<Inventory> searchInventories(String term, Pageable pageable);
    List<Inventory> getLowStockInventories();
    InventoryTransaction addTransaction(Long inventoryId, InventoryTransactionRequest request);
    List<InventoryTransaction> getTransactions(Long inventoryId);
}