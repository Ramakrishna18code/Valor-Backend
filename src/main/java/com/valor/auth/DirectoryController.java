package com.valor.auth;

import com.valor.response.ApiResponse;
import com.valor.workflow.WorkflowDtos.PageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
class DirectoryController {
    private final DirectoryService directories;
    DirectoryController(DirectoryService directories) { this.directories = directories; }

    @Schema(name="TechnicianDirectoryEntry")
    public record Technician(Long userId, String email, boolean active, Long technicianProfileId,
            String employeeId, String assignedArea, String specialization,
            @Schema(allowableValues={"AVAILABLE","BUSY","OFF_DUTY","ON_LEAVE"}) String availabilityStatus) {}
    @Schema(name="CustomerDirectoryEntry")
    public record Customer(Long userId, Long customerProfileId, String fullName, String email,
            String phone, boolean active, @Schema(allowableValues={"ACTIVE","INACTIVE","SUSPENDED"}) String status) {}

    @Operation(operationId="getAdminTechnicians", summary="List technician choices for administrators")
    @GetMapping("/technicians")
    ApiResponse<PageView<Technician>> technicians(
            @Parameter(description="Zero-based page", schema=@Schema(minimum="0",defaultValue="0")) @RequestParam(defaultValue="0") int page,
            @Parameter(description="Page size, 1-100", schema=@Schema(minimum="1",maximum="100",defaultValue="20")) @RequestParam(defaultValue="20") int size,
            @Parameter(description="Case-insensitive literal substring of email, employee ID, assigned area or specialization; trimmed, max 254 characters") @RequestParam(required=false) String q,
            @Parameter(description="Filter combined user and profile active flags; omitted includes both. Does not guarantee assignment eligibility.") @RequestParam(required=false) Boolean active) {
        return ApiResponse.success("Technicians", directories.technicians(page,size,q,active),200);
    }

    @Operation(operationId="getAdminCustomers", summary="List customer choices for administrators")
    @GetMapping("/customers")
    ApiResponse<PageView<Customer>> customers(
            @Parameter(description="Zero-based page", schema=@Schema(minimum="0",defaultValue="0")) @RequestParam(defaultValue="0") int page,
            @Parameter(description="Page size, 1-100", schema=@Schema(minimum="1",maximum="100",defaultValue="20")) @RequestParam(defaultValue="20") int size,
            @Parameter(description="Case-insensitive literal substring of full name, email or canonical phone; trimmed, max 254 characters") @RequestParam(required=false) String q,
            @Parameter(description="Filter combined user and profile active flags; status is separate and creation eligibility is revalidated by workflow.") @RequestParam(required=false) Boolean active) {
        return ApiResponse.success("Customers", directories.customers(page,size,q,active),200);
    }
}
