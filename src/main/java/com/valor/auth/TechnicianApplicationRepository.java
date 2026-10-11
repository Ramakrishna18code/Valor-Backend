package com.valor.auth;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface TechnicianApplicationRepository extends JpaRepository<TechnicianApplication, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from TechnicianApplication a where a.id = :id")
    Optional<TechnicianApplication> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<TechnicianApplication> findByTokenHash(String tokenHash);
    Page<TechnicianApplication> findAllByOrderByCreatedAtDesc(Pageable pageable);
    boolean existsByEmailIgnoreCaseAndStatusIn(String email, Collection<String> statuses);
    Optional<TechnicianApplication> findByEmailIgnoreCase(String email);
}

interface TechnicianApplicationDocumentRepository extends JpaRepository<TechnicianApplicationDocument, Long> {
    List<TechnicianApplicationDocument> findByApplicationIdOrderByCreatedAtAsc(Long applicationId);
    Optional<TechnicianApplicationDocument> findByApplicationIdAndDocumentType(Long applicationId, String documentType);
}
