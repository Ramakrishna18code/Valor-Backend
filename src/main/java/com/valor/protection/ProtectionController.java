package com.valor.protection;

import com.valor.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
class ProtectionController {
    private final ProtectionService protection;
    ProtectionController(ProtectionService protection) { this.protection = protection; }

    @Operation(operationId = "protectionProviders")
    @GetMapping("/protection/providers")
    ApiResponse<ProtectionPage<ProviderView>> providers(@RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(protection.providers(search, false, page, size));
    }

    @Operation(operationId = "protectionProvider")
    @GetMapping("/protection/providers/{id}")
    ApiResponse<ProviderView> provider(@PathVariable @Positive Long id) { return ok(protection.provider(id, false)); }

    @Operation(operationId = "createProtectionProvider")
    @PostMapping("/admin/protection/providers")
    ApiResponse<ProviderView> createProvider(@Valid @RequestBody ProviderWrite input) { return ok(protection.createProvider(input)); }

    @Operation(operationId = "updateProtectionProvider")
    @PutMapping("/admin/protection/providers/{id}")
    ApiResponse<ProviderView> updateProvider(@PathVariable @Positive Long id, @Valid @RequestBody ProviderWrite input) {
        return ok(protection.updateProvider(id, input));
    }

    @Operation(operationId = "protectionProducts")
    @GetMapping("/protection/products")
    ApiResponse<ProtectionPage<ProductView>> products(@RequestParam(required = false) InsuredAssetType insuredAssetType,
            @RequestParam(required = false) @Positive Long providerId, @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(protection.products(insuredAssetType, providerId, search, false, page, size));
    }

    @Operation(operationId = "protectionProduct")
    @GetMapping("/protection/products/{id}")
    ApiResponse<ProductView> product(@PathVariable @Positive Long id) { return ok(protection.product(id, false)); }

    @Operation(operationId = "createProtectionProduct")
    @PostMapping("/admin/protection/products")
    ApiResponse<ProductView> createProduct(@Valid @RequestBody ProductWrite input) { return ok(protection.createProduct(input)); }

    @Operation(operationId = "updateProtectionProduct")
    @PutMapping("/admin/protection/products/{id}")
    ApiResponse<ProductView> updateProduct(@PathVariable @Positive Long id, @Valid @RequestBody ProductWrite input) {
        return ok(protection.updateProduct(id, input));
    }

    @Operation(operationId = "technicianProtectionPolicies")
    @GetMapping("/technicians/me/protection/policies")
    ApiResponse<ProtectionPage<TechnicianPolicyView>> technicianPolicies(
            @RequestParam(required = false) PolicyStatus status, @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(protection.technicianPolicies(status, search, false, page, size));
    }

    @Operation(operationId = "declareTechnicianProtectionPolicy")
    @PostMapping("/technicians/me/protection/policies")
    ApiResponse<TechnicianPolicyView> declareTechnicianPolicy(@Valid @RequestBody TechnicianPolicyWrite input) {
        return ok(protection.declareTechnicianPolicy(input));
    }

    @Operation(operationId = "technicianProtectionPolicy")
    @GetMapping("/protection/technician-policies/{id}")
    ApiResponse<TechnicianPolicyView> technicianPolicy(@PathVariable @Positive Long id) { return ok(protection.technicianPolicy(id)); }

    @Operation(operationId = "adminTechnicianProtectionPolicies")
    @GetMapping("/admin/protection/technician-policies")
    ApiResponse<ProtectionPage<TechnicianPolicyView>> adminTechnicianPolicies(
            @RequestParam(required = false) PolicyStatus status, @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(protection.technicianPolicies(status, search, true, page, size));
    }

    @Operation(operationId = "recordTechnicianInsurerPolicyStatus")
    @PatchMapping("/admin/protection/technician-policies/{id}/insurer-confirmation")
    ApiResponse<TechnicianPolicyView> confirmTechnicianPolicy(@PathVariable @Positive Long id, @Valid @RequestBody PolicyReviewWrite input) {
        return ok(protection.confirmTechnicianPolicy(id, input));
    }

    @Operation(operationId = "technicianProtectionPolicyDocuments")
    @GetMapping("/protection/technician-policies/{id}/documents")
    ApiResponse<List<DocumentView>> technicianPolicyDocuments(@PathVariable @Positive Long id) {
        return ok(protection.technicianPolicyDocuments(id));
    }

    @Operation(operationId = "addTechnicianProtectionPolicyDocumentMetadata")
    @PostMapping("/protection/technician-policies/{id}/documents")
    ApiResponse<DocumentView> addTechnicianPolicyDocument(@PathVariable @Positive Long id, @Valid @RequestBody DocumentMetadataWrite input) {
        return ok(protection.addTechnicianPolicyDocument(id, input));
    }

    @Operation(operationId = "customerLiftProtectionPolicies")
    @GetMapping("/customers/me/protection/lift-policies")
    ApiResponse<ProtectionPage<LiftPolicyView>> liftPolicies(
            @RequestParam(required = false) PolicyStatus status, @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(protection.liftPolicies(status, search, false, page, size));
    }

    @Operation(operationId = "declareLiftProtectionPolicy")
    @PostMapping("/customers/me/protection/lift-policies")
    ApiResponse<LiftPolicyView> declareLiftPolicy(@Valid @RequestBody LiftPolicyWrite input) { return ok(protection.declareLiftPolicy(input)); }

    @Operation(operationId = "liftProtectionPolicy")
    @GetMapping("/protection/lift-policies/{id}")
    ApiResponse<LiftPolicyView> liftPolicy(@PathVariable @Positive Long id) { return ok(protection.liftPolicy(id)); }

    @Operation(operationId = "adminLiftProtectionPolicies")
    @GetMapping("/admin/protection/lift-policies")
    ApiResponse<ProtectionPage<LiftPolicyView>> adminLiftPolicies(
            @RequestParam(required = false) PolicyStatus status, @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(protection.liftPolicies(status, search, true, page, size));
    }

    @Operation(operationId = "recordLiftInsurerPolicyStatus")
    @PatchMapping("/admin/protection/lift-policies/{id}/insurer-confirmation")
    ApiResponse<LiftPolicyView> confirmLiftPolicy(@PathVariable @Positive Long id, @Valid @RequestBody PolicyReviewWrite input) {
        return ok(protection.confirmLiftPolicy(id, input));
    }

    @Operation(operationId = "liftProtectionPolicyDocuments")
    @GetMapping("/protection/lift-policies/{id}/documents")
    ApiResponse<List<DocumentView>> liftPolicyDocuments(@PathVariable @Positive Long id) {
        return ok(protection.liftPolicyDocuments(id));
    }

    @Operation(operationId = "addLiftProtectionPolicyDocumentMetadata")
    @PostMapping("/protection/lift-policies/{id}/documents")
    ApiResponse<DocumentView> addLiftPolicyDocument(@PathVariable @Positive Long id, @Valid @RequestBody DocumentMetadataWrite input) {
        return ok(protection.addLiftPolicyDocument(id, input));
    }

    @Operation(operationId = "customerLiftProtectionApplications")
    @GetMapping("/customers/me/protection/applications")
    ApiResponse<ProtectionPage<LiftApplicationView>> applications(
            @RequestParam(required = false) ApplicationStatus status, @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(protection.applications(status, search, false, page, size));
    }

    @Operation(operationId = "createLiftProtectionApplication")
    @PostMapping("/customers/me/protection/applications")
    ApiResponse<LiftApplicationView> createApplication(@Valid @RequestBody LiftApplicationWrite input) {
        return ok(protection.createApplication(input));
    }

    @Operation(operationId = "getLiftProtectionApplication")
    @GetMapping("/protection/applications/{id}")
    ApiResponse<LiftApplicationView> application(@PathVariable @Positive Long id) { return ok(protection.application(id)); }

    @Operation(operationId = "adminLiftProtectionApplications")
    @GetMapping("/admin/protection/applications")
    ApiResponse<ProtectionPage<LiftApplicationView>> adminApplications(
            @RequestParam(required = false) ApplicationStatus status, @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(protection.applications(status, search, true, page, size));
    }

    @Operation(operationId = "recordManualLiftInsurerQuote")
    @PostMapping("/admin/protection/applications/{id}/manual-quote")
    ApiResponse<LiftApplicationView> recordQuote(@PathVariable @Positive Long id, @Valid @RequestBody QuoteWrite input) {
        return ok(protection.recordQuote(id, input));
    }

    @Operation(operationId = "decideLiftInsuranceQuote")
    @PostMapping("/customers/me/protection/applications/{id}/decision")
    ApiResponse<LiftApplicationView> decideApplication(@PathVariable @Positive Long id, @Valid @RequestBody ApplicationDecisionWrite input) {
        return ok(protection.decideApplication(id, input));
    }

    @Operation(operationId = "cancelLiftProtectionApplication")
    @PostMapping("/customers/me/protection/applications/{id}/cancel")
    ApiResponse<LiftApplicationView> cancelApplication(@PathVariable @Positive Long id) { return ok(protection.cancelApplication(id)); }

    @Operation(operationId = "protectionClaims")
    @GetMapping("/protection/claims")
    ApiResponse<ProtectionPage<ClaimView>> claims(@RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) @Size(max = 100) String search, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(protection.claims(status, search, false, page, size));
    }

    @Operation(operationId = "createProtectionClaim")
    @PostMapping("/protection/claims")
    ApiResponse<ClaimView> createClaim(@Valid @RequestBody ClaimWrite input) { return ok(protection.createClaim(input)); }

    @Operation(operationId = "getProtectionClaim")
    @GetMapping("/protection/claims/{id}")
    ApiResponse<ClaimView> claim(@PathVariable @Positive Long id) { return ok(protection.claim(id)); }

    @Operation(operationId = "withdrawProtectionClaim")
    @PostMapping("/protection/claims/{id}/withdraw")
    ApiResponse<ClaimView> withdrawClaim(@PathVariable @Positive Long id) { return ok(protection.withdrawClaim(id)); }

    @Operation(operationId = "adminProtectionClaims")
    @GetMapping("/admin/protection/claims")
    ApiResponse<ProtectionPage<ClaimView>> adminClaims(@RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) @Size(max = 100) String search, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(protection.claims(status, search, true, page, size));
    }

    @Operation(operationId = "recordInsurerReportedClaimStatus")
    @PatchMapping("/admin/protection/claims/{id}/insurer-status")
    ApiResponse<ClaimView> updateClaimFromInsurer(@PathVariable @Positive Long id, @Valid @RequestBody InsurerClaimUpdate input) {
        return ok(protection.updateClaimFromInsurer(id, input));
    }

    @Operation(operationId = "protectionClaimDocuments")
    @GetMapping("/protection/claims/{id}/documents")
    ApiResponse<List<DocumentView>> claimDocuments(@PathVariable @Positive Long id) { return ok(protection.claimDocuments(id)); }

    @Operation(operationId = "addProtectionClaimDocumentMetadata")
    @PostMapping("/protection/claims/{id}/documents")
    ApiResponse<DocumentView> addClaimDocument(@PathVariable @Positive Long id, @Valid @RequestBody DocumentMetadataWrite input) {
        return ok(protection.addClaimDocument(id, input));
    }

    @Operation(operationId = "protectionApplicationDocuments")
    @GetMapping("/protection/applications/{id}/documents")
    ApiResponse<List<DocumentView>> applicationDocuments(@PathVariable @Positive Long id) { return ok(protection.applicationDocuments(id)); }

    @Operation(operationId = "addProtectionApplicationDocumentMetadata")
    @PostMapping("/customers/me/protection/applications/{id}/documents")
    ApiResponse<DocumentView> addApplicationDocument(@PathVariable @Positive Long id, @Valid @RequestBody DocumentMetadataWrite input) {
        return ok(protection.addApplicationDocument(id, input));
    }

    @Operation(operationId = "protectionRenewals")
    @GetMapping("/protection/renewals")
    ApiResponse<ProtectionPage<RenewalView>> renewals(@RequestParam(required = false) RenewalStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(protection.renewals(status, false, page, size));
    }

    @Operation(operationId = "requestProtectionRenewal")
    @PostMapping("/protection/renewals")
    ApiResponse<RenewalView> requestRenewal(@Valid @RequestBody RenewalWrite input) { return ok(protection.requestRenewal(input)); }

    @Operation(operationId = "cancelProtectionRenewal")
    @PostMapping("/protection/renewals/{id}/cancel")
    ApiResponse<RenewalView> cancelRenewal(@PathVariable @Positive Long id) { return ok(protection.cancelRenewal(id)); }

    @Operation(operationId = "adminProtectionRenewals")
    @GetMapping("/admin/protection/renewals")
    ApiResponse<ProtectionPage<RenewalView>> adminRenewals(@RequestParam(required = false) RenewalStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(protection.renewals(status, true, page, size));
    }

    @Operation(operationId = "recordInsurerReportedRenewalStatus")
    @PatchMapping("/admin/protection/renewals/{id}/insurer-status")
    ApiResponse<RenewalView> updateRenewalFromInsurer(@PathVariable @Positive Long id, @Valid @RequestBody InsurerRenewalUpdate input) {
        return ok(protection.updateRenewalFromInsurer(id, input));
    }

    @Operation(operationId = "protectionExpiryNotices")
    @GetMapping("/protection/expiry-notices")
    ApiResponse<ProtectionPage<ExpiryNoticeView>> expiryNotices(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return ok(protection.expiryNotices(page, size)); }

    @Operation(operationId = "readProtectionExpiryNotice")
    @PatchMapping("/protection/expiry-notices/{id}/read")
    ApiResponse<ExpiryNoticeView> readNotice(@PathVariable @Positive Long id) { return ok(protection.readNotice(id)); }

    @Operation(operationId = "protectionDashboard")
    @GetMapping("/protection/dashboard")
    ApiResponse<ProtectionDashboard> dashboard() { return ok(protection.dashboard()); }

    private <T> ApiResponse<T> ok(T data) { return ApiResponse.success("Success", data, 200); }
}
