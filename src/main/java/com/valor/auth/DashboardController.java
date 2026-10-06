package com.valor.auth;

import com.valor.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.persistence.EntityManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
class DashboardController {
    private final EntityManager em;private final AssetIdentityAccess identities;
    DashboardController(EntityManager em,AssetIdentityAccess identities){this.em=em;this.identities=identities;}
    record Summary(long unassignedRequests,long inProgressRequests,long emergencyJobs) {}
    private long count(String query){return em.createQuery(query,Long.class).getSingleResult();}
    @Operation(operationId="getAdminDashboardSummary") @GetMapping("/api/v1/admin/dashboard/summary") @Transactional(readOnly=true)
    ApiResponse<Summary> summary(){
        identities.requireAdmin();
        return ApiResponse.success("Success",new Summary(
          count("select count(r) from ServiceRequest r where r.status=com.valor.workflow.RequestStatus.PENDING and not exists (select a.id from TechnicianAssignment a where a.request=r and a.status in (com.valor.workflow.AssignmentStatus.ASSIGNED, com.valor.workflow.AssignmentStatus.ACCEPTED))"),
          count("select count(r) from ServiceRequest r where r.status in (com.valor.workflow.RequestStatus.ACCEPTED, com.valor.workflow.RequestStatus.ON_THE_WAY, com.valor.workflow.RequestStatus.REACHED_SITE, com.valor.workflow.RequestStatus.DIAGNOSIS, com.valor.workflow.RequestStatus.REPAIR_IN_PROGRESS, com.valor.workflow.RequestStatus.WAITING_FOR_PARTS, com.valor.workflow.RequestStatus.TESTING)"),
          count("select count(r) from ServiceRequest r where r.priority=com.valor.workflow.RequestPriority.EMERGENCY and r.status not in (com.valor.workflow.RequestStatus.COMPLETED, com.valor.workflow.RequestStatus.CANCELLED)")),200);
    }
}
