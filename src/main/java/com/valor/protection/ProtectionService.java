package com.valor.protection;

import com.valor.assets.Lift;
import com.valor.auth.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProtectionService {
    private static final List<ClaimStatus> OPEN_CLAIMS = List.of(ClaimStatus.SUBMITTED, ClaimStatus.DOCUMENTS_REQUESTED,
            ClaimStatus.FORWARDED_TO_INSURER, ClaimStatus.INSURER_DECISION_PENDING);
    private static final List<RenewalStatus> OPEN_RENEWALS = List.of(RenewalStatus.REQUESTED, RenewalStatus.UNDER_REVIEW);
    private final AssetIdentityAccess identities;
    private final EntityManager em;
    private final InsuranceProviderRepository providers;
    private final InsuranceProductRepository products;
    private final TechnicianInsurancePolicyRepository technicianPolicies;
    private final LiftInsurancePolicyRepository liftPolicies;
    private final LiftInsuranceApplicationRepository applications;
    private final InsuranceClaimRepository claims;
    private final InsuranceRenewalRepository renewals;
    private final InsuranceDocumentRepository documents;
    private final InsuranceExpiryNoticeRepository notices;
    private final InsuranceActivityRepository activities;
    private final AuditService audit;
    private final Clock clock;

    public ProtectionService(AssetIdentityAccess identities, EntityManager em, InsuranceProviderRepository providers,
            InsuranceProductRepository products, TechnicianInsurancePolicyRepository technicianPolicies,
            LiftInsurancePolicyRepository liftPolicies, LiftInsuranceApplicationRepository applications,
            InsuranceClaimRepository claims, InsuranceRenewalRepository renewals, InsuranceDocumentRepository documents,
            InsuranceExpiryNoticeRepository notices, InsuranceActivityRepository activities, AuditService audit, Clock clock) {
        this.identities = identities; this.em = em; this.providers = providers; this.products = products;
        this.technicianPolicies = technicianPolicies; this.liftPolicies = liftPolicies; this.applications = applications;
        this.claims = claims; this.renewals = renewals; this.documents = documents; this.notices = notices;
        this.activities = activities; this.audit = audit; this.clock = clock;
    }

    public DocumentView addTechnicianPolicyDocument(Long id, DocumentMetadataWrite input) {
        User actor = identities.actor();
        TechnicianInsurancePolicy policy = technicianPolicies.detailed(id).orElseThrow(() -> missing("Policy not found"));
        if (!isAdmin(actor) && !policy.technician.getUser().getId().equals(actor.getId())) throw denied();
        if (!isAdmin(actor)) technicianProfile(requireRole(actor, Role.TECHNICIAN));
        InsuranceDocument document = document(input, actor);
        document.technicianPolicy = policy;
        documents.saveAndFlush(document);
        audit.record("PROTECTION_TECHNICIAN_POLICY_DOCUMENT_METADATA", "TECHNICIAN_POLICY", id, "Recorded policy document metadata");
        activity(policy.technician.getUser(), actor, "POLICY_DOCUMENT_ADDED", "TECHNICIAN_POLICY", id, "Policy document metadata added");
        return documentView(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentView> technicianPolicyDocuments(Long id) {
        User actor = identities.actor();
        TechnicianInsurancePolicy policy = technicianPolicies.detailed(id).orElseThrow(() -> missing("Policy not found"));
        if (!isAdmin(actor) && !policy.technician.getUser().getId().equals(actor.getId())) throw denied();
        if (!isAdmin(actor)) technicianProfile(requireRole(actor, Role.TECHNICIAN));
        return documents.findByTechnicianPolicyIdOrderByCreatedAtAsc(id).stream().map(this::documentView).toList();
    }

    public DocumentView addLiftPolicyDocument(Long id, DocumentMetadataWrite input) {
        User actor = identities.actor();
        LiftInsurancePolicy policy = liftPolicies.detailed(id).orElseThrow(() -> missing("Policy not found"));
        if (!isAdmin(actor) && !policy.lift.getBuilding().getCustomer().getUser().getId().equals(actor.getId())) throw denied();
        if (!isAdmin(actor)) customerProfile(requireRole(actor, Role.CUSTOMER));
        InsuranceDocument document = document(input, actor);
        document.liftPolicy = policy;
        documents.saveAndFlush(document);
        audit.record("PROTECTION_LIFT_POLICY_DOCUMENT_METADATA", "LIFT_POLICY", id, "Recorded policy document metadata");
        activity(policy.lift.getBuilding().getCustomer().getUser(), actor, "POLICY_DOCUMENT_ADDED", "LIFT_POLICY", id, "Policy document metadata added");
        return documentView(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentView> liftPolicyDocuments(Long id) {
        User actor = identities.actor();
        LiftInsurancePolicy policy = liftPolicies.detailed(id).orElseThrow(() -> missing("Policy not found"));
        if (!isAdmin(actor) && !policy.lift.getBuilding().getCustomer().getUser().getId().equals(actor.getId())) throw denied();
        if (!isAdmin(actor)) customerProfile(requireRole(actor, Role.CUSTOMER));
        return documents.findByLiftPolicyIdOrderByCreatedAtAsc(id).stream().map(this::documentView).toList();
    }

    @Transactional(readOnly = true)
    public ProtectionPage<ProviderView> providers(String search, boolean admin, int page, int size) {
        admin = isAdmin(identities.actor());
        Page<InsuranceProvider> result = search == null || search.isBlank()
                ? (admin ? providers.findAll(PageRequest.of(page, size, Sort.by("name").ascending()))
                          : providers.findByActiveTrue(PageRequest.of(page, size, Sort.by("name").ascending())))
                : (admin ? providers.findByNameContainingIgnoreCase(search.trim(), PageRequest.of(page, size, Sort.by("name").ascending()))
                         : providers.findByActiveTrueAndNameContainingIgnoreCase(search.trim(), PageRequest.of(page, size, Sort.by("name").ascending())));
        return page(result.map(this::providerView));
    }

    @Transactional(readOnly = true)
    public ProviderView provider(Long id, boolean admin) {
        admin = isAdmin(identities.actor());
        InsuranceProvider provider = providers.findById(id).orElseThrow(() -> missing("Provider not found"));
        if (!admin && !provider.active) throw missing("Provider not found");
        return providerView(provider);
    }

    public ProviderView createProvider(ProviderWrite input) {
        User actor = requireAdmin();
        InsuranceProvider provider = new InsuranceProvider();
        apply(provider, input);
        provider.createdBy = actor;
        providers.saveAndFlush(provider);
        audit.record("PROTECTION_PROVIDER_CREATE", "INSURANCE_PROVIDER", provider.id, "Added protection provider");
        activity(null, actor, "PROVIDER_CREATED", "PROVIDER", provider.id, "Protection provider added to shared catalog");
        return providerView(provider);
    }

    public ProviderView updateProvider(Long id, ProviderWrite input) {
        requireAdmin();
        InsuranceProvider provider = providers.findById(id).orElseThrow(() -> missing("Provider not found"));
        apply(provider, input);
        audit.record("PROTECTION_PROVIDER_UPDATE", "INSURANCE_PROVIDER", provider.id, "Updated protection provider");
        activity(null, identities.actor(), "PROVIDER_UPDATED", "PROVIDER", provider.id, "Shared protection provider catalog updated");
        return providerView(provider);
    }

    @Transactional(readOnly = true)
    public ProtectionPage<ProductView> products(InsuredAssetType type, Long providerId, String search,
            boolean admin, int page, int size) {
        admin = isAdmin(identities.actor());
        Page<InsuranceProduct> result = products.catalog(!admin, type, providerId, normalize(search),
                PageRequest.of(page, size, Sort.by("id").descending()));
        return page(result.map(this::productView));
    }

    @Transactional(readOnly = true)
    public ProductView product(Long id, boolean admin) {
        admin = isAdmin(identities.actor());
        InsuranceProduct product = products.findById(id).orElseThrow(() -> missing("Product not found"));
        if (!admin && (!product.active || !product.provider.active)) throw missing("Product not found");
        return productView(product);
    }

    public ProductView createProduct(ProductWrite input) {
        requireAdmin();
        InsuranceProduct product = new InsuranceProduct();
        apply(product, input);
        products.saveAndFlush(product);
        audit.record("PROTECTION_PRODUCT_CREATE", "INSURANCE_PRODUCT", product.id, "Added protection product");
        activity(null, identities.actor(), "PRODUCT_CREATED", "PRODUCT", product.id, "Protection product added to shared catalog");
        return productView(product);
    }

    public ProductView updateProduct(Long id, ProductWrite input) {
        requireAdmin();
        InsuranceProduct product = products.findById(id).orElseThrow(() -> missing("Product not found"));
        apply(product, input);
        audit.record("PROTECTION_PRODUCT_UPDATE", "INSURANCE_PRODUCT", product.id, "Updated protection product");
        activity(null, identities.actor(), "PRODUCT_UPDATED", "PRODUCT", product.id, "Shared protection product catalog updated");
        return productView(product);
    }

    @Transactional(readOnly = true)
    public ProtectionPage<TechnicianPolicyView> technicianPolicies(PolicyStatus status, String search, boolean admin, int page, int size) {
        User actor = identities.actor();
        if (!admin) technicianProfile(requireRole(actor, Role.TECHNICIAN));
        Long owner = admin ? null : requireRole(actor, Role.TECHNICIAN).getId();
        if (admin) identities.requireAdmin();
        return page(technicianPolicies.list(owner, status, normalize(search), PageRequest.of(page, size, Sort.by("id").descending()))
                .map(this::technicianPolicyView));
    }

    @Transactional(readOnly = true)
    public TechnicianPolicyView technicianPolicy(Long id) {
        User actor = identities.actor();
        TechnicianInsurancePolicy policy = technicianPolicies.detailed(id).orElseThrow(() -> missing("Policy not found"));
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN
                && !policy.technician.getUser().getId().equals(actor.getId())) throw denied();
        if (actor.getRole() == Role.TECHNICIAN) technicianProfile(actor);
        return technicianPolicyView(policy);
    }

    public TechnicianPolicyView declareTechnicianPolicy(TechnicianPolicyWrite input) {
        User actor = requireRole(identities.actor(), Role.TECHNICIAN);
        TechnicianProfile technician = technicianProfile(actor);
        InsuranceProvider provider = activeProvider(input.providerId());
        InsuranceProduct product = optionalProduct(input.productId(), provider, InsuredAssetType.TECHNICIAN);
        validatePolicyDates(input.coverageStartDate(), input.coverageEndDate());
        TechnicianInsurancePolicy policy = new TechnicianInsurancePolicy();
        policy.technician = technician;
        policy.provider = provider;
        policy.product = product;
        policy.insurerPolicyNumber = input.insurerPolicyNumber().trim();
        policy.coverageStartDate = input.coverageStartDate();
        policy.coverageEndDate = input.coverageEndDate();
        policy.premiumAmount = input.premiumAmount();
        policy.currency = currency(input.currency());
        policy.notes = clean(input.notes());
        policy.status = PolicyStatus.DECLARED;
        technicianPolicies.saveAndFlush(policy);
        audit.record("TECHNICIAN_POLICY_DECLARED", "TECHNICIAN_POLICY", policy.id, "Technician recorded an insurer-issued policy for verification");
        activity(actor, actor, "POLICY_DECLARED", "TECHNICIAN_POLICY", policy.id, "Technician policy details recorded; insurer confirmation pending");
        return technicianPolicyView(policy);
    }

    public TechnicianPolicyView confirmTechnicianPolicy(Long id, PolicyReviewWrite input) {
        User actor = requireAdmin();
        TechnicianInsurancePolicy policy = technicianPolicies.detailed(id).orElseThrow(() -> missing("Policy not found"));
        updatePolicyStatus(policy.status, input.status());
        policy.status = input.status();
        policy.insurerReference = input.insurerReference().trim();
        policy.insurerReviewNote = input.insurerReviewNote().trim();
        audit.record("TECHNICIAN_POLICY_INSURER_STATUS", "TECHNICIAN_POLICY", id, "Recorded insurer-reported policy status");
        activity(policy.technician.getUser(), actor, "POLICY_INSURER_STATUS", "TECHNICIAN_POLICY", id,
                "Insurer-reported policy status recorded as " + policy.status.name());
        return technicianPolicyView(policy);
    }

    @Transactional(readOnly = true)
    public ProtectionPage<LiftPolicyView> liftPolicies(PolicyStatus status, String search, boolean admin, int page, int size) {
        User actor = identities.actor();
        if (!admin) customerProfile(requireRole(actor, Role.CUSTOMER));
        Long owner = admin ? null : requireRole(actor, Role.CUSTOMER).getId();
        if (admin) identities.requireAdmin();
        return page(liftPolicies.list(owner, status, normalize(search), PageRequest.of(page, size, Sort.by("id").descending()))
                .map(this::liftPolicyView));
    }

    @Transactional(readOnly = true)
    public LiftPolicyView liftPolicy(Long id) {
        User actor = identities.actor();
        LiftInsurancePolicy policy = liftPolicies.detailed(id).orElseThrow(() -> missing("Policy not found"));
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN
                && !policy.lift.getBuilding().getCustomer().getUser().getId().equals(actor.getId())) throw denied();
        if (actor.getRole() == Role.CUSTOMER) customerProfile(actor);
        return liftPolicyView(policy);
    }

    public LiftPolicyView declareLiftPolicy(LiftPolicyWrite input) {
        User actor = requireRole(identities.actor(), Role.CUSTOMER);
        CustomerProfile customer = customerProfile(actor);
        Lift lift = ownedLift(input.liftId(), customer);
        InsuranceProvider provider = activeProvider(input.providerId());
        InsuranceProduct product = optionalProduct(input.productId(), provider, InsuredAssetType.LIFT);
        validatePolicyDates(input.coverageStartDate(), input.coverageEndDate());
        LiftInsurancePolicy policy = new LiftInsurancePolicy();
        policy.lift = lift;
        policy.provider = provider;
        policy.product = product;
        policy.insurerPolicyNumber = input.insurerPolicyNumber().trim();
        policy.coverageStartDate = input.coverageStartDate();
        policy.coverageEndDate = input.coverageEndDate();
        policy.premiumAmount = input.premiumAmount();
        policy.insuredValue = input.insuredValue();
        policy.currency = currency(input.currency());
        policy.notes = clean(input.notes());
        policy.status = PolicyStatus.DECLARED;
        liftPolicies.saveAndFlush(policy);
        audit.record("LIFT_POLICY_DECLARED", "LIFT_POLICY", policy.id, "Customer recorded an insurer-issued lift policy for verification");
        activity(actor, actor, "POLICY_DECLARED", "LIFT_POLICY", policy.id, "Lift policy details recorded; insurer confirmation pending");
        return liftPolicyView(policy);
    }

    public LiftPolicyView confirmLiftPolicy(Long id, PolicyReviewWrite input) {
        User actor = requireAdmin();
        LiftInsurancePolicy policy = liftPolicies.detailed(id).orElseThrow(() -> missing("Policy not found"));
        updatePolicyStatus(policy.status, input.status());
        policy.status = input.status();
        policy.insurerReference = input.insurerReference().trim();
        policy.insurerReviewNote = input.insurerReviewNote().trim();
        audit.record("LIFT_POLICY_INSURER_STATUS", "LIFT_POLICY", id, "Recorded insurer-reported policy status");
        activity(policy.lift.getBuilding().getCustomer().getUser(), actor, "POLICY_INSURER_STATUS", "LIFT_POLICY", id,
                "Insurer-reported policy status recorded as " + policy.status.name());
        return liftPolicyView(policy);
    }

    @Transactional(readOnly = true)
    public ProtectionPage<LiftApplicationView> applications(ApplicationStatus status, String search, boolean admin, int page, int size) {
        User actor = identities.actor();
        if (!admin) customerProfile(requireRole(actor, Role.CUSTOMER));
        Long owner = admin ? null : requireRole(actor, Role.CUSTOMER).getId();
        if (admin) identities.requireAdmin();
        return page(applications.list(owner, status, normalize(search), PageRequest.of(page, size, Sort.by("id").descending()))
                .map(this::applicationView));
    }

    @Transactional(readOnly = true)
    public LiftApplicationView application(Long id) {
        User actor = identities.actor();
        LiftInsuranceApplication application = applications.detailed(id).orElseThrow(() -> missing("Application not found"));
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN
                && !application.customer.getUser().getId().equals(actor.getId())) throw denied();
        if (actor.getRole() == Role.CUSTOMER) customerProfile(actor);
        return applicationView(application);
    }

    public LiftApplicationView createApplication(LiftApplicationWrite input) {
        User actor = requireRole(identities.actor(), Role.CUSTOMER);
        CustomerProfile customer = customerProfile(actor);
        Lift lift = ownedLift(input.liftId(), customer);
        InsuranceProvider provider = activeProvider(input.providerId());
        InsuranceProduct product = optionalProduct(input.productId(), provider, InsuredAssetType.LIFT);
        LiftInsuranceApplication application = new LiftInsuranceApplication();
        application.customer = customer;
        application.lift = lift;
        application.provider = provider;
        application.product = product;
        application.coverageRequested = input.coverageRequested().trim();
        application.insuredValue = input.insuredValue();
        application.status = ApplicationStatus.SUBMITTED;
        applications.saveAndFlush(application);
        audit.record("LIFT_INSURANCE_APPLICATION", "LIFT_APPLICATION", application.id, "Submitted manual insurer quote request");
        activity(actor, actor, "APPLICATION_SUBMITTED", "LIFT_APPLICATION", application.id,
                "Lift insurance application submitted for manual insurer follow-up");
        return applicationView(application);
    }

    public LiftApplicationView recordQuote(Long id, QuoteWrite input) {
        User actor = requireAdmin();
        LiftInsuranceApplication application = applications.detailed(id).orElseThrow(() -> missing("Application not found"));
        if (application.status != ApplicationStatus.SUBMITTED) throw conflict("Application is not awaiting a quote");
        if (input.quoteExpiresOn().isBefore(today())) throw bad("Quote expiry must be today or later");
        application.quoteAmount = input.quoteAmount();
        application.quoteCurrency = currency(input.quoteCurrency());
        application.quoteExpiresOn = input.quoteExpiresOn();
        application.insurerQuoteReference = clean(input.insurerQuoteReference());
        application.insurerNotes = clean(input.insurerNotes());
        application.status = ApplicationStatus.QUOTE_RECEIVED;
        audit.record("LIFT_INSURANCE_QUOTE_RECORDED", "LIFT_APPLICATION", id, "Manually recorded insurer-provided quote");
        activity(application.customer.getUser(), actor, "QUOTE_RECORDED", "LIFT_APPLICATION", id,
                "Quote manually relayed from insurer; insurer remains the decision maker");
        return applicationView(application);
    }

    public LiftApplicationView decideApplication(Long id, ApplicationDecisionWrite input) {
        User actor = requireRole(identities.actor(), Role.CUSTOMER);
        LiftInsuranceApplication application = applications.detailed(id).orElseThrow(() -> missing("Application not found"));
        ensureApplicationOwner(application, actor);
        if (application.status != ApplicationStatus.QUOTE_RECEIVED) throw conflict("Application has no quote to decide");
        ApplicationStatus decision = ApplicationStatus.valueOf(input.status());
        if (decision == ApplicationStatus.CUSTOMER_ACCEPTED
                && (application.quoteExpiresOn == null || application.quoteExpiresOn.isBefore(today())))
            throw conflict("Quote has expired or is missing an expiry date; request a fresh quote");
        application.status = decision;
        audit.record("LIFT_INSURANCE_APPLICATION_DECISION", "LIFT_APPLICATION", id, "Customer recorded a quote response");
        activity(actor, actor, "APPLICATION_DECISION", "LIFT_APPLICATION", id, "Customer recorded a response to the quote");
        return applicationView(application);
    }

    public LiftApplicationView cancelApplication(Long id) {
        User actor = requireRole(identities.actor(), Role.CUSTOMER);
        LiftInsuranceApplication application = applications.detailed(id).orElseThrow(() -> missing("Application not found"));
        ensureApplicationOwner(application, actor);
        if (application.status != ApplicationStatus.SUBMITTED && application.status != ApplicationStatus.QUOTE_RECEIVED)
            throw conflict("Application cannot be cancelled in its current state");
        application.status = ApplicationStatus.CANCELLED;
        audit.record("LIFT_INSURANCE_APPLICATION_CANCELLED", "LIFT_APPLICATION", id, "Customer cancelled lift insurance application");
        activity(actor, actor, "APPLICATION_CANCELLED", "LIFT_APPLICATION", id, "Lift insurance application cancelled");
        return applicationView(application);
    }

    @Transactional(readOnly = true)
    public ProtectionPage<ClaimView> claims(ClaimStatus status, String search, boolean admin, int page, int size) {
        User actor = identities.actor();
        if (!admin) validateOwnerProfile(actor);
        Long owner = admin ? null : actor.getId();
        if (admin) identities.requireAdmin();
        return page(claims.list(owner, status, normalize(search), PageRequest.of(page, size, Sort.by("id").descending()))
                .map(this::claimView));
    }

    @Transactional(readOnly = true)
    public ClaimView claim(Long id) {
        User actor = identities.actor();
        InsuranceClaim claim = claims.detailed(id).orElseThrow(() -> missing("Claim not found"));
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN
                && !claim.claimant.getId().equals(actor.getId())) throw denied();
        if (claim.claimant.getId().equals(actor.getId())) validateOwnerProfile(actor);
        return claimView(claim);
    }

    public ClaimView createClaim(ClaimWrite input) {
        User actor = identities.actor();
        InsuranceClaim claim = new InsuranceClaim();
        claim.claimant = actor;
        claim.incidentDate = input.incidentDate();
        if (input.incidentDate().isAfter(today())) throw bad("Incident date cannot be in the future");
        claim.description = input.description().trim();
        claim.claimedAmount = input.claimedAmount();
        claim.currency = currency(input.currency());
        if (input.policyType() == InsuredAssetType.TECHNICIAN) {
            requireRole(actor, Role.TECHNICIAN);
            technicianProfile(actor);
            TechnicianInsurancePolicy policy = technicianPolicies.detailed(input.policyId()).orElseThrow(() -> missing("Policy not found"));
            if (!policy.technician.getUser().getId().equals(actor.getId())) throw denied();
            claim.technicianPolicy = policy;
        } else {
            requireRole(actor, Role.CUSTOMER);
            customerProfile(actor);
            LiftInsurancePolicy policy = liftPolicies.detailed(input.policyId()).orElseThrow(() -> missing("Policy not found"));
            if (!policy.lift.getBuilding().getCustomer().getUser().getId().equals(actor.getId())) throw denied();
            claim.liftPolicy = policy;
        }
        claim.claimReference = "VCL-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        claims.saveAndFlush(claim);
        audit.record("PROTECTION_CLAIM_SUBMITTED", "INSURANCE_CLAIM", claim.id, "Claim submitted for insurer review");
        activity(actor, actor, "CLAIM_SUBMITTED", "CLAIM", claim.id, "Insurance claim submitted; insurer determines coverage and outcome");
        return claimView(claim);
    }

    public ClaimView updateClaimFromInsurer(Long id, InsurerClaimUpdate input) {
        User actor = requireAdmin();
        InsuranceClaim claim = claims.detailed(id).orElseThrow(() -> missing("Claim not found"));
        ClaimStatus next = input.status();
        if (!List.of(ClaimStatus.DOCUMENTS_REQUESTED, ClaimStatus.FORWARDED_TO_INSURER,
                ClaimStatus.INSURER_DECISION_PENDING, ClaimStatus.INSURER_APPROVED, ClaimStatus.INSURER_REJECTED).contains(next))
            throw bad("Unsupported insurer claim status");
        boolean validTransition = switch (claim.status) {
            case SUBMITTED -> next == ClaimStatus.DOCUMENTS_REQUESTED || next == ClaimStatus.FORWARDED_TO_INSURER;
            case DOCUMENTS_REQUESTED -> next == ClaimStatus.FORWARDED_TO_INSURER;
            case FORWARDED_TO_INSURER -> next == ClaimStatus.INSURER_DECISION_PENDING;
            case INSURER_DECISION_PENDING -> next == ClaimStatus.INSURER_APPROVED || next == ClaimStatus.INSURER_REJECTED;
            default -> false;
        };
        if (!validTransition) throw conflict("Invalid claim status transition");
        if ((next == ClaimStatus.INSURER_APPROVED || next == ClaimStatus.INSURER_REJECTED)
                && (clean(input.insurerClaimReference()) == null || clean(input.insurerResponseNote()) == null))
            throw bad("Insurer reference and reported decision are required for an insurer decision");
        claim.status = next;
        claim.insurerClaimReference = clean(input.insurerClaimReference());
        claim.insurerResponseNote = clean(input.insurerResponseNote());
        audit.record("PROTECTION_CLAIM_INSURER_STATUS", "INSURANCE_CLAIM", id, "Recorded insurer-reported claim status");
        activity(claim.claimant, actor, "CLAIM_INSURER_STATUS", "CLAIM", id,
                "Insurer-reported claim status recorded as " + next.name());
        return claimView(claim);
    }

    public ClaimView withdrawClaim(Long id) {
        User actor = identities.actor();
        InsuranceClaim claim = claims.detailed(id).orElseThrow(() -> missing("Claim not found"));
        if (!claim.claimant.getId().equals(actor.getId())) throw denied();
        validateOwnerProfile(actor);
        if (claim.status != ClaimStatus.SUBMITTED && claim.status != ClaimStatus.DOCUMENTS_REQUESTED)
            throw conflict("Claim cannot be withdrawn after it has been sent to the insurer");
        claim.status = ClaimStatus.WITHDRAWN;
        audit.record("PROTECTION_CLAIM_WITHDRAWN", "INSURANCE_CLAIM", id, "Claimant withdrew claim before insurer decision");
        activity(actor, actor, "CLAIM_WITHDRAWN", "CLAIM", id, "Insurance claim withdrawn by claimant");
        return claimView(claim);
    }

    public DocumentView addClaimDocument(Long id, DocumentMetadataWrite input) {
        User actor = identities.actor();
        InsuranceClaim claim = claims.detailed(id).orElseThrow(() -> missing("Claim not found"));
        if (!isAdmin(actor) && !claim.claimant.getId().equals(actor.getId())) throw denied();
        if (claim.claimant.getId().equals(actor.getId())) validateOwnerProfile(actor);
        InsuranceDocument document = document(input, actor);
        document.claim = claim;
        documents.saveAndFlush(document);
        audit.record("PROTECTION_CLAIM_DOCUMENT_METADATA", "INSURANCE_CLAIM", id, "Recorded claim document metadata");
        activity(claim.claimant, actor, "CLAIM_DOCUMENT_ADDED", "CLAIM", id, "Claim document metadata added");
        return documentView(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentView> claimDocuments(Long id) {
        User actor = identities.actor();
        InsuranceClaim claim = claims.detailed(id).orElseThrow(() -> missing("Claim not found"));
        if (!isAdmin(actor) && !claim.claimant.getId().equals(actor.getId())) throw denied();
        if (claim.claimant.getId().equals(actor.getId())) validateOwnerProfile(actor);
        return documents.findByClaimIdOrderByCreatedAtAsc(id).stream().map(this::documentView).toList();
    }

    @Transactional(readOnly = true)
    public ProtectionPage<RenewalView> renewals(RenewalStatus status, boolean admin, int page, int size) {
        User actor = identities.actor();
        if (!admin) validateOwnerProfile(actor);
        Long owner = admin ? null : actor.getId();
        if (admin) identities.requireAdmin();
        return page(renewals.list(owner, status, PageRequest.of(page, size, Sort.by("id").descending())).map(this::renewalView));
    }

    public RenewalView requestRenewal(RenewalWrite input) {
        User actor = identities.actor();
        InsuranceRenewalRequest renewal = new InsuranceRenewalRequest();
        if (input.policyType() == InsuredAssetType.TECHNICIAN) {
            requireRole(actor, Role.TECHNICIAN);
            technicianProfile(actor);
            TechnicianInsurancePolicy policy = technicianPolicies.detailed(input.policyId()).orElseThrow(() -> missing("Policy not found"));
            if (!policy.technician.getUser().getId().equals(actor.getId())) throw denied();
            renewal.technicianPolicy = policy;
            ensureNoOpenRenewal(actor, policy.id, null);
        } else {
            requireRole(actor, Role.CUSTOMER);
            customerProfile(actor);
            LiftInsurancePolicy policy = liftPolicies.detailed(input.policyId()).orElseThrow(() -> missing("Policy not found"));
            if (!policy.lift.getBuilding().getCustomer().getUser().getId().equals(actor.getId())) throw denied();
            renewal.liftPolicy = policy;
            ensureNoOpenRenewal(actor, null, policy.id);
        }
        renewal.requestedBy = actor;
        renewal.requestedStartDate = input.requestedStartDate();
        renewal.note = clean(input.note());
        renewals.saveAndFlush(renewal);
        audit.record("PROTECTION_RENEWAL_REQUESTED", "INSURANCE_RENEWAL", renewal.id, "Submitted manual insurer renewal request");
        activity(actor, actor, "RENEWAL_REQUESTED", "RENEWAL", renewal.id,
                "Renewal request submitted for manual insurer follow-up");
        return renewalView(renewal);
    }

    public RenewalView updateRenewalFromInsurer(Long id, InsurerRenewalUpdate input) {
        User actor = requireAdmin();
        InsuranceRenewalRequest renewal = renewals.detailed(id).orElseThrow(() -> missing("Renewal not found"));
        RenewalStatus next = input.status();
        if (next != RenewalStatus.UNDER_REVIEW && next != RenewalStatus.INSURER_CONFIRMED && next != RenewalStatus.INSURER_DECLINED)
            throw bad("Unsupported insurer renewal status");
        if (renewal.status != RenewalStatus.REQUESTED && renewal.status != RenewalStatus.UNDER_REVIEW)
            throw conflict("Renewal request is already closed");
        if ((next == RenewalStatus.INSURER_CONFIRMED || next == RenewalStatus.INSURER_DECLINED)
                && (clean(input.insurerReference()) == null || clean(input.insurerResponseNote()) == null))
            throw bad("Insurer reference and reported outcome are required");
        renewal.status = next;
        renewal.insurerReference = clean(input.insurerReference());
        renewal.insurerResponseNote = clean(input.insurerResponseNote());
        audit.record("PROTECTION_RENEWAL_INSURER_STATUS", "INSURANCE_RENEWAL", id, "Recorded insurer-reported renewal outcome");
        activity(renewal.requestedBy, actor, "RENEWAL_INSURER_STATUS", "RENEWAL", id,
                "Insurer-reported renewal status recorded as " + next.name() + "; no policy was issued by Valor");
        return renewalView(renewal);
    }

    public RenewalView cancelRenewal(Long id) {
        User actor = identities.actor();
        InsuranceRenewalRequest renewal = renewals.detailed(id).orElseThrow(() -> missing("Renewal not found"));
        if (!renewal.requestedBy.getId().equals(actor.getId())) throw denied();
        validateOwnerProfile(actor);
        if (renewal.status != RenewalStatus.REQUESTED) throw conflict("Renewal request can no longer be cancelled");
        renewal.status = RenewalStatus.CANCELLED;
        audit.record("PROTECTION_RENEWAL_CANCELLED", "INSURANCE_RENEWAL", id, "Customer or technician cancelled renewal request");
        activity(actor, actor, "RENEWAL_CANCELLED", "RENEWAL", id, "Renewal request cancelled");
        return renewalView(renewal);
    }

    public DocumentView addApplicationDocument(Long id, DocumentMetadataWrite input) {
        User actor = requireRole(identities.actor(), Role.CUSTOMER);
        LiftInsuranceApplication application = applications.detailed(id).orElseThrow(() -> missing("Application not found"));
        ensureApplicationOwner(application, actor);
        customerProfile(actor);
        InsuranceDocument document = document(input, actor);
        document.application = application;
        documents.saveAndFlush(document);
        audit.record("PROTECTION_APPLICATION_DOCUMENT_METADATA", "LIFT_APPLICATION", id, "Recorded application document metadata");
        activity(actor, actor, "APPLICATION_DOCUMENT_ADDED", "LIFT_APPLICATION", id, "Application document metadata added");
        return documentView(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentView> applicationDocuments(Long id) {
        User actor = identities.actor();
        LiftInsuranceApplication application = applications.detailed(id).orElseThrow(() -> missing("Application not found"));
        if (!isAdmin(actor) && !application.customer.getUser().getId().equals(actor.getId())) throw denied();
        if (application.customer.getUser().getId().equals(actor.getId())) customerProfile(actor);
        return documents.findByApplicationIdOrderByCreatedAtAsc(id).stream().map(this::documentView).toList();
    }

    public ExpiryNoticeView readNotice(Long id) {
        User actor = identities.actor();
        validateOwnerProfile(actor);
        InsuranceExpiryNotice notice = notices.findByIdAndRecipientId(id, actor.getId()).orElseThrow(() -> missing("Notice not found"));
        if (notice.readAt == null) notice.readAt = LocalDateTime.now(clock);
        return noticeView(notice);
    }

    @Transactional(readOnly = true)
    public ProtectionPage<ExpiryNoticeView> expiryNotices(int page, int size) {
        User actor = identities.actor();
        validateOwnerProfile(actor);
        return page(notices.findByRecipientId(actor.getId(), PageRequest.of(page, size, Sort.by("id").descending()))
                .map(this::noticeView));
    }

    @Transactional(readOnly = true)
    public ProtectionDashboard dashboard() {
        User actor = identities.actor();
        validateOwnerProfile(actor);
        boolean admin = isAdmin(actor);
        long technicianCount = admin ? count("select count(p) from TechnicianInsurancePolicy p")
                : actor.getRole() == Role.TECHNICIAN ? count("select count(p) from TechnicianInsurancePolicy p where p.technician.user.id=:owner", actor.getId()) : 0;
        long liftCount = admin ? count("select count(p) from LiftInsurancePolicy p")
                : actor.getRole() == Role.CUSTOMER ? count("select count(p) from LiftInsurancePolicy p where p.lift.building.customer.user.id=:owner", actor.getId()) : 0;
        List<ApplicationStatus> pendingApplicationStatuses = List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.QUOTE_RECEIVED);
        long applicationCount = admin
                ? countApplications("select count(a) from LiftInsuranceApplication a where a.status in :statuses", null, pendingApplicationStatuses)
                : actor.getRole() == Role.CUSTOMER ? countApplications(
                        "select count(a) from LiftInsuranceApplication a where a.customer.user.id=:owner and a.status in :statuses",
                        actor.getId(), pendingApplicationStatuses) : 0;
        long claimCount = admin ? claims.countByStatusIn(OPEN_CLAIMS) : claims.countByClaimantIdAndStatusIn(actor.getId(), OPEN_CLAIMS);
        long renewalCount = countOpenRenewals(actor, admin);
        long expiring = expiringCount(actor, admin);
        long unread = notices.countByRecipientIdAndReadAtIsNull(actor.getId());
        Page<InsuranceActivity> activityPage = admin
                ? activities.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, 10))
                : activities.findByVisibleToId(actor.getId(), PageRequest.of(0, 10, Sort.by("createdAt").descending().and(Sort.by("id").descending())));
        return new ProtectionDashboard(technicianCount, liftCount, applicationCount, claimCount, renewalCount,
                expiring, unread, activityPage.getContent().stream().map(this::activityView).toList());
    }

    @Transactional
    public void generateExpiryNotices() {
        LocalDate current = today();
        for (int days : new int[]{30, 15, 7, 0}) {
            LocalDate expiry = current.plusDays(days);
            for (TechnicianInsurancePolicy policy : technicianPolicies.expiringOn(expiry)) {
                if (policy.status == PolicyStatus.INSURER_CONFIRMED_ACTIVE) addNotice(InsuredAssetType.TECHNICIAN,
                        policy.id, policy.technician.getUser(), expiry, days);
            }
            for (LiftInsurancePolicy policy : liftPolicies.expiringOn(expiry)) {
                if (policy.status == PolicyStatus.INSURER_CONFIRMED_ACTIVE) addNotice(InsuredAssetType.LIFT,
                        policy.id, policy.lift.getBuilding().getCustomer().getUser(), expiry, days);
            }
        }
    }

    private void addNotice(InsuredAssetType type, Long policyId, User recipient, LocalDate expiry, int days) {
        short stage = (short) days;
        if (notices.existsByPolicyTypeAndPolicyIdAndDaysBeforeExpiry(type, policyId, stage)) return;
        InsuranceExpiryNotice notice = new InsuranceExpiryNotice();
        notice.recipient = recipient; notice.policyType = type; notice.policyId = policyId;
        notice.daysBeforeExpiry = stage; notice.expiryDate = expiry;
        notice.message = type == InsuredAssetType.TECHNICIAN
                ? "Your technician insurance policy expires on " + expiry + ". Contact the insurer to discuss renewal."
                : "Your lift insurance policy expires on " + expiry + ". Contact the insurer to discuss renewal.";
        notices.save(notice);
        activity(recipient, null, "POLICY_EXPIRY_NOTICE", type.name() + "_POLICY", policyId, "A policy expiry reminder was generated");
    }

    private long countOpenRenewals(User actor, boolean admin) {
        if (admin) return em.createQuery("select count(r) from InsuranceRenewalRequest r where r.status in :statuses", Long.class)
                .setParameter("statuses", OPEN_RENEWALS).getSingleResult();
        return renewals.countByRequestedByIdAndStatusIn(actor.getId(), OPEN_RENEWALS);
    }

    private long expiringCount(User actor, boolean admin) {
        LocalDate today = today();
        LocalDate end = today.plusDays(30);
        if (admin) return count("select count(p) from TechnicianInsurancePolicy p where p.status=:active and p.coverageEndDate between :today and :end", PolicyStatus.INSURER_CONFIRMED_ACTIVE, today, end)
                + count("select count(p) from LiftInsurancePolicy p where p.status=:active and p.coverageEndDate between :today and :end", PolicyStatus.INSURER_CONFIRMED_ACTIVE, today, end);
        if (actor.getRole() == Role.TECHNICIAN)
            return count("select count(p) from TechnicianInsurancePolicy p where p.technician.user.id=:owner and p.status=:active and p.coverageEndDate between :today and :end", actor.getId(), PolicyStatus.INSURER_CONFIRMED_ACTIVE, today, end);
        if (actor.getRole() == Role.CUSTOMER)
            return count("select count(p) from LiftInsurancePolicy p where p.lift.building.customer.user.id=:owner and p.status=:active and p.coverageEndDate between :today and :end", actor.getId(), PolicyStatus.INSURER_CONFIRMED_ACTIVE, today, end);
        return 0;
    }

    private void ensureNoOpenRenewal(User actor, Long technicianPolicyId, Long liftPolicyId) {
        boolean exists = em.createQuery("select count(r) from InsuranceRenewalRequest r where r.requestedBy.id=:owner and r.status in :statuses and "
                        + (technicianPolicyId != null ? "r.technicianPolicy.id=:policy" : "r.liftPolicy.id=:policy"), Long.class)
                .setParameter("owner", actor.getId()).setParameter("statuses", OPEN_RENEWALS)
                .setParameter("policy", technicianPolicyId != null ? technicianPolicyId : liftPolicyId).getSingleResult() > 0;
        if (exists) throw conflict("An open renewal request already exists for this policy");
    }

    private void apply(InsuranceProvider provider, ProviderWrite input) {
        provider.name = input.name().trim();
        provider.description = clean(input.description());
        provider.websiteUrl = clean(input.websiteUrl());
        provider.supportEmail = clean(input.supportEmail());
        provider.supportPhone = clean(input.supportPhone());
        provider.active = input.active();
    }

    private void apply(InsuranceProduct product, ProductWrite input) {
        product.provider = providers.findById(input.providerId()).orElseThrow(() -> missing("Provider not found"));
        product.name = input.name().trim();
        product.insuredAssetType = input.insuredAssetType();
        product.coverageSummary = input.coverageSummary().trim();
        product.eligibility = clean(input.eligibility());
        product.minimumPremium = input.minimumPremium();
        product.maximumPremium = input.maximumPremium();
        if (product.minimumPremium != null && product.maximumPremium != null && product.maximumPremium.compareTo(product.minimumPremium) < 0)
            throw bad("Maximum premium must not be less than minimum premium");
        product.currency = currency(input.currency());
        product.active = input.active();
    }

    private InsuranceProvider activeProvider(Long id) {
        InsuranceProvider provider = providers.findById(id).orElseThrow(() -> missing("Provider not found"));
        if (!provider.active) throw conflict("Provider is inactive");
        return provider;
    }

    private InsuranceProduct optionalProduct(Long id, InsuranceProvider provider, InsuredAssetType type) {
        if (id == null) return null;
        InsuranceProduct product = products.findById(id).orElseThrow(() -> missing("Product not found"));
        if (!product.active || !product.provider.active || !product.provider.id.equals(provider.id)
                || product.insuredAssetType != type) throw bad("Product does not match the selected provider and insured asset");
        return product;
    }

    private void validatePolicyDates(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) throw bad("Coverage end date must be on or after start date");
    }

    private void updatePolicyStatus(PolicyStatus current, PolicyStatus next) {
        if (next == PolicyStatus.DECLARED || (current != PolicyStatus.DECLARED
                && current == next)) throw conflict("An insurer-reported status change is required");
    }

    private TechnicianProfile technicianProfile(User actor) {
        TechnicianProfile technician = em.createQuery("select t from TechnicianProfile t where t.user.id=:userId", TechnicianProfile.class)
                .setParameter("userId", actor.getId()).getResultStream().findFirst().orElseThrow(() -> denied());
        if (!technician.isActive()) throw denied();
        return technician;
    }

    private CustomerProfile customerProfile(User actor) {
        identities.requireActiveCustomerUser(actor);
        CustomerProfile customer = em.createQuery("select c from CustomerProfile c where c.user.id=:userId", CustomerProfile.class)
                .setParameter("userId", actor.getId()).getResultStream().findFirst().orElseThrow(() -> denied());
        return identities.activeCustomer(customer.getId());
    }

    private void validateOwnerProfile(User actor) {
        if (actor.getRole() == Role.CUSTOMER) customerProfile(actor);
        else if (actor.getRole() == Role.TECHNICIAN) technicianProfile(actor);
    }

    private Lift ownedLift(Long id, CustomerProfile customer) {
        Lift lift = em.find(Lift.class, id);
        if (lift == null || !lift.isActive()) throw missing("Lift not found");
        if (!lift.getBuilding().getCustomer().getId().equals(customer.getId())) throw denied();
        return lift;
    }

    private void ensureApplicationOwner(LiftInsuranceApplication application, User actor) {
        if (!application.customer.getUser().getId().equals(actor.getId())) throw denied();
    }

    private InsuranceDocument document(DocumentMetadataWrite input, User actor) {
        InsuranceDocument document = new InsuranceDocument();
        document.uploadedBy = actor;
        document.documentType = input.documentType().trim();
        document.originalFilename = input.originalFilename().trim();
        document.contentType = input.contentType().trim();
        document.fileSize = input.fileSize();
        document.storageKey = input.storageKey().trim();
        return document;
    }

    private void activity(User visibleTo, User actor, String type, String entityType, Long entityId, String summary) {
        InsuranceActivity row = new InsuranceActivity();
        row.visibleTo = visibleTo;
        row.actor = actor;
        row.activityType = type;
        row.entityType = entityType;
        row.entityId = entityId;
        row.summary = summary;
        activities.save(row);
    }

    private long count(String jpql, Long owner) {
        return em.createQuery(jpql, Long.class).setParameter("owner", owner).getSingleResult();
    }

    private long count(String jpql) {
        return em.createQuery(jpql, Long.class).getSingleResult();
    }

    private long count(String jpql, Long owner, Object status) {
        return em.createQuery(jpql, Long.class).setParameter("owner", owner).setParameter("status", status).getSingleResult();
    }

    private long countApplications(String jpql, Long owner, List<ApplicationStatus> statuses) {
        var query = em.createQuery(jpql, Long.class).setParameter("statuses", statuses);
        if (owner != null) query.setParameter("owner", owner);
        return query.getSingleResult();
    }

    private long count(String jpql, Object status) {
        return em.createQuery(jpql, Long.class).setParameter("status", status).getSingleResult();
    }

    private long count(String jpql, Object first, Object second, Object third) {
        return em.createQuery(jpql, Long.class).setParameter("active", first).setParameter("today", second).setParameter("end", third).getSingleResult();
    }

    private long count(String jpql, Long owner, PolicyStatus active, LocalDate today, LocalDate end) {
        return em.createQuery(jpql, Long.class).setParameter("owner", owner).setParameter("active", active)
                .setParameter("today", today).setParameter("end", end).getSingleResult();
    }

    private void requireAdminIdentity() { identities.requireAdmin(); }
    private User requireAdmin() { User actor = identities.actor(); requireAdminIdentity(); return actor; }
    private User requireRole(User actor, Role role) {
        if (actor.getRole() != role) throw denied();
        return actor;
    }
    private boolean isAdmin(User user) { return user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN; }
    private LocalDate today() { return LocalDate.now(clock); }
    private String currency(String value) { return value == null || value.isBlank() ? "INR" : value.trim().toUpperCase(Locale.ROOT); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private ProviderView providerView(InsuranceProvider p) { return new ProviderView(p.id, p.name, p.description, p.websiteUrl, p.supportEmail, p.supportPhone, p.active); }
    private ProductView productView(InsuranceProduct p) {
        return new ProductView(p.id, p.provider.id, p.provider.name, p.name, p.insuredAssetType, p.coverageSummary,
                p.eligibility, p.minimumPremium, p.maximumPremium, p.currency, p.active);
    }
    private TechnicianPolicyView technicianPolicyView(TechnicianInsurancePolicy p) {
        return new TechnicianPolicyView(p.id, p.technician.getId(), p.provider.id, p.provider.name, p.product == null ? null : p.product.id,
                p.product == null ? null : p.product.name, p.insurerPolicyNumber, p.coverageStartDate, p.coverageEndDate, p.premiumAmount,
                p.currency, p.status, p.insurerReference, p.insurerReviewNote, p.notes, p.createdAt);
    }
    private LiftPolicyView liftPolicyView(LiftInsurancePolicy p) {
        return new LiftPolicyView(p.id, p.lift.getId(), p.lift.getName(), p.provider.id, p.provider.name,
                p.product == null ? null : p.product.id, p.product == null ? null : p.product.name, p.insurerPolicyNumber,
                p.coverageStartDate, p.coverageEndDate, p.premiumAmount, p.insuredValue, p.currency, p.status,
                p.insurerReference, p.insurerReviewNote, p.notes, p.createdAt);
    }
    private LiftApplicationView applicationView(LiftInsuranceApplication a) {
        return new LiftApplicationView(a.id, a.lift.getId(), a.lift.getName(), a.provider.id, a.provider.name,
                a.product == null ? null : a.product.id, a.product == null ? null : a.product.name, a.status,
                a.coverageRequested, a.insuredValue, a.quoteAmount, a.quoteCurrency, a.quoteExpiresOn,
                a.insurerQuoteReference, a.insurerNotes, a.submittedAt);
    }
    private ClaimView claimView(InsuranceClaim c) {
        boolean technician = c.technicianPolicy != null;
        return new ClaimView(c.id, c.claimReference, technician ? InsuredAssetType.TECHNICIAN : InsuredAssetType.LIFT,
                technician ? c.technicianPolicy.id : c.liftPolicy.id,
                technician ? c.technicianPolicy.insurerPolicyNumber : c.liftPolicy.insurerPolicyNumber,
                technician ? c.technicianPolicy.provider.name : c.liftPolicy.provider.name, c.status, c.incidentDate,
                c.description, c.claimedAmount, c.currency, c.insurerClaimReference, c.insurerResponseNote, c.createdAt, c.updatedAt);
    }
    private RenewalView renewalView(InsuranceRenewalRequest r) {
        boolean technician = r.technicianPolicy != null;
        return new RenewalView(r.id, technician ? InsuredAssetType.TECHNICIAN : InsuredAssetType.LIFT,
                technician ? r.technicianPolicy.id : r.liftPolicy.id,
                technician ? r.technicianPolicy.insurerPolicyNumber : r.liftPolicy.insurerPolicyNumber,
                technician ? r.technicianPolicy.provider.name : r.liftPolicy.provider.name, r.status, r.requestedStartDate,
                r.note, r.insurerReference, r.insurerResponseNote, r.createdAt, r.updatedAt);
    }
    private DocumentView documentView(InsuranceDocument d) {
        return new DocumentView(d.id, d.documentType, d.originalFilename, d.contentType, d.fileSize, d.storageKey,
                d.uploadedBy.getId(), d.createdAt);
    }
    private ExpiryNoticeView noticeView(InsuranceExpiryNotice n) {
        return new ExpiryNoticeView(n.id, n.policyType, n.policyId, n.expiryDate, n.daysBeforeExpiry, n.message, n.readAt, n.createdAt);
    }
    private ActivityView activityView(InsuranceActivity a) {
        return new ActivityView(a.id, a.activityType, a.entityType, a.entityId, a.summary, a.createdAt);
    }
    private <T> ProtectionPage<T> page(Page<T> page) {
        return new ProtectionPage<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.hasNext());
    }
    private ProtectionException missing(String message) { return new ProtectionException(404, message); }
    private ProtectionException conflict(String message) { return new ProtectionException(409, message); }
    private ProtectionException bad(String message) { return new ProtectionException(400, message); }
    private AccessDeniedException denied() { return new AccessDeniedException("You do not have permission to access this information."); }
}
