package com.valor.auth;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnicianSavedLocationRepo extends JpaRepository<TechnicianSavedLocation, Long> {
    List<TechnicianSavedLocation> findByTechnicianIdOrderByDefaultLocationDescUpdatedAtDesc(Long technicianId);
    long countByTechnicianId(Long technicianId);
}
