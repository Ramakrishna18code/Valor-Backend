package com.valor.repository;

import com.valor.entity.Customer;
import com.valor.entity.Lift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LiftRepository extends JpaRepository<Lift, Long> {
    List<Lift> findByCustomer(Customer customer);
    List<Lift> findByBuildingId(Long buildingId);
    List<Lift> findByNameContainingIgnoreCase(String term);

    @Query("""
            select l from Lift l
            where lower(coalesce(l.name, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(l.liftNumber, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(l.model, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(l.manufacturer, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(l.serialNumber, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(l.location, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(l.customer.name, '')) like lower(concat('%', :term, '%'))
            """)
    Page<Lift> search(String term, Pageable pageable);
}
