package com.valor.repository;

import com.valor.entity.Technician;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TechnicianRepository extends JpaRepository<Technician, Long> {
    Optional<Technician> findByEmail(String email);

    @Query("""
            select t from Technician t
            where lower(coalesce(t.name, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(t.email, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(t.employeeId, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(t.assignedArea, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(t.specialization, '')) like lower(concat('%', :term, '%'))
            """)
    Page<Technician> search(String term, Pageable pageable);
}
