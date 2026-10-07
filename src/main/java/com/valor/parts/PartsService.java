package com.valor.parts;

import com.valor.auth.*;
import com.valor.workflow.ServiceRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.valor.parts.PartsDtos.*;

@Service
@Transactional
class PartsService {
    private final PartCategoryRepository categories; private final PartItemRepository items; private final PartMovementRepository movements;
    private final PartRequestRepository requests; private final PartLineRepository lines; private final PartEventRepository events;
    private final PartsTechnicianRepository technicians; private final PartsServiceRequestRepository serviceRequests;
    private final AssetIdentityAccess identities; private final AuditService audit;

    PartsService(PartCategoryRepository categories, PartItemRepository items, PartMovementRepository movements,
            PartRequestRepository requests, PartLineRepository lines, PartEventRepository events, PartsTechnicianRepository technicians,
            PartsServiceRequestRepository serviceRequests, AssetIdentityAccess identities, AuditService audit) {
        this.categories=categories; this.items=items; this.movements=movements; this.requests=requests; this.lines=lines; this.events=events;
        this.technicians=technicians; this.serviceRequests=serviceRequests; this.identities=identities; this.audit=audit;
    }

    List<CategoryView> categories(boolean admin) { if (!admin) return categories.findAll().stream().filter(c -> c.active).map(this::category).toList(); return categories.findAll().stream().map(this::category).toList(); }
    CategoryView saveCategory(Long id, CategoryWrite input) {
        requireAdmin(); PartCategory row = id == null ? new PartCategory() : categories.findById(id).orElseThrow(() -> missing("Category"));
        categories.findByNameIgnoreCase(input.name().trim()).filter(found -> !found.equals(row)).ifPresent(found -> { throw conflict("Category name already exists"); });
        row.name=input.name().trim(); row.description=input.description(); if (input.active()!=null) row.active=input.active();
        categories.saveAndFlush(row); audit.record(id == null ? "PART_CATEGORY_CREATE" : "PART_CATEGORY_UPDATE", "PART_CATEGORY", row.id, "Updated part category"); return category(row);
    }
    Page<ItemView> catalog(String query, Long categoryId, boolean admin, Pageable page) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        return items.search(normalizedQuery, categoryId, admin, page).map(this::item);
    }
    ItemView item(Long id) { return item(items.findWithCategoryById(id).orElseThrow(() -> missing("Part"))); }
    ItemView saveItem(Long id, ItemWrite input) {
        requireAdmin(); PartItem row=id == null ? new PartItem() : items.findById(id).orElseThrow(() -> missing("Part"));
        if (id == null && items.existsBySkuIgnoreCase(input.sku().trim()) || id != null && items.existsBySkuIgnoreCaseAndIdNot(input.sku().trim(), id)) throw conflict("SKU already exists");
        row.category=categories.findById(input.categoryId()).orElseThrow(() -> missing("Category")); row.name=input.name().trim(); row.sku=input.sku().trim();
        row.description=input.description(); row.unit=input.unit().trim(); row.reorderThreshold=input.reorderThreshold() == null ? 0 : input.reorderThreshold();
        row.compatibilityMetadata=input.compatibilityMetadata(); if (input.active()!=null) row.active=input.active(); items.saveAndFlush(row);
        audit.record(id == null ? "PART_CREATE" : "PART_UPDATE", "PART_ITEM", row.id, "Updated part item"); return item(row);
    }
    ItemView movement(Long id, StockMovementWrite input) {
        requireAdmin(); PartItem row=items.lockById(id).orElseThrow(() -> missing("Part")); int quantity=input.quantity();
        if (input.movementType() != StockMovementType.RESTOCK && input.movementType() != StockMovementType.RETURN && input.movementType() != StockMovementType.ADJUSTMENT) throw conflict("Use request issue flow for outbound stock");
        int next=input.movementType() == StockMovementType.ADJUSTMENT ? quantity : Math.addExact(row.quantityOnHand, quantity);
        if (next < 0) throw conflict("Stock cannot be negative"); row.quantityOnHand=next; items.saveAndFlush(row);
        PartStockMovement movement=new PartStockMovement(); movement.item=row; movement.movementType=input.movementType(); movement.quantity=quantity; movement.reason=input.reason(); movement.actorUserId=identities.actor().getId(); movements.save(movement);
        audit.record("PART_STOCK_MOVEMENT", "PART_ITEM", id, "Stock movement " + input.movementType()); return item(row);
    }
    List<PartStockMovement> movements(Long id) { requireAdmin(); return movements.findByItemIdOrderByCreatedAtDesc(id); }

    RequestView submit(RequestWrite input) {
        User actor=identities.actor(); TechnicianProfile technician=technicians.findByUserId(actor.getId()).orElseThrow(() -> denied());
        ServiceRequest serviceRequest=serviceRequests.findById(input.serviceRequestId()).orElseThrow(() -> missing("Service request"));
        if (serviceRequest.getStatus() == com.valor.workflow.RequestStatus.COMPLETED || serviceRequest.getStatus() == com.valor.workflow.RequestStatus.CANCELLED) throw conflict("Service request is closed");
        ServiceRequestPartRequest request=new ServiceRequestPartRequest(); request.serviceRequest=serviceRequest; request.technician=technician; request.notes=input.notes(); request.status=PartRequestStatus.SUBMITTED; request.submittedAt=LocalDateTime.now(); requests.saveAndFlush(request);
        for (LineWrite line : input.lines()) { PartItem item=items.findById(line.partItemId()).orElseThrow(() -> missing("Part")); if (!item.active) throw conflict("Part is inactive"); ServiceRequestPartLine row=new ServiceRequestPartLine(); row.request=request; row.item=item; row.requestedQuantity=line.quantity(); lines.save(row); }
        event(request, null, PartRequestStatus.SUBMITTED, input.notes()); audit.record("PART_REQUEST_SUBMIT", "PART_REQUEST", request.id, "Submitted part request"); return detail(request.id);
    }
    Page<RequestView> myRequests(Pageable page) { return requests.findByTechnicianUserId(identities.actor().getId(), page).map(r -> detail(r.id)); }
    Page<RequestView> adminRequests(Pageable page) { requireAdmin(); return requests.findAllByOrderByCreatedAtDesc(page).map(r -> detail(r.id)); }
    List<CustomerStatusView> customerStatuses(Long serviceRequestId) { User actor=identities.actor(); ServiceRequest request=serviceRequests.findById(serviceRequestId).orElseThrow(() -> missing("Service request")); if (!request.getCustomer().getUser().getId().equals(actor.getId())) throw denied(); return requests.findByServiceRequestIdOrderByCreatedAtDesc(serviceRequestId).stream().filter(row -> row.status != PartRequestStatus.CANCELLED).map(row -> new CustomerStatusView(serviceRequestId,row.status,customerLabel(row.status))).toList(); }
    RequestView detail(Long id) { ServiceRequestPartRequest request=requests.findById(id).orElseThrow(() -> missing("Part request")); User actor=identities.actor(); if (actor.getRole()==Role.TECHNICIAN && !request.technician.getUser().getId().equals(actor.getId())) throw denied(); if (actor.getRole()==Role.CUSTOMER && !request.serviceRequest.getCustomer().getUser().getId().equals(actor.getId())) throw denied(); if (actor.getRole()!=Role.TECHNICIAN && actor.getRole()!=Role.CUSTOMER) requireAdmin(); return view(request); }
    void cancel(Long id) { User actor=identities.actor(); ServiceRequestPartRequest request=requests.lockById(id).orElseThrow(() -> missing("Part request")); if (!request.technician.getUser().getId().equals(actor.getId())) throw denied(); if (!EnumSet.of(PartRequestStatus.DRAFT,PartRequestStatus.SUBMITTED,PartRequestStatus.APPROVED,PartRequestStatus.RESERVED).contains(request.status)) throw conflict("Request cannot be cancelled"); if (request.status == PartRequestStatus.RESERVED) releaseReservedStock(request); PartRequestStatus before=request.status; request.status=PartRequestStatus.CANCELLED; request.cancelledAt=LocalDateTime.now(); event(request,before,request.status,"Cancelled by technician"); audit.record("PART_REQUEST_CANCEL", "PART_REQUEST", id, "Cancelled part request"); }
    RequestView approve(Long id, boolean approve, String reason) {
        requireAdmin(); ServiceRequestPartRequest request=requests.lockById(id).orElseThrow(() -> missing("Part request"));
        if (request.status != PartRequestStatus.SUBMITTED && request.status != PartRequestStatus.UNDER_REVIEW) throw conflict("Request is not awaiting review");
        PartRequestStatus before=request.status;
        if (!approve) { request.status=PartRequestStatus.REJECTED; request.rejectedAt=LocalDateTime.now(); event(request,before,request.status,reason); audit.record("PART_REQUEST_REJECT", "PART_REQUEST", id, "Rejected part request"); return detail(id); }
        List<ServiceRequestPartLine> requestLines=lines.findByRequestId(id).stream().sorted(Comparator.comparing(line -> line.item.id)).toList(); List<PartItem> locked=new ArrayList<>();
        for (ServiceRequestPartLine line:requestLines) locked.add(items.lockById(line.item.id).orElseThrow(() -> missing("Part")));
        for (int i=0;i<requestLines.size();i++) if (locked.get(i).quantityOnHand < requestLines.get(i).requestedQuantity) throw conflict("Insufficient stock for " + locked.get(i).name);
        for (int i=0;i<requestLines.size();i++) { ServiceRequestPartLine line=requestLines.get(i); PartItem item=locked.get(i); line.approvedQuantity=line.requestedQuantity; item.quantityOnHand-=line.requestedQuantity; items.save(item); lines.save(line); PartStockMovement movement=new PartStockMovement(); movement.item=item; movement.movementType=StockMovementType.RESERVE; movement.quantity=line.requestedQuantity; movement.reason="Reserved for part request " + id; movement.serviceRequest=request.serviceRequest; movement.actorUserId=identities.actor().getId(); movements.save(movement); }
        request.status=PartRequestStatus.RESERVED; request.approvedAt=LocalDateTime.now(); request.reservedAt=request.approvedAt; event(request,before,request.status,reason); audit.record("PART_REQUEST_APPROVE", "PART_REQUEST", id, "Approved and reserved part request"); return detail(id);
    }
    RequestView ready(Long id) { requireAdmin(); ServiceRequestPartRequest request=requests.lockById(id).orElseThrow(() -> missing("Part request")); if (request.status != PartRequestStatus.RESERVED) throw conflict("Request is not reserved"); PartRequestStatus before=request.status; request.status=PartRequestStatus.READY; event(request,before,request.status,"Marked ready by Admin"); audit.record("PART_REQUEST_READY", "PART_REQUEST", id, "Marked part request ready"); return detail(id); }
    RequestView issue(Long id) { requireAdmin(); ServiceRequestPartRequest request=requests.lockById(id).orElseThrow(() -> missing("Part request")); if (request.status != PartRequestStatus.RESERVED && request.status != PartRequestStatus.READY) throw conflict("Request is not ready to issue"); PartRequestStatus before=request.status; request.status=PartRequestStatus.ISSUED; request.issuedAt=LocalDateTime.now(); event(request,before,request.status,"Issued by Admin"); audit.record("PART_REQUEST_ISSUE", "PART_REQUEST", id, "Issued part request"); return detail(id); }

    private void releaseReservedStock(ServiceRequestPartRequest request) {
        List<ServiceRequestPartLine> requestLines=lines.findByRequestId(request.id).stream().sorted(Comparator.comparing(line -> line.item.id)).toList();
        for (ServiceRequestPartLine line : requestLines) {
            if (line.approvedQuantity <= 0) continue;
            PartItem item=items.lockById(line.item.id).orElseThrow(() -> missing("Part"));
            item.quantityOnHand=Math.addExact(item.quantityOnHand, line.approvedQuantity); items.save(item);
            PartStockMovement movement=new PartStockMovement(); movement.item=item; movement.movementType=StockMovementType.RELEASE; movement.quantity=line.approvedQuantity; movement.reason="Released from cancelled part request " + request.id; movement.serviceRequest=request.serviceRequest; movement.actorUserId=identities.actor().getId(); movements.save(movement);
        }
    }

    private void event(ServiceRequestPartRequest request, PartRequestStatus before, PartRequestStatus after, String reason) { PartRequestEvent event=new PartRequestEvent(); event.request=request; event.previousStatus=before; event.newStatus=after; event.reason=reason; event.actorUserId=identities.actor().getId(); events.save(event); }
    private RequestView view(ServiceRequestPartRequest request) { List<ServiceRequestPartLine> requestLines=lines.findByRequestId(request.id); return new RequestView(request.id,request.serviceRequest.getId(),request.technician.getId(),request.technician.getUser().getEmail(),request.status,request.notes,request.createdAt,request.submittedAt,request.approvedAt,request.rejectedAt,request.reservedAt,request.issuedAt,requestLines.stream().map(l -> new LineView(l.id,l.item.id,l.item.name,l.item.sku,l.requestedQuantity,l.approvedQuantity,l.issuedQuantity)).toList(),events.findByRequestIdOrderByCreatedAtAsc(request.id).stream().map(e -> new EventView(e.id,e.previousStatus,e.newStatus,e.actorUserId,e.reason,e.createdAt)).toList()); }
    private CategoryView category(PartCategory row) { return new CategoryView(row.id,row.name,row.description,row.active); }
    private ItemView item(PartItem row) { return new ItemView(row.id,row.category.id,row.category.name,row.name,row.sku,row.description,row.unit,row.quantityOnHand,row.reorderThreshold,row.active,row.compatibilityMetadata,row.active && row.category.active && row.quantityOnHand > 0); }
    private String customerLabel(PartRequestStatus status) { return switch (status) { case SUBMITTED,UNDER_REVIEW -> "Part requested"; case APPROVED,RESERVED -> "Part being arranged"; case READY,ISSUED,CONSUMED -> "Part ready"; case REJECTED -> "Part required"; default -> "Part required"; }; }
    private void requireAdmin() { Role role=identities.actor().getRole(); if (role!=Role.ADMIN && role!=Role.SUPER_ADMIN) throw denied(); }
    private static RuntimeException missing(String value) { return new IllegalArgumentException(value + " not found"); }
    private static RuntimeException conflict(String value) { return new IllegalStateException(value); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
}
