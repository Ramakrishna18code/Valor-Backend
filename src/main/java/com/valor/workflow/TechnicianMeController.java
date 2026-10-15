package com.valor.workflow;

import com.valor.auth.*;
import com.valor.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.*;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static com.valor.workflow.TechnicianDtos.*;

@RestController
@RequestMapping("/api/v1/technician/me")
@Transactional
class TechnicianMeController {
    private final AssetIdentityAccess identities;
    private final WorkflowIdentityAccess profiles;
    private final RequestRepository requests;
    private final ServiceVisitRepository visits;
    private final TechnicianSavedLocationRepo savedLocations;
    private final Clock clock;

    TechnicianMeController(AssetIdentityAccess identities, WorkflowIdentityAccess profiles, RequestRepository requests,
            ServiceVisitRepository visits, TechnicianSavedLocationRepo savedLocations, Clock clock) {
        this.identities = identities; this.profiles = profiles; this.requests = requests; this.visits = visits; this.savedLocations = savedLocations; this.clock = clock;
    }

    @Operation(operationId = "technicianProfile")
    @GetMapping("/profile")
    ApiResponse<Profile> profile() { return ApiResponse.success("Technician profile", profile(current()), 200); }

    @Operation(operationId = "technicianProfileUpdate")
    @PutMapping("/profile")
    ApiResponse<Profile> updateProfile(@Valid @RequestBody ProfileUpdate input) {
        TechnicianProfile profile = current();
        if (input.availabilityStatus() != null) profile.setAvailabilityStatus(input.availabilityStatus());
        applyEditable(profile, input);
        profile.setLastActiveAt(LocalDateTime.now(clock));
        return ApiResponse.success("Technician profile updated", profile(profile), 200);
    }

    @GetMapping("/locations")
    ApiResponse<List<SavedLocation>> locations() { return ApiResponse.success("Technician locations", savedLocations.findByTechnicianIdOrderByDefaultLocationDescUpdatedAtDesc(current().getId()).stream().map(this::location).toList(), 200); }

    @PostMapping("/locations")
    ApiResponse<SavedLocation> addLocation(@Valid @RequestBody SavedLocationInput input) {
        TechnicianProfile technician = current();
        if (input.defaultLocation() || savedLocations.countByTechnicianId(technician.getId()) == 0) clearDefault(technician.getId());
        TechnicianSavedLocation value = new TechnicianSavedLocation(); value.setTechnician(technician); apply(value, input);
        return ApiResponse.success("Technician location saved", location(savedLocations.save(value)), 201);
    }

    @PutMapping("/locations/{id}")
    ApiResponse<SavedLocation> updateLocation(@PathVariable Long id, @Valid @RequestBody SavedLocationInput input) {
        TechnicianProfile technician = current();
        TechnicianSavedLocation value = savedLocations.findById(id).filter(item -> item.getTechnician().getId().equals(technician.getId())).orElseThrow(() -> new IllegalArgumentException("Location not found"));
        if (input.defaultLocation()) clearDefault(technician.getId()); apply(value, input);
        return ApiResponse.success("Technician location updated", location(savedLocations.save(value)), 200);
    }

    @DeleteMapping("/locations/{id}")
    ApiResponse<Void> deleteLocation(@PathVariable Long id) {
        TechnicianProfile technician = current();
        TechnicianSavedLocation value = savedLocations.findById(id).filter(item -> item.getTechnician().getId().equals(technician.getId())).orElseThrow(() -> new IllegalArgumentException("Location not found"));
        savedLocations.delete(value); return ApiResponse.success("Technician location deleted", null, 200);
    }

    private void clearDefault(Long id) { savedLocations.findByTechnicianIdOrderByDefaultLocationDescUpdatedAtDesc(id).forEach(item -> item.setDefaultLocation(false)); }
    private void apply(TechnicianSavedLocation value, SavedLocationInput input) { value.setTitle(input.title()); value.setPincode(input.pincode()); value.setState(input.state()); value.setDistrict(input.district()); value.setAddress(input.address()); value.setLatitude(input.latitude()); value.setLongitude(input.longitude()); value.setDefaultLocation(input.defaultLocation()); }
    private SavedLocation location(TechnicianSavedLocation value) { return new SavedLocation(value.getId(), value.getTitle(), value.getPincode(), value.getState(), value.getDistrict(), value.getAddress(), value.getLatitude(), value.getLongitude(), value.isDefaultLocation()); }

    @Operation(operationId = "technicianDashboard")
    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    ApiResponse<Dashboard> dashboard() {
        TechnicianProfile technician = current();
        LocalDate today = LocalDate.now(clock);
        LocalDate quarterStart = today.with(today.getMonth().firstMonthOfQuarter()).withDayOfMonth(1);
        LocalDate quarterEnd = quarterStart.plusMonths(3);
        long inProgress = 0;
        for (RequestStatus status : List.of(RequestStatus.ACCEPTED, RequestStatus.ON_THE_WAY, RequestStatus.REACHED_SITE,
                RequestStatus.DIAGNOSIS, RequestStatus.REPAIR_IN_PROGRESS, RequestStatus.WAITING_FOR_PARTS, RequestStatus.TESTING)) {
            inProgress += requests.assignedStatusCount(technician.getId(), status);
        }
        Dashboard summary = new Dashboard(profile(technician), requests.activeJobCount(technician.getId()),
                requests.assignedStatusCount(technician.getId(), RequestStatus.ASSIGNED), inProgress,
                requests.assignedStatusCount(technician.getId(), RequestStatus.COMPLETED),
                requests.completedBetween(technician.getId(), quarterStart.atStartOfDay(), quarterEnd.atStartOfDay()),
                visits.technicianVisits(technician.getId(), today, today, null, PageRequest.of(0, 1)).getTotalElements(),
                requests.activeEmergencyCount(technician.getId()));
        return ApiResponse.success("Technician dashboard", summary, 200);
    }

    private TechnicianProfile current() {
        User actor = identities.actor();
        if (actor.getRole() != Role.TECHNICIAN) throw new AccessDeniedException("Access denied");
        return profiles.technician(actor);
    }
    private Profile profile(TechnicianProfile p) {
        User user = p.getUser();
        return new Profile(user.getId(), p.getId(), user.getEmail(), user.getPhone(), p.getEmployeeId(),
                p.getAssignedArea(), p.getSpecialization(), p.getAvailabilityStatus(), p.isActive(), p.getLastActiveAt(),
                p.getProfilePhotoUrl(), p.getDateOfBirth(), p.getGender(), p.getAddress(), p.getLatitude(), p.getLongitude(),
                p.getEmergencyContactName(), p.getEmergencyContactPhone());
    }
    static void applyEditable(TechnicianProfile profile, ProfileUpdate input) {
        profile.setProfilePhotoUrl(input.profilePhotoUrl());
        profile.setDateOfBirth(input.dateOfBirth());
        profile.setGender(input.gender());
        profile.setAddress(input.address());
        profile.setEmergencyContactName(input.emergencyContactName());
        profile.setEmergencyContactPhone(input.emergencyContactPhone());
        profile.setLatitude(input.latitude());
        profile.setLongitude(input.longitude());
    }
}
