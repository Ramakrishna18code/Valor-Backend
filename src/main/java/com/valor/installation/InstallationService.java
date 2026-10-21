package com.valor.installation;

import com.valor.auth.*;
import com.valor.notifications.NotificationService;
import com.valor.realtime.RealtimeEventService;
import com.valor.response.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import com.valor.workflow.WorkflowDtos.PageView;
import static com.valor.installation.InstallationDtos.*;

@Service
@Transactional
public class InstallationService {
    private final InstallationRequestRepository requests;
    private final InstallationHistoryRepository history;
    private final InstallationDocumentRepository documents;
    private final InstallationAuthorizationRepository authorizations;
    private final InstallationBuildingRepository buildings;
    private final WorkflowIdentityAccess identities;
    private final AssetIdentityAccess access;
    private final AuditService audit;
    private final NotificationService notifications;
    private final RealtimeEventService realtime;

    public InstallationService(InstallationRequestRepository requests, InstallationHistoryRepository history,
            InstallationDocumentRepository documents, InstallationAuthorizationRepository authorizations,
            InstallationBuildingRepository buildings, WorkflowIdentityAccess identities, AssetIdentityAccess access, AuditService audit, NotificationService notifications, RealtimeEventService realtime) {
        this.requests = requests; this.history = history; this.documents = documents; this.authorizations = authorizations;
        this.buildings = buildings; this.identities = identities; this.access = access; this.audit = audit; this.notifications = notifications; this.realtime = realtime;
    }

    @Transactional(readOnly = true)
    public List<View> list() {
        CustomerProfile customer = customer();
        return requests.findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public View read(Long id) { return view(owned(id)); }

    @Transactional(readOnly = true)
    public PageView<View> adminList(InstallationStatus status, String query, int page, int size) {
        access.requireAdmin();
        int safeSize = Math.min(Math.max(size, 1), 100); int safePage = Math.max(page, 0);
        var result = requests.adminSearch(status, clean(query), PageRequest.of(safePage, safeSize));
        return new PageView<>(result.getContent().stream().map(this::view).toList(), safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public View adminRead(Long id) { access.requireAdmin(); return requests.findById(id).map(this::view).orElseThrow(() -> new IllegalArgumentException("Installation not found")); }

    public View create(WriteRequest input) {
        CustomerProfile customer = customer();
        String key = clean(input.clientRequestKey());
        if (key != null) return requests.findByCustomerIdAndClientRequestKey(customer.getId(), key).map(this::view).orElseGet(() -> createNew(input, customer, key));
        return createNew(input, customer, null);
    }

    private View createNew(WriteRequest input, CustomerProfile customer, String key) {
        InstallationRequest row = new InstallationRequest();
        row.setCustomer(customer); row.setClientRequestKey(key);
        apply(row, input, customer);
        requests.saveAndFlush(row);
        transition(row, null, InstallationStatus.DRAFT, "Installation draft created");
        requests.saveAndFlush(row);
        audit.record("INSTALLATION_REQUEST_CREATE", "INSTALLATION_REQUEST", row.getId(), "Installation draft created");
        return view(row);
    }

    public View update(Long id, WriteRequest input) {
        InstallationRequest row = owned(id); editable(row);
        if (input.version() == null || row.getVersion() == null || !row.getVersion().equals(input.version())) throw conflict("Installation draft changed; refresh and try again");
        apply(row, input, row.getCustomer());
        if (input.clientRequestKey() != null) row.setClientRequestKey(clean(input.clientRequestKey()));
        requests.saveAndFlush(row);
        audit.record("INSTALLATION_REQUEST_UPDATE", "INSTALLATION_REQUEST", row.getId(), "Installation draft updated");
        return view(row);
    }

    public View submit(Long id, SubmitRequest input) {
        CustomerProfile customer = customer();
        InstallationRequest row = owned(id);
        var existing = requests.findByCustomerIdAndSubmissionKey(customer.getId(), clean(input.submissionKey()));
        if (existing.isPresent()) return view(existing.get());
        editable(row);
        // A registered building already supplies the property's saved site context.
        // Additional requirements/contact details remain optional for this flow.
        if (row.getBuilding() == null && (row.getRequirements() == null || row.getRequirements().isBlank() || row.getSiteContactName() == null || row.getSiteContactPhone() == null))
            throw conflict("Requirements and site contact details are required for a property without a registered building");
        if (row.getBuilding() == null && row.getAuthorization() == null) throw conflict("A building or structured property authorization is required");
        if (row.getAuthorization() != null && row.getAuthorization().getStatus() != AuthorizationStatus.APPROVED)
            throw conflict("Property authorization is pending review");
        if (row.getVersion() == null || !row.getVersion().equals(input.version())) throw conflict("Installation draft changed; refresh and try again");
        row.setSubmissionKey(clean(input.submissionKey())); row.setSubmittedAt(LocalDateTime.now());
        transition(row, InstallationStatus.DRAFT, InstallationStatus.SUBMITTED, "Installation request submitted");
        requests.saveAndFlush(row);
        audit.record("INSTALLATION_REQUEST_SUBMIT", "INSTALLATION_REQUEST", row.getId(), "Installation request submitted");
        notifications.createSystemOnce(row.getCustomer().getUser().getId(), "INSTALLATION_SUBMITTED:" + row.getId() + ":" + row.getVersion(), "Installation request submitted", "Your installation service request was submitted for review.");
        realtime.publish("INSTALLATION_STATUS_CHANGED", "INSTALLATION_REQUEST", row.getId(), row.getStatus().name(), java.util.Set.of(row.getCustomer().getUser().getId()));
        return view(row);
    }

    public View cancel(Long id, CancelRequest input) {
        InstallationRequest row = owned(id);
        if (row.getVersion() == null || !row.getVersion().equals(input.version())) throw conflict("Installation request changed; refresh and try again");
        if (row.getStatus() != InstallationStatus.DRAFT && row.getStatus() != InstallationStatus.SUBMITTED) throw conflict("Installation cannot be cancelled in this state");
        InstallationStatus from = row.getStatus(); row.setStatus(InstallationStatus.CANCELLED); row.setCancelledAt(LocalDateTime.now());
        transition(row, from, InstallationStatus.CANCELLED, input.reason().trim());
        requests.saveAndFlush(row);
        audit.record("INSTALLATION_REQUEST_CANCEL", "INSTALLATION_REQUEST", row.getId(), "Installation request cancelled", "status=" + from, "status=CANCELLED", "SUCCESS");
        realtime.publish("INSTALLATION_STATUS_CHANGED", "INSTALLATION_REQUEST", row.getId(), row.getStatus().name(), java.util.Set.of(row.getCustomer().getUser().getId()));
        return view(row);
    }

    public AuthorizationView reviewAuthorization(Long id, AuthorizationReviewRequest input) {
        access.requireAdmin();
        if (input.status() != AuthorizationStatus.APPROVED && input.status() != AuthorizationStatus.REJECTED) throw new IllegalArgumentException("Review status must be APPROVED or REJECTED");
        PropertyAuthorization auth = authorizations.findById(id).orElseThrow(() -> new IllegalArgumentException("Authorization not found"));
        auth.setStatus(input.status()); authorizations.save(auth);
        audit.record("INSTALLATION_AUTHORIZATION_REVIEW", "INSTALLATION_AUTHORIZATION", id, "Property authorization reviewed", null, "status=" + input.status(), "SUCCESS");
        return new AuthorizationView(auth.getId(), auth.getOwnerName(), auth.getOrganizationName(), auth.getRelationshipType(), auth.getStatus(), auth.getAuthorizationReference());
    }

    public View addDocument(Long id, DocumentReference input) {
        InstallationRequest row = owned(id); editable(row);
        InstallationRequestDocument document = new InstallationRequestDocument(); document.setRequest(row); document.setUploadedBy(access.actor());
        document.setDocumentType(input.documentType().trim()); document.setOriginalFilename(input.originalFilename().trim()); document.setContentType(input.contentType().trim()); document.setFileSize(input.fileSize()); document.setStorageKey(input.storageKey().trim());
        documents.saveAndFlush(document); audit.record("INSTALLATION_DOCUMENT_REFERENCE", "INSTALLATION_REQUEST", row.getId(), "Installation document reference added"); return view(row);
    }

    private void apply(InstallationRequest row, WriteRequest input, CustomerProfile customer) {
        if (input.buildingId() != null) row.setBuilding(buildings.findOwnedActive(input.buildingId(), customer.getId()).orElseThrow(() -> new AccessDeniedException("Building unavailable")));
        else row.setBuilding(null);
        AuthorizationInput a = input.authorization();
        if (a != null) {
            PropertyAuthorization auth = row.getAuthorization();
            if (auth == null) { auth = new PropertyAuthorization(); auth.setCustomer(customer); auth.setStatus(AuthorizationStatus.PENDING_REVIEW); }
            auth.setOwnerName(clean(a.ownerName())); auth.setOrganizationName(clean(a.organizationName())); auth.setRelationshipType(clean(a.relationshipType())); auth.setAuthorizationReference(clean(a.authorizationReference()));
            row.setAuthorization(authorizations.save(auth));
        } else row.setAuthorization(null);
        row.setPropertyType(clean(input.propertyType())); row.setIntendedUse(clean(input.intendedUse())); row.setPassengerOrGoods(clean(input.passengerOrGoods()));
        row.setEstimatedFloorCount(input.estimatedFloorCount()); row.setEstimatedCapacity(input.estimatedCapacity()); row.setPreferredLocation(clean(input.preferredLocation())); row.setRequirements(clean(input.requirements()));
        row.setSiteContactName(clean(input.siteContactName())); row.setSiteContactPhone(clean(input.siteContactPhone())); row.setSiteContactEmail(clean(input.siteContactEmail()));
        row.setSiteAddressLine1(clean(input.siteAddressLine1())); row.setSiteAddressLine2(clean(input.siteAddressLine2()));
        row.setSiteLocality(clean(input.siteLocality())); row.setSiteCity(clean(input.siteCity())); row.setSiteState(clean(input.siteState()));
        row.setSitePostalCode(clean(input.sitePostalCode())); row.setSiteCountry(clean(input.siteCountry())); row.setSitePropertyDescription(clean(input.sitePropertyDescription()));
    }

    private InstallationRequest owned(Long id) { CustomerProfile c = customer(); return requests.findByIdAndCustomerId(id, c.getId()).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Access denied")); }
    private CustomerProfile customer() { return identities.customer(access.actor()); }
    private void editable(InstallationRequest row) { if (row.getStatus() != InstallationStatus.DRAFT) throw conflict("Only draft installations can be edited"); }
    private void transition(InstallationRequest row, InstallationStatus from, InstallationStatus to, String notes) { InstallationRequestHistory h = new InstallationRequestHistory(); h.setRequest(row); h.setChangedBy(access.actor()); h.setFromStatus(from); h.setToStatus(to); h.setNotes(notes); history.save(h); row.setStatus(to); }
    private View view(InstallationRequest row) { return new View(row.getId(), row.getCustomer().getId(), row.getBuilding() == null ? null : row.getBuilding().getId(), row.getAuthorization() == null ? null : new AuthorizationView(row.getAuthorization().getId(), row.getAuthorization().getOwnerName(), row.getAuthorization().getOrganizationName(), row.getAuthorization().getRelationshipType(), row.getAuthorization().getStatus(), row.getAuthorization().getAuthorizationReference()), row.getStatus(), row.getPropertyType(), row.getIntendedUse(), row.getPassengerOrGoods(), row.getEstimatedFloorCount(), row.getEstimatedCapacity(), row.getPreferredLocation(), row.getRequirements(), row.getSiteContactName(), row.getSiteContactPhone(), row.getSiteContactEmail(), row.getSiteAddressLine1(), row.getSiteAddressLine2(), row.getSiteLocality(), row.getSiteCity(), row.getSiteState(), row.getSitePostalCode(), row.getSiteCountry(), row.getSitePropertyDescription(), row.getVersion(), row.getSubmittedAt(), row.getCancelledAt(), row.getCreatedAt(), row.getUpdatedAt(), history.findByRequestIdOrderByChangedAtAscIdAsc(row.getId()).stream().map(h -> new HistoryView(h.getId(), h.getFromStatus(), h.getToStatus(), h.getChangedBy().getId(), h.getNotes(), h.getChangedAt())).toList(), documents.findByRequestIdOrderByCreatedAtAscIdAsc(row.getId()).stream().map(d -> new DocumentView(d.getId(), d.getDocumentType(), d.getOriginalFilename(), d.getContentType(), d.getFileSize(), d.getCreatedAt())).toList()); }
    private static String clean(String value) { if (value == null) return null; String v = value.trim(); return v.isEmpty() ? null : v; }
    private static IllegalStateException conflict(String message) { return new IllegalStateException(message); }
}
