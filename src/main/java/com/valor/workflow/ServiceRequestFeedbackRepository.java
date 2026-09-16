package com.valor.workflow;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ServiceRequestFeedbackRepository extends JpaRepository<ServiceRequestFeedback, Long> {
    Optional<ServiceRequestFeedback> findByRequestId(Long requestId);
}
