package com.valor.tracking;

import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static com.valor.tracking.TrackingDtos.*;

@RestController
@RequestMapping("/api/v1")
class TrackingController {
    private final TrackingService service;
    TrackingController(TrackingService service) { this.service = service; }

    @PostMapping("/technician/me/jobs/{id}/location")
    ApiResponse<LocationView> update(@PathVariable Long id, @Valid @RequestBody LocationUpdate input) {
        return ApiResponse.success("Location updated", service.update(id, input), 200);
    }

    @GetMapping("/customers/me/service-requests/{id}/technician-location")
    ApiResponse<LocationView> customerLocation(@PathVariable Long id) {
        return ApiResponse.success("Technician location", service.customerLocation(id), 200);
    }

    @GetMapping("/technician/me/jobs/{id}/location")
    ApiResponse<LocationView> technicianLocation(@PathVariable Long id) {
        return ApiResponse.success("Job location", service.technicianLocation(id), 200);
    }
}
