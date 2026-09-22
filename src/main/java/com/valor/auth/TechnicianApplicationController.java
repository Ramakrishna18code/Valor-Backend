package com.valor.auth;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/technician-applications")
class TechnicianApplicationController {
    private final TechnicianApplicationService service;
    TechnicianApplicationController(TechnicianApplicationService service) { this.service = service; }
    @PostMapping ApiResponse<TechnicianApplicationService.Created> create(@Valid @RequestBody Create input) { return ApiResponse.success("Technician application created", service.create(input), 201); }
    @PostMapping("/{id}/otp/send") ApiResponse<TechnicianApplicationService.OtpView> sendOtp(@PathVariable Long id, @RequestHeader("X-Application-Token") String token) { return ApiResponse.success("OTP sent", service.sendOtp(id, token), 200); }
    @PostMapping("/{id}/otp/verify") ApiResponse<TechnicianApplicationService.ApplicationView> verifyOtp(@PathVariable Long id, @RequestHeader("X-Application-Token") String token, @Valid @RequestBody Otp input) { return ApiResponse.success("Mobile verified", service.verifyOtp(id, token, input.otp()), 200); }
    @PutMapping("/{id}") ApiResponse<TechnicianApplicationService.ApplicationView> update(@PathVariable Long id, @RequestHeader("X-Application-Token") String token, @RequestBody Update input) { return ApiResponse.success("Application updated", service.update(id, token, input), 200); }
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) ApiResponse<TechnicianApplicationService.DocumentView> document(@PathVariable Long id, @RequestHeader("X-Application-Token") String token, @RequestParam String documentType, @RequestPart("file") MultipartFile file) { return ApiResponse.success("Document uploaded", service.uploadDocument(id, token, documentType, file), 201); }
    @GetMapping("/{id}") ApiResponse<TechnicianApplicationService.ApplicationView> read(@PathVariable Long id, @RequestHeader("X-Application-Token") String token) { return ApiResponse.success("Application", service.read(id, token), 200); }
    @PostMapping("/{id}/submit") ApiResponse<TechnicianApplicationService.ApplicationView> submit(@PathVariable Long id, @RequestHeader("X-Application-Token") String token) { return ApiResponse.success("Application submitted", service.submit(id, token), 200); }
    record Create(@NotBlank @Size(max=160) String fullName,@NotBlank @Email String email,@NotBlank @Size(max=20) String phone,@NotBlank @Size(max=72) String password) implements StrictInput {}
    record Otp(@NotBlank @Size(min=4,max=4) String otp) implements StrictInput {}
    record Update(String experience,String specialization,String liftBrands,String certifications,String highestQualification,String handsOnExperience,String preferredLocations,Boolean willingToWorkAtHeights,String travelAvailability,String additionalNotes,String aadhaarNumber,String drivingLicenseNumber) implements StrictInput {}
    interface StrictInput { @JsonAnySetter default void rejectUnknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported field"); } }
}
