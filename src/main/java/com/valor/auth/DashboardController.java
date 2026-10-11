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
    record Summary(long unassignedRequests,long inProgressRequests,long emergencyJobs,
                   long customerCount,long buildingCount,long liftCount,long amcCount,
                   long standardAmcCount,long premiumAmcCount,long comprehensiveAmcCount,
                   long technicianCount,long availableTechnicianCount,long busyTechnicianCount,
                   long onLeaveTechnicianCount,long notAvailableTechnicianCount,
                   long paymentCount,long pendingPaymentCount,long outstandingInvoiceCount,
                   long totalRequests) {}
    private long count(String query){return em.createQuery(query,Long.class).getSingleResult();}
    @Operation(operationId="getAdminDashboardSummary") @GetMapping("/api/v1/admin/dashboard/summary") @Transactional(readOnly=true)
    ApiResponse<Summary> summary(){
        identities.requireAdmin();
        long technicians = count("select count(t) from TechnicianProfile t");
        long availableTechnicians = count("select count(t) from TechnicianProfile t where t.active = true and t.user.active = true and t.user.locked = false and t.availabilityStatus = 'AVAILABLE'");
        long busyTechnicians = count("select count(t) from TechnicianProfile t where t.active = true and t.user.active = true and t.user.locked = false and t.availabilityStatus = 'BUSY'");
        long onLeaveTechnicians = count("select count(t) from TechnicianProfile t where t.active = true and t.user.active = true and t.user.locked = false and t.availabilityStatus = 'ON_LEAVE'");
        return ApiResponse.success("Success",new Summary(
          count("select count(r) from ServiceRequest r where r.status=com.valor.workflow.RequestStatus.PENDING and not exists (select a.id from TechnicianAssignment a where a.request=r and a.status in (com.valor.workflow.AssignmentStatus.ASSIGNED, com.valor.workflow.AssignmentStatus.ACCEPTED))"),
          count("select count(r) from ServiceRequest r where r.status in (com.valor.workflow.RequestStatus.ACCEPTED, com.valor.workflow.RequestStatus.ON_THE_WAY, com.valor.workflow.RequestStatus.REACHED_SITE, com.valor.workflow.RequestStatus.DIAGNOSIS, com.valor.workflow.RequestStatus.REPAIR_IN_PROGRESS, com.valor.workflow.RequestStatus.WAITING_FOR_PARTS, com.valor.workflow.RequestStatus.TESTING)"),
          count("select count(r) from ServiceRequest r where r.priority=com.valor.workflow.RequestPriority.EMERGENCY and r.status not in (com.valor.workflow.RequestStatus.COMPLETED, com.valor.workflow.RequestStatus.CANCELLED)"),
          count("select count(c) from CustomerProfile c"),
          count("select count(b) from Building b"),
          count("select count(l) from Lift l"),
          count("select count(a) from AmcContract a"),
          count("select count(a) from AmcContract a where lower(a.plan) like '%standard%'"),
          count("select count(a) from AmcContract a where lower(a.plan) like '%premium%'"),
          count("select count(a) from AmcContract a where lower(a.plan) like '%comprehensive%'"),
          technicians, availableTechnicians, busyTechnicians, onLeaveTechnicians,
          Math.max(0, technicians - availableTechnicians - busyTechnicians - onLeaveTechnicians),
          count("select count(p) from PaymentRecord p"),
          count("select count(p) from PaymentRecord p where p.status = com.valor.commerce.PaymentStatus.PENDING"),
          count("select count(i) from Invoice i where i.status not in (com.valor.commerce.InvoiceStatus.PAID, com.valor.commerce.InvoiceStatus.VOID, com.valor.commerce.InvoiceStatus.CANCELLED)"),
          count("select count(r) from ServiceRequest r")),200);
    }
}
