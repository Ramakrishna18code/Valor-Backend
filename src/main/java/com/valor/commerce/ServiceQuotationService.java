package com.valor.commerce;

import com.valor.auth.AssetIdentityAccess;
import com.valor.auth.AuditService;
import com.valor.auth.Role;
import com.valor.auth.User;
import com.valor.pricing.PricingCatalogue;
import com.valor.response.ApiResponse;
import com.valor.workflow.ServiceRequest;
import com.valor.workflow.RequestStatus;
import com.valor.workflow.AssignmentStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.valor.commerce.ServiceQuotationDtos.*;

@Service
@Transactional
class ServiceQuotationService {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private FinanceAccess finance;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private AmcCoverageService coverage;
    private final AssetIdentityAccess identities;
    private final ServiceQuotationRepository quotations;
    private final QuotationServiceRequestRepository requests;
    private final CommerceAssignmentRepository assignments;
    private final InvoiceRepository invoices;
    private final PricingCatalogue catalogue;
    private final AuditService audit;

    ServiceQuotationService(AssetIdentityAccess identities, ServiceQuotationRepository quotations,
            QuotationServiceRequestRepository requests, CommerceAssignmentRepository assignments, InvoiceRepository invoices,
            PricingCatalogue catalogue, AuditService audit) {
        this.identities = identities;
        this.quotations = quotations;
        this.requests = requests;
        this.assignments = assignments;
        this.invoices = invoices;
        this.catalogue = catalogue;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    CommerceDtos.PageView<View> customerQuotations(Pageable pageable) {
        User actor = identities.actor();
        requireCustomer(actor);
        return page(quotations.findForCustomer(actor.getId(), bounded(pageable)).map(this::view));
    }

    @Transactional(readOnly = true)
    CommerceDtos.PageView<View> adminQuotations(ServiceQuotationStatus status, Pageable pageable) {
        identities.requireAdmin();
        Pageable bounded = bounded(pageable);
        Page<ServiceQuotation> rows = status == null
                ? quotations.findAll(bounded)
                : quotations.findByStatus(status, bounded);
        return page(rows.map(this::view));
    }

    List<View> technicianQuotations(Long serviceRequestId) {
        User actor = identities.actor();
        if (actor.getRole() != Role.TECHNICIAN
                || !assignments.existsByRequestIdAndTechnicianUserIdAndStatusIn(serviceRequestId, actor.getId(),
                        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.ACCEPTED, AssignmentStatus.COMPLETED))) {
            throw denied();
        }
        return quotations.findByServiceRequestIdOrderByCreatedAtDescIdDesc(serviceRequestId).stream()
                .map(this::view).toList();
    }

    @Transactional(readOnly = true)
    View customerQuotation(Long id) {
        User actor = identities.actor();
        requireCustomer(actor);
        return quotations.findForCustomerById(id, actor.getId()).map(this::view)
                .orElseThrow(() -> new CommerceException(404, "Quotation not found"));
    }

    @Transactional(readOnly = true)
    View adminQuotation(Long id) {
        identities.requireAdmin();
        return quotations.findById(id).map(this::view)
                .orElseThrow(() -> new CommerceException(404, "Quotation not found"));
    }

    View createForServiceRequest(Long serviceRequestId, Create input) {
        return createQuotation(serviceRequestId,input,null,false);
    }
    View draft(Long serviceRequestId,Create input){return createQuotation(serviceRequestId,input,null,true);}
    View revise(Long id,Revision input){
        identities.requireAdmin();
        ServiceQuotation parent=quotations.findById(id).orElseThrow(()->new CommerceException(404,"Quotation not found"));
        FinanceAccess.text(input.reason(),1000);
        return createQuotation(parent.getServiceRequest().getId(),input.quotation(),parent,true,input.reason());
    }
    private View createQuotation(Long serviceRequestId,Create input,ServiceQuotation parent,boolean draft){return createQuotation(serviceRequestId,input,parent,draft,null);}
    private View createQuotation(Long serviceRequestId,Create input,ServiceQuotation parent,boolean draft,String reason) {
        User actor = identities.actor();
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN) throw denied();
        ServiceRequest request = requests.lockForQuotation(serviceRequestId)
                .orElseThrow(() -> new CommerceException(404, "Service request not found"));
        if (request.getStatus() == RequestStatus.CANCELLED) {
            throw new CommerceException(409, "A quotation cannot be issued for a cancelled service request");
        }
        if (parent == null && quotations.existsByServiceRequestIdAndStatus(serviceRequestId, ServiceQuotationStatus.ACCEPTED)) {
            throw new CommerceException(409, "A quotation has already been accepted for this service request");
        }
        if (input.taxAmount() != null && input.taxAmount().signum() != 0) {
            throw new CommerceException(400, "Service catalogue prices are tax-inclusive; additional tax is not accepted");
        }
        List<CalculatedLine> lines = calculateLines(input);
        if(!draft)validateCoverage(serviceRequestId,lines);
        BigDecimal subtotal = lines.stream().map(CalculatedLine::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.UNNECESSARY);
        if (input.subtotal() != null && input.subtotal().compareTo(subtotal) != 0) {
            throw new CommerceException(400, "Quotation subtotal must match the service line items");
        }
        BigDecimal taxAmount = BigDecimal.ZERO.setScale(2);
        BigDecimal totalAmount = subtotal;
        if (totalAmount.signum() <= 0) throw new CommerceException(400, "Quotation total must be greater than zero");

        ServiceQuotation row = new ServiceQuotation();
        row.setQuotationNumber("PENDING-"+java.util.UUID.randomUUID().toString().replace("-",""));
        var history=quotations.findByServiceRequestIdOrderByCreatedAtDescIdDesc(serviceRequestId);
        row.setVersionNumber(history.stream().mapToInt(ServiceQuotation::getVersionNumber).max().orElse(0)+1);
        if(parent!=null){row.setSupersedesQuotationId(parent.getId());row.setRevisionKind(parent.getStatus()==ServiceQuotationStatus.ACCEPTED?"ADDITIONAL":"REPLACEMENT");row.setRevisionReason(reason);}
        row.setReleaseStatus(draft?"DRAFT":"RELEASED");
        if(!draft)row.setReleasedAt(java.time.LocalDateTime.now());
        row.setCustomer(request.getCustomer());
        row.setServiceRequest(request);
        row.setCreatedBy(actor);
        row.setPricingCode(lines.size() == 1 ? lines.get(0).pricingCode() : null);
        row.setDescription(quotationDescription(input.description(), lines));
        row.setSubtotal(subtotal);
        row.setTaxAmount(taxAmount);
        row.setTotalAmount(totalAmount);
        row.setCurrency(currency(input.currency()));
        row.setValidUntil(input.validUntil());
        int lineNumber = 1;
        for (CalculatedLine line : lines) {
            ServiceQuotationItem item = new ServiceQuotationItem();
            item.setQuotation(row);
            item.setLineNumber(lineNumber++);
            item.setPricingCode(line.pricingCode());
            item.setCoverageScope(line.coverageScope());
            item.setDescription(line.description());
            item.setQuantity(line.quantity());
            item.setUnitAmount(line.unitAmount());
            item.setLineAmount(line.lineAmount());
            row.getItems().add(item);
        }
        quotations.saveAndFlush(row);
        row.setQuotationNumber("VAL-QT-%06d".formatted(row.getId()));
        audit.record("SERVICE_QUOTATION_ISSUED", "SERVICE_QUOTATION", row.getId(),
                "Issued service quotation for request=" + serviceRequestId + " total=" + totalAmount);
        return view(row);
    }

    private List<CalculatedLine> calculateLines(Create input) {
        if (input.items() != null && !input.items().isEmpty()) {
            return input.items().stream().map(this::calculateLine).toList();
        }
        if (input.description() == null || input.description().isBlank() || input.subtotal() == null) {
            throw new CommerceException(400, "At least one service line item is required");
        }
        String code = clean(input.pricingCode(), 80);
        BigDecimal requestedAmount = amount(input.subtotal(), "Invalid quotation subtotal");
        if (code == null) {
            return List.of(new CalculatedLine("CUSTOM_QUOTE", input.description().trim(), 1, requestedAmount, requestedAmount, null));
        }
        PricingCatalogue.Item item = catalogue.findByCode(code)
                .orElseThrow(() -> new CommerceException(400, "Pricing catalogue item was not found"));
        if (!catalogue.approvedForFinancialUse(item)) {
            throw new CommerceException(400, "Pricing catalogue item is not approved for financial use");
        }
        BigDecimal unitAmount = catalogueUnitAmount(item, requestedAmount);
        return List.of(new CalculatedLine(code, input.description().trim(), 1, unitAmount, unitAmount, null));
    }

    private CalculatedLine calculateLine(LineItemCreate input) {
        if (input.quantity() == null || input.quantity() < 1 || input.quantity() > 1000) {
            throw new CommerceException(400, "Service quantity must be between 1 and 1000");
        }
        String code = input.pricingCode().trim();
        PricingCatalogue.Item item = catalogue.findByCode(code)
                .orElseThrow(() -> new CommerceException(400, "Pricing catalogue item was not found"));
        if (!catalogue.approvedForFinancialUse(item)) {
            throw new CommerceException(400, "Pricing catalogue item is not approved for financial use");
        }
        if (!"PER_JOB".equals(item.billingUnit())) {
            throw new CommerceException(400, "Only per-job catalogue items can be added to a service bill");
        }
        BigDecimal unitAmount = catalogueUnitAmount(item, input.unitAmount());
        BigDecimal lineAmount = unitAmount.multiply(BigDecimal.valueOf(input.quantity()))
                .setScale(2, RoundingMode.UNNECESSARY);
        if (lineAmount.precision() > 12) throw new CommerceException(400, "Service line amount is too large");
        String description = input.description() == null || input.description().isBlank()
                ? item.name() : input.description().trim();
        return new CalculatedLine(code, description, input.quantity(), unitAmount, lineAmount, input.coverageScope());
    }

    private BigDecimal catalogueUnitAmount(PricingCatalogue.Item item, BigDecimal requestedAmount) {
        if (!"PER_JOB".equals(item.billingUnit())) {
            throw new CommerceException(400, "Only per-job catalogue items can be quoted for a service request");
        }
        switch (item.pricingType()) {
            case FIXED -> {
                if (requestedAmount != null && requestedAmount.compareTo(item.fixedAmount()) != 0) {
                    throw new CommerceException(400, "Unit amount must match the published catalogue price");
                }
                return item.fixedAmount();
            }
            case RANGE -> {
                BigDecimal value = amount(requestedAmount, "A unit amount is required for range-priced services");
                if (value.compareTo(item.minAmount()) < 0 || value.compareTo(item.maxAmount()) > 0) {
                    throw new CommerceException(400, "Unit amount must be within the published catalogue range");
                }
                return value;
            }
            case QUOTE_REQUIRED -> {
                BigDecimal value = amount(requestedAmount, "A quoted unit amount is required");
                if (value.signum() <= 0) throw new CommerceException(400, "Quoted unit amount must be greater than zero");
                return value;
            }
            default -> throw new CommerceException(400, "Catalogue item is not available for a service quotation");
        }
    }

    private String quotationDescription(String description, List<CalculatedLine> lines) {
        if (description != null && !description.isBlank()) return clean(description, 500);
        String joined = lines.stream().map(CalculatedLine::description).distinct()
                .reduce((first, second) -> first + ", " + second).orElse("Service quotation");
        return clean(joined, 500);
    }

    private record CalculatedLine(String pricingCode, String description, int quantity,
            BigDecimal unitAmount, BigDecimal lineAmount, String coverageScope) {}

    private void validateCoverage(Long requestId,List<CalculatedLine> lines){
        if(finance==null||coverage==null)return;
        var request=finance.one("SELECT lift_id,preferred_visit_date FROM service_requests WHERE id=?",requestId);
        LocalDate date=request.get("preferredVisitDate") instanceof LocalDate d?d:LocalDate.now();
        var visitDecision=finance.rows("SELECT service_date FROM service_request_coverage_decisions WHERE service_request_id=? AND scope='VISIT' ORDER BY version_number DESC LIMIT 1",requestId);
        if(!visitDecision.isEmpty())date=(LocalDate)visitDecision.get(0).get("serviceDate");
        boolean hasPlan=finance.count("SELECT COUNT(*) FROM amc_contracts WHERE lift_id=? AND status='ACTIVE' AND start_date<=? AND end_date>=?",request.get("liftId"),date,date)>0
                ||finance.count("SELECT COUNT(*) FROM service_request_coverage_decisions WHERE service_request_id=? AND scope='VISIT' AND contract_id IS NOT NULL AND version_number=(SELECT MAX(v.version_number) FROM service_request_coverage_decisions v WHERE v.service_request_id=? AND v.scope='VISIT')",requestId,requestId)>0;
        if(!hasPlan)return;
        for(var line:lines){
            if(line.coverageScope()==null||!List.of("VISIT","LABOUR","PARTS").contains(line.coverageScope()))
                throw new CommerceException(409,"Choose visit, labour or parts for each charge before sharing this quotation");
            var catalogueItem=catalogue.findByCode(line.pricingCode());
            catalogueItem.ifPresent(item->{
                if("EMERGENCY_SERVICE".equals(item.category())&&!"VISIT".equals(line.coverageScope())
                        ||List.of("REPAIR_SPARE_PART","MODERNIZATION_UPGRADE").contains(item.category())&&"VISIT".equals(line.coverageScope()))
                    throw new CommerceException(409,"Choose the work type that matches this service before sharing the quotation");
            });
            if(catalogueItem.isPresent()&&List.of("REPAIR_SPARE_PART","MODERNIZATION_UPGRADE").contains(catalogueItem.get().category())){
                String labour=coverageOutcome(requestId,"LABOUR",date);
                String parts=coverageOutcome(requestId,"PARTS",date);
                if(!"EXCLUDED".equals(labour)||!"EXCLUDED".equals(parts))
                    throw new CommerceException(409,"Review labour and parts coverage separately. This combined service price can be shared only when neither is included in the maintenance plan");
                continue;
            }
            String outcome=coverageOutcome(requestId,line.coverageScope(),date);
            if("COVERED".equals(outcome))throw new CommerceException(409,"This work is included in the maintenance plan. Remove its charge from the quotation");
            if("UNCONFIRMED".equals(outcome))throw new CommerceException(409,"Review the maintenance plan coverage for this work before sharing a price");
        }
    }

    private String coverageOutcome(Long requestId,String scope,LocalDate date){
        var prior=finance.rows("SELECT outcome,service_date FROM service_request_coverage_decisions WHERE service_request_id=? AND scope=? ORDER BY version_number DESC LIMIT 1",requestId,scope);
        if(!prior.isEmpty()&&date.equals(prior.get(0).get("serviceDate")))return String.valueOf(prior.get(0).get("outcome"));
        return String.valueOf(coverage.capture(requestId,scope,date).get("outcome"));
    }

    View decide(Long id, Decision input) {
        User actor = identities.actor();
        requireCustomer(actor);
        ServiceQuotation initial=quotations.findById(id).orElseThrow(()->new CommerceException(404,"Quotation not found"));
        requests.lockForQuotation(initial.getServiceRequest().getId()).orElseThrow(()->new CommerceException(404,"Service request not found"));
        ServiceQuotation row = quotations.lockById(id).orElseThrow(() -> new CommerceException(404, "Quotation not found"));
        if (!row.getCustomer().getUser().getId().equals(actor.getId())) throw denied();
        if(!"RELEASED".equals(row.getReleaseStatus()))throw new CommerceException(409,"Quotation is not released for customer approval");
        if (row.getStatus() != ServiceQuotationStatus.ISSUED) {
            throw new CommerceException(409, "Quotation is no longer awaiting a response");
        }
        if (row.getValidUntil() != null && row.getValidUntil().isBefore(LocalDate.now())) {
            row.setStatus(ServiceQuotationStatus.EXPIRED);
            row.setRespondedAt(LocalDateTime.now());
            audit.record("SERVICE_QUOTATION_EXPIRED", "SERVICE_QUOTATION", row.getId(),
                    "Customer attempted to respond after quotation expiry");
            return view(row);
        }

        row.setRespondedAt(LocalDateTime.now());
        if (input.decision() == ServiceQuotationDecision.DECLINE) {
            row.setStatus(ServiceQuotationStatus.DECLINED);
            if(finance!=null)finance.db.update("UPDATE service_work_approvals SET status='REJECTED' WHERE quotation_id=? AND status='AWAITING_CUSTOMER'",id);
            audit.record("SERVICE_QUOTATION_DECLINED", "SERVICE_QUOTATION", row.getId(),
                    "Customer declined service quotation");
            return view(row);
        }

        requests.lockForQuotation(row.getServiceRequest().getId())
                .orElseThrow(() -> new CommerceException(404, "Service request not found"));
        if (!"ADDITIONAL".equals(row.getRevisionKind()) && quotations.existsByServiceRequestIdAndStatus(row.getServiceRequest().getId(), ServiceQuotationStatus.ACCEPTED)) {
            throw new CommerceException(409, "A quotation has already been accepted for this service request");
        }
        if (row.getInvoice() == null && !issueMilestones(row)) {
            Invoice invoice = new Invoice();
            invoice.setCustomer(row.getCustomer());
            invoice.setServiceRequest(row.getServiceRequest());
            invoice.setCreatedBy(row.getCreatedBy());
            invoice.setDescription(row.getDescription());
            invoice.setSubtotal(row.getSubtotal());
            invoice.setTaxAmount(row.getTaxAmount());
            invoice.setTotalAmount(row.getTotalAmount());
            invoice.setCurrency(row.getCurrency());
            int lineNumber = 1;
            for (ServiceQuotationItem quotedItem : row.getItems()) {
                InvoiceLineItem item = new InvoiceLineItem();
                item.setInvoice(invoice);
                item.setLineNumber(lineNumber++);
                item.setPricingCode(quotedItem.getPricingCode());
                item.setDescription(quotedItem.getDescription());
                item.setQuantity(quotedItem.getQuantity());
                item.setUnitAmount(quotedItem.getUnitAmount());
                item.setLineAmount(quotedItem.getLineAmount());
                invoice.getItems().add(item);
            }
            invoices.saveAndFlush(invoice);
            invoice.setInvoiceNumber("INV-%06d".formatted(invoice.getId()));
            row.setInvoice(invoice);
        }
        row.setStatus(ServiceQuotationStatus.ACCEPTED);
        row.setApprovedTermsHash(termsHash(row));
        if(finance!=null){finance.db.update("UPDATE service_work_approvals SET status='APPROVED' WHERE quotation_id=? AND status='AWAITING_CUSTOMER'",id);finance.event(row.getServiceRequest().getId(),"QUOTATION_ACCEPTED",row.getQuotationNumber()+" terms="+row.getApprovedTermsHash());}
        audit.record("SERVICE_QUOTATION_ACCEPTED", "SERVICE_QUOTATION", row.getId(),
                "Customer accepted quotation and invoice=" + row.getInvoice().getId());
        return view(row);
    }

    View release(Long id){
        identities.requireAdmin();var initial=quotations.findById(id).orElseThrow(()->new CommerceException(404,"Quotation not found"));
        requests.lockForQuotation(initial.getServiceRequest().getId()).orElseThrow(()->new CommerceException(404,"Service request not found"));
        var row=quotations.lockById(id).orElseThrow(()->new CommerceException(404,"Quotation not found"));
        if(!"DRAFT".equals(row.getReleaseStatus())||row.getStatus()!=ServiceQuotationStatus.ISSUED)throw new CommerceException(409,"Quotation is not awaiting release");
        validateCoverage(row.getServiceRequest().getId(),row.getItems().stream().map(i->new CalculatedLine(i.getPricingCode(),i.getDescription(),i.getQuantity(),i.getUnitAmount(),i.getLineAmount(),i.getCoverageScope())).toList());
        if(row.getSupersedesQuotationId()!=null){var parent=quotations.lockById(row.getSupersedesQuotationId()).orElseThrow(()->new CommerceException(404,"Parent quotation not found"));
            if("REPLACEMENT".equals(row.getRevisionKind())){if(parent.getStatus()==ServiceQuotationStatus.ACCEPTED)throw new CommerceException(409,"Accepted terms cannot be replaced; create additional scope instead");parent.setStatus(ServiceQuotationStatus.CANCELLED);}
            else if(parent.getStatus()!=ServiceQuotationStatus.ACCEPTED)throw new CommerceException(409,"Additional scope requires accepted original terms");}
        row.setReleaseStatus("RELEASED");row.setReleasedAt(java.time.LocalDateTime.now());quotations.flush();audit.record("SERVICE_QUOTATION_RELEASED","SERVICE_QUOTATION",id,"Released version="+row.getVersionNumber());return view(row);
    }
    View plan(Long id,Plan input){
        identities.requireAdmin();var row=quotations.lockById(id).orElseThrow(()->new CommerceException(404,"Quotation not found"));
        if(!"DRAFT".equals(row.getReleaseStatus())||row.getStatus()!=ServiceQuotationStatus.ISSUED||finance==null)throw new CommerceException(409,"Payment stages must be defined on a draft quotation");
        BigDecimal sum=BigDecimal.ZERO;for(var m:input.milestones()){FinanceAccess.money(m.amount(),false);FinanceAccess.text(m.label(),120);if(!List.of("ON_APPROVAL","BEFORE_REPAIR","ON_COMPLETION","SCHEDULED").contains(m.dueCondition())||m.dueCondition().equals("SCHEDULED")&&m.dueDate()==null)throw new CommerceException(400,"Invalid payment stage condition");sum=sum.add(m.amount());}
        if(sum.compareTo(row.getTotalAmount())!=0)throw new CommerceException(400,"Payment stages must equal the quotation total");
        finance.db.update("DELETE FROM service_payment_milestones WHERE quotation_id=? AND invoice_id IS NULL",id);int n=0;
        for(var m:input.milestones())finance.db.update("INSERT INTO service_payment_milestones(quotation_id,service_request_id,sequence_number,label,due_condition,due_date,amount) VALUES(?,?,?,?,?,?,?)",id,row.getServiceRequest().getId(),++n,m.label().trim(),m.dueCondition(),m.dueDate(),m.amount());return view(row);
    }
    private boolean issueMilestones(ServiceQuotation row){
        if(finance==null)return false;
        var stages=finance.rows("SELECT * FROM service_payment_milestones WHERE quotation_id=? ORDER BY sequence_number",row.getId());if(stages.isEmpty())return false;
        for(var stage:stages){Invoice invoice=new Invoice();invoice.setCustomer(row.getCustomer());invoice.setServiceRequest(row.getServiceRequest());invoice.setCreatedBy(row.getCreatedBy());invoice.setDescription(row.getQuotationNumber()+" · "+stage.get("label"));invoice.setSubtotal((BigDecimal)stage.get("amount"));invoice.setTaxAmount(BigDecimal.ZERO);invoice.setTotalAmount((BigDecimal)stage.get("amount"));invoice.setCurrency(row.getCurrency());invoice.setDueDate((LocalDate)stage.get("dueDate"));
            InvoiceLineItem item=new InvoiceLineItem();item.setInvoice(invoice);item.setLineNumber(1);item.setPricingCode("SERVICE_MILESTONE");item.setDescription(invoice.getDescription());item.setQuantity(1);item.setUnitAmount(invoice.getTotalAmount());item.setLineAmount(invoice.getTotalAmount());invoice.getItems().add(item);invoices.saveAndFlush(invoice);invoice.setInvoiceNumber("INV-%06d".formatted(invoice.getId()));
            finance.db.update("UPDATE service_payment_milestones SET invoice_id=? WHERE id=?",invoice.getId(),stage.get("id"));if(row.getInvoice()==null)row.setInvoice(invoice);}
        return true;
    }
    private String termsHash(ServiceQuotation row){
        String text=row.getId()+"|"+row.getVersionNumber()+"|"+row.getDescription()+"|"+row.getTotalAmount()+"|"+row.getCurrency()+"|"+row.getItems().stream().map(i->i.getPricingCode()+":"+i.getQuantity()+":"+i.getUnitAmount()+":"+i.getDescription()+":"+i.getCoverageScope()).toList();
        if(finance!=null)text+="|"+finance.rows("SELECT sequence_number,label,due_condition,due_date,amount FROM service_payment_milestones WHERE quotation_id=? ORDER BY sequence_number",row.getId());
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }

    private void requireCustomer(User actor) {
        if (actor.getRole() != Role.CUSTOMER) throw denied();
        identities.requireActiveCustomerUser(actor);
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Access denied");
    }

    private BigDecimal amount(BigDecimal value, String message) {
        if (value == null || value.signum() < 0 || value.scale() > 2) throw new CommerceException(400, message);
        return value;
    }

    private String currency(String value) {
        String normalized = value == null || value.isBlank() ? "INR" : value.trim().toUpperCase(Locale.ROOT);
        if (!"INR".equals(normalized)) throw new CommerceException(400, "Quotation currency must be INR");
        return normalized;
    }

    private String clean(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private Pageable bounded(Pageable pageable) {
        if (pageable.getPageNumber() < 0 || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new CommerceException(400, "Invalid paging");
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private CommerceDtos.PageView<View> page(Page<View> rows) {
        return new CommerceDtos.PageView<>(rows.getContent(), rows.getTotalElements(), rows.getTotalPages(),
                rows.getNumber(), rows.getSize());
    }

    private View view(ServiceQuotation row) {
        List<LineItemView> items = row.getItems().stream()
                .map(item -> new LineItemView(item.getPricingCode(), item.getDescription(), item.getQuantity(),
                        item.getUnitAmount(), item.getLineAmount(), item.getCoverageScope()))
                .toList();
        if (items.isEmpty()) {
            items = List.of(new LineItemView(row.getPricingCode() == null ? "CUSTOM_QUOTE" : row.getPricingCode(),
                    row.getDescription(), 1, row.getSubtotal(), row.getSubtotal(), null));
        }
        return new View(row.getId(), row.getQuotationNumber(), row.getServiceRequest().getId(),
                row.getServiceRequest().getTitle(), row.getPricingCode(), row.getDescription(), row.getSubtotal(),
                row.getTaxAmount(), row.getTotalAmount(), row.getCurrency(), row.getStatus(), row.getValidUntil(),
                row.getRespondedAt(), row.getInvoice() == null ? null : row.getInvoice().getId(), items,
                row.getCreatedAt(), row.getUpdatedAt(),row.getVersionNumber(),row.getSupersedesQuotationId(),row.getRevisionKind(),row.getReleaseStatus(),row.getRevisionReason(),row.getApprovedTermsHash());
    }
}
