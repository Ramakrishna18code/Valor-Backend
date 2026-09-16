package com.valor.tracking;

import com.valor.auth.*;
import com.valor.workflow.*;
import java.time.*;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.valor.tracking.TrackingDtos.*;

@Service
@Transactional
class TrackingService {
    private static final Set<RequestStatus> TRACKABLE = Set.of(RequestStatus.ON_THE_WAY, RequestStatus.REACHED_SITE,
            RequestStatus.DIAGNOSIS, RequestStatus.REPAIR_IN_PROGRESS, RequestStatus.WAITING_FOR_PARTS, RequestStatus.TESTING);
    private final AssetIdentityAccess identities;
    private final WorkflowIdentityAccess profiles;
    private final TrackingRequestRepository requests;
    private final TrackingAssignmentRepository assignments;
    private final TechnicianLatestLocationRepository locations;
    private final Clock clock;

    TrackingService(AssetIdentityAccess identities, WorkflowIdentityAccess profiles, TrackingRequestRepository requests,
            TrackingAssignmentRepository assignments, TechnicianLatestLocationRepository locations, Clock clock) {
        this.identities = identities; this.profiles = profiles; this.requests = requests; this.assignments = assignments;
        this.locations = locations; this.clock = clock;
    }

    LocationView update(Long requestId, LocationUpdate input) {
        User actor = identities.actor();
        if (actor.getRole() != Role.TECHNICIAN) throw denied();
        TechnicianProfile technician = profiles.technician(actor);
        ServiceRequest request = requests.findById(requestId).orElseThrow(() -> missing("Service request"));
        TechnicianAssignment assignment = assignments.active(requestId).orElseThrow(() -> denied());
        if (!assignment.getTechnician().getId().equals(technician.getId())) throw denied();
        if (!TRACKABLE.contains(request.getStatus())) throw new TrackingException(409, "Tracking is not active for this job");
        TechnicianLatestLocation row = locations.findByServiceRequestId(requestId).orElseGet(TechnicianLatestLocation::new);
        row.setTechnician(technician); row.setServiceRequest(request); row.setLatitude(input.latitude()); row.setLongitude(input.longitude());
        row.setRecordedAt(input.timestamp() == null ? LocalDateTime.now(clock) : input.timestamp());
        return view(locations.saveAndFlush(row), request.getStatus());
    }

    @Transactional(readOnly = true)
    LocationView customerLocation(Long requestId) {
        User actor = identities.actor();
        if (actor.getRole() != Role.CUSTOMER) throw denied();
        ServiceRequest request = requests.withCustomer(requestId).orElseThrow(() -> missing("Service request"));
        if (!request.getCustomer().getUser().getId().equals(actor.getId())) throw denied();
        if (!TRACKABLE.contains(request.getStatus())) throw new TrackingException(409, "Tracking is not active for this request");
        TechnicianLatestLocation row = locations.findByServiceRequestId(requestId).orElseThrow(() -> missing("Technician location"));
        return view(row, request.getStatus());
    }

    private LocationView view(TechnicianLatestLocation row, RequestStatus status) {
        boolean stale = row.getRecordedAt().isBefore(LocalDateTime.now(clock).minusMinutes(10));
        return new LocationView(row.getLatitude(), row.getLongitude(), row.getRecordedAt(), stale, status.name());
    }
    private static RuntimeException missing(String name) { return new TrackingException(404, name + " not found"); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
}
