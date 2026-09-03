package com.valor.service.impl;

import com.valor.entity.Inventory;
import com.valor.entity.InventoryTransaction;
import com.valor.enums.InventoryTransactionType;
import com.valor.exception.ResourceNotFoundException;
import com.valor.repository.InventoryRepository;
import com.valor.repository.InventoryTransactionRepository;
import com.valor.request.InventoryRequest;
import com.valor.request.InventoryTransactionRequest;
import com.valor.service.InventoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;

    public InventoryServiceImpl(InventoryRepository inventoryRepository, InventoryTransactionRepository inventoryTransactionRepository) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
    }

    @Override
    public Inventory createInventory(InventoryRequest request) {
        Inventory inventory = new Inventory();
        inventory.setItemName(request.itemName());
        inventory.setSku(request.sku());
        inventory.setStockQuantity(request.stockQuantity());
        inventory.setReorderLevel(request.reorderLevel());
        inventory.setUnit(request.unit());
        inventory.setLocation(request.location());
        return inventoryRepository.save(inventory);
    }

    @Override
    public Inventory updateInventory(Long id, InventoryRequest request) {
        Inventory inventory = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found"));
        inventory.setItemName(request.itemName());
        inventory.setSku(request.sku());
        inventory.setStockQuantity(request.stockQuantity());
        inventory.setReorderLevel(request.reorderLevel());
        inventory.setUnit(request.unit());
        inventory.setLocation(request.location());
        return inventoryRepository.save(inventory);
    }

    @Override
    public Inventory getInventory(Long id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found"));
    }

    @Override
    public List<Inventory> getAllInventories() {
        return inventoryRepository.findAll();
    }

    @Override
    public Page<Inventory> searchInventories(String term, Pageable pageable) {
        return inventoryRepository.search(term, pageable);
    }

    @Override
    public List<Inventory> getLowStockInventories() {
        return inventoryRepository.findByStockQuantityLessThanEqual(0);
    }

    @Override
    public InventoryTransaction addTransaction(Long inventoryId, InventoryTransactionRequest request) {
        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found"));

        int currentStock = inventory.getStockQuantity() == null ? 0 : inventory.getStockQuantity();
        int quantity = request.quantity();
        InventoryTransactionType transactionType = request.transactionType();

        if (transactionType == InventoryTransactionType.PURCHASE || transactionType == InventoryTransactionType.RETURN) {
            inventory.setStockQuantity(currentStock + quantity);
        } else {
            inventory.setStockQuantity(Math.max(0, currentStock - quantity));
        }
        inventoryRepository.save(inventory);

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setInventory(inventory);
        transaction.setTransactionType(transactionType);
        transaction.setQuantity(quantity);
        transaction.setReferenceNumber(request.referenceNumber());
        transaction.setTransactionDateTime(LocalDateTime.now());
        transaction.setRemarks(request.remarks());
        return inventoryTransactionRepository.save(transaction);
    }

    @Override
    public List<InventoryTransaction> getTransactions(Long inventoryId) {
        return inventoryTransactionRepository.findByInventoryIdOrderByTransactionDateTimeDesc(inventoryId);
    }
}