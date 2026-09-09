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
    record Summary(long totalCustomers,long totalLifts,long totalRequests,long pendingJobs,long completedJobs,
                   long emergencyJobs,long totalTechnicians,long totalAmcs) {}
    private long count(String query){return em.createQuery(query,Long.class).getSingleResult();}
    @Operation(operationId="getAdminDashboardSummary") @GetMapping("/api/v1/admin/dashboard/summary") @Transactional(readOnly=true)
    ApiResponse<Summary> summary(){
        identities.requireAdmin();
        return ApiResponse.success("Success",new Summary(count("select count(c) from CustomerProfile c"),count("select count(l) from Lift l"),
          count("select count(r) from ServiceRequest r"),count("select count(r) from ServiceRequest r where r.status=com.valor.workflow.RequestStatus.PENDING"),
          count("select count(r) from ServiceRequest r where r.status=com.valor.workflow.RequestStatus.COMPLETED"),
          count("select count(r) from ServiceRequest r where r.priority=com.valor.workflow.RequestPriority.EMERGENCY"),
          count("select count(t) from TechnicianProfile t"),count("select count(a) from AmcContract a")),200);
    }
}
