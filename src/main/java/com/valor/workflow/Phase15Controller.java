package com.valor.workflow;

import com.valor.auth.*;
import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static com.valor.workflow.ChecklistDtos.*;
import static com.valor.workflow.TechnicianDtos.*;

@RestController
@RequestMapping("/api/v1")
class Phase15Controller {
    private final ChecklistService checklists; private final CompletionOtpService otps; private final ArrivalOtpService arrivalOtps; private final ServiceCodeService serviceCodes;
    private final TechnicianPrivateAttachmentService privateFiles; private final WorkflowIdentityAccess profiles; private final AssetIdentityAccess identities;
    Phase15Controller(ChecklistService checklists, CompletionOtpService otps, ArrivalOtpService arrivalOtps,
            TechnicianPrivateAttachmentService privateFiles, WorkflowIdentityAccess profiles, AssetIdentityAccess identities, ServiceCodeService serviceCodes) {
        this.checklists = checklists; this.otps = otps; this.arrivalOtps = arrivalOtps; this.privateFiles = privateFiles; this.profiles = profiles; this.identities = identities;
        this.serviceCodes = serviceCodes;
    }

    @GetMapping("/admin/checklist-templates") ApiResponse<List<TemplateView>> templates() { return ok(checklists.templates()); }
    @PostMapping("/admin/checklist-templates") ApiResponse<TemplateView> createTemplate(@Valid @RequestBody TemplateWrite input) { return ok(checklists.createTemplate(input)); }
    @PutMapping("/admin/checklist-templates/{id}") ApiResponse<TemplateView> updateTemplate(@PathVariable Long id, @Valid @RequestBody TemplateWrite input) { return ok(checklists.updateTemplate(id, input)); }
    @PostMapping("/admin/checklist-templates/{id}/items") ApiResponse<ItemView> addItem(@PathVariable Long id, @Valid @RequestBody ItemWrite input) { return ok(checklists.addItem(id, input)); }
    @PutMapping("/admin/checklist-templates/{id}/items/{itemId}") ApiResponse<ItemView> updateItem(@PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody ItemWrite input) { return ok(checklists.updateItem(id, itemId, input)); }
    @DeleteMapping("/admin/checklist-templates/{id}/items/{itemId}") ApiResponse<Void> deleteItem(@PathVariable Long id, @PathVariable Long itemId) { checklists.deleteItem(id, itemId); return ok(null); }

    @GetMapping("/technician/me/jobs/{id}/checklist") ApiResponse<JobChecklistView> jobChecklist(@PathVariable Long id) { return ok(checklists.technicianChecklist(id)); }
    @PutMapping("/technician/me/jobs/{id}/checklist/responses") ApiResponse<JobChecklistView> saveChecklist(@PathVariable Long id, @Valid @RequestBody ResponseBatch input) { return ok(checklists.saveResponses(id, input)); }

    @PostMapping("/technician/me/jobs/{id}/completion-otp/request") ApiResponse<ServiceCodeService.CodeView> requestOtp(@PathVariable Long id) { return ok(serviceCodes.stateForRequest(id, ServiceCodeAction.COMPLETE, false)); }
    @PostMapping("/technician/me/jobs/{id}/completion-otp/verify") ApiResponse<ServiceCodeService.CodeView> verifyOtp(@PathVariable Long id, @Valid @RequestBody CompletionOtpVerifyInput input) { return ok(serviceCodes.verify(id, ServiceCodeAction.COMPLETE, input.otp())); }
    @GetMapping("/service-requests/{id}/completion-otp") ApiResponse<ServiceCodeService.CodeView> otpState(@PathVariable Long id) { return ok(serviceCodes.stateForRequest(id, ServiceCodeAction.COMPLETE, true)); }
    @PostMapping("/technician/me/jobs/{id}/arrival-otp/request") ApiResponse<ServiceCodeService.CodeView> requestArrivalOtp(@PathVariable Long id) { return ok(serviceCodes.stateForRequest(id, ServiceCodeAction.START, false)); }
    @PostMapping("/technician/me/jobs/{id}/arrival-otp/verify") ApiResponse<ServiceCodeService.CodeView> verifyArrivalOtp(@PathVariable Long id, @Valid @RequestBody CompletionOtpVerifyInput input) { return ok(serviceCodes.verify(id, ServiceCodeAction.START, input.otp())); }
    @GetMapping("/service-requests/{id}/arrival-otp") ApiResponse<ServiceCodeService.CodeView> arrivalOtpState(@PathVariable Long id) { return ok(serviceCodes.stateForRequest(id, ServiceCodeAction.START, true)); }

    @GetMapping("/technician/me/private-attachments") ApiResponse<List<TechnicianPrivateAttachmentService.View>> myPrivateFiles() { return ok(privateFiles.mine()); }
    @PostMapping(value="/technician/me/private-attachments", consumes=MediaType.MULTIPART_FORM_DATA_VALUE) ApiResponse<TechnicianPrivateAttachmentService.View> uploadMine(@RequestPart("file") MultipartFile file) { return ok(privateFiles.uploadMine(file)); }
    @GetMapping("/technician/me/private-attachments/{id}") ResponseEntity<Resource> downloadMine(@PathVariable Long id) { var tech = profiles.technician(identities.actor()); return download(privateFiles.download(tech.getId(), id)); }
    @DeleteMapping("/technician/me/private-attachments/{id}") ApiResponse<Void> deleteMine(@PathVariable Long id) { var tech = profiles.technician(identities.actor()); privateFiles.delete(tech.getId(), id); return ok(null); }

    @GetMapping("/admin/technicians/{id}/private-attachments") ApiResponse<List<TechnicianPrivateAttachmentService.View>> technicianFiles(@PathVariable Long id) { return ok(privateFiles.list(id)); }
    @PostMapping(value="/admin/technicians/{id}/private-attachments", consumes=MediaType.MULTIPART_FORM_DATA_VALUE) ApiResponse<TechnicianPrivateAttachmentService.View> uploadTechnicianFile(@PathVariable Long id, @RequestPart("file") MultipartFile file) { return ok(privateFiles.upload(id, file)); }
    @GetMapping("/admin/technicians/{id}/private-attachments/{attachmentId}") ResponseEntity<Resource> downloadTechnicianFile(@PathVariable Long id, @PathVariable Long attachmentId) { return download(privateFiles.download(id, attachmentId)); }
    @DeleteMapping("/admin/technicians/{id}/private-attachments/{attachmentId}") ApiResponse<Void> deleteTechnicianFile(@PathVariable Long id, @PathVariable Long attachmentId) { privateFiles.delete(id, attachmentId); return ok(null); }

    @GetMapping("/admin/technicians/{id}") ApiResponse<Profile> technicianProfile(@PathVariable Long id) { return ok(profile(profiles.activeTechnician(id))); }
    @PutMapping("/admin/technicians/{id}") ApiResponse<Profile> updateTechnicianProfile(@PathVariable Long id, @Valid @RequestBody AdminTechnicianUpdate input) {
        TechnicianProfile technician = profiles.activeTechnician(id);
        if (input.availabilityStatus() != null) technician.setAvailabilityStatus(input.availabilityStatus());
        if (input.assignedArea() != null) technician.setAssignedArea(input.assignedArea());
        if (input.specialization() != null) technician.setSpecialization(input.specialization());
        TechnicianMeController.applyEditable(technician, new ProfileUpdate(input.availabilityStatus(), input.profilePhotoUrl(), input.dateOfBirth(),
                input.gender(), input.address(), input.emergencyContactName(), input.emergencyContactPhone(), input.latitude(), input.longitude()));
        return ok(profile(technician));
    }

    private static <T> ApiResponse<T> ok(T data) { return ApiResponse.success("Success", data, 200); }
    private static Profile profile(TechnicianProfile p) {
        User user = p.getUser();
        return new Profile(user.getId(), p.getId(), user.getEmail(), user.getPhone(), p.getEmployeeId(),
                p.getAssignedArea(), p.getSpecialization(), p.getAvailabilityStatus(), p.isActive(), p.getLastActiveAt(),
                p.getProfilePhotoUrl(), p.getDateOfBirth(), p.getGender(), p.getAddress(), p.getLatitude(), p.getLongitude(), p.getEmergencyContactName(), p.getEmergencyContactPhone());
    }
    private static ResponseEntity<Resource> download(TechnicianPrivateAttachmentService.Download file) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString()).body(file.resource());
    }
    record CompletionOtpVerifyInput(Long otpId, @jakarta.validation.constraints.NotBlank String otp) {}
    record AdminTechnicianUpdate(String availabilityStatus, String assignedArea, String specialization,
            String profilePhotoUrl, java.time.LocalDate dateOfBirth, String gender, String address,
            String emergencyContactName, String emergencyContactPhone,
            @jakarta.validation.constraints.DecimalMin("-90.0") @jakarta.validation.constraints.DecimalMax("90.0") java.math.BigDecimal latitude,
            @jakarta.validation.constraints.DecimalMin("-180.0") @jakarta.validation.constraints.DecimalMax("180.0") java.math.BigDecimal longitude) {}
}
