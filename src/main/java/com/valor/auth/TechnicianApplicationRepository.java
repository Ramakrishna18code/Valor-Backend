package com.valor.auth;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface TechnicianApplicationRepository extends JpaRepository<TechnicianApplication, Long> {
    Optional<TechnicianApplication> findByTokenHash(String tokenHash);
    Page<TechnicianApplication> findAllByOrderByCreatedAtDesc(Pageable pageable);
    boolean existsByEmailIgnoreCaseAndStatusIn(String email, Collection<String> statuses);
}

interface TechnicianApplicationDocumentRepository extends JpaRepository<TechnicianApplicationDocument, Long> {
    List<TechnicianApplicationDocument> findByApplicationIdOrderByCreatedAtAsc(Long applicationId);
    Optional<TechnicianApplicationDocument> findByApplicationIdAndDocumentType(Long applicationId, String documentType);
}
