package com.valor.repository;

import com.valor.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Query("""
            select i from Inventory i
            where lower(coalesce(i.itemName, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(i.sku, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(i.location, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(i.unit, '')) like lower(concat('%', :term, '%'))
            """)
    Page<Inventory> search(String term, Pageable pageable);

    List<Inventory> findByStockQuantityLessThanEqual(Integer threshold);
}