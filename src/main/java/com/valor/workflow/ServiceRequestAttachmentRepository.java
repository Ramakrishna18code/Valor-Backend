package com.valor.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ServiceRequestAttachmentRepository extends JpaRepository<ServiceRequestAttachment, Long> {
    List<ServiceRequestAttachment> findByRequestIdOrderByCreatedAtAscIdAsc(Long requestId);
}
