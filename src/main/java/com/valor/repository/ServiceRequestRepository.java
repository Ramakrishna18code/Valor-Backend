package com.valor.repository;

import com.valor.entity.ServiceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

	List<ServiceRequest> findByCustomerIdOrderByServiceRequestedAtDesc(Long customerId);

	List<ServiceRequest> findByAssignedTechnicianIdOrderByServiceRequestedAtDesc(Long technicianId);

	@Query("""
			select s from ServiceRequest s
			where lower(coalesce(s.serviceId, '')) like lower(concat('%', :term, '%'))
			   or lower(coalesce(s.title, '')) like lower(concat('%', :term, '%'))
			   or lower(coalesce(s.issueCategory, '')) like lower(concat('%', :term, '%'))
			   or lower(coalesce(s.customer.name, '')) like lower(concat('%', :term, '%'))
			   or lower(coalesce(s.customer.phone, '')) like lower(concat('%', :term, '%'))
			   or lower(coalesce(s.lift.name, '')) like lower(concat('%', :term, '%'))
			   or lower(coalesce(s.lift.liftNumber, '')) like lower(concat('%', :term, '%'))
			   or lower(coalesce(s.assignedTechnician.name, '')) like lower(concat('%', :term, '%'))
			""")
	Page<ServiceRequest> search(String term, Pageable pageable);

	Page<ServiceRequest> findByStatus(com.valor.enums.JobStatus status, Pageable pageable);
}
