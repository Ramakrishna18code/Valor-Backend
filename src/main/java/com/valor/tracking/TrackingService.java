package com.valor.tracking;

import com.valor.auth.*;
import com.valor.workflow.*;
import java.time.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final TechnicianLocationHistoryRepository history;
    private final ServiceRequestGeofenceStateRepository geofenceStates;
    private final ServiceRequestGeofenceEventRepository geofenceEvents;
    private final RoutingProvider routing;
    private final Clock clock;
    private final TrackingProperties properties;

    TrackingService(AssetIdentityAccess identities, WorkflowIdentityAccess profiles, TrackingRequestRepository requests,
            TrackingAssignmentRepository assignments, TechnicianLatestLocationRepository locations,
            TechnicianLocationHistoryRepository history, ServiceRequestGeofenceStateRepository geofenceStates,
            ServiceRequestGeofenceEventRepository geofenceEvents, RoutingProvider routing, TrackingProperties properties, Clock clock) {
        this.identities = identities; this.profiles = profiles; this.requests = requests; this.assignments = assignments;
        this.locations = locations; this.history = history; this.geofenceStates = geofenceStates; this.geofenceEvents = geofenceEvents;
        this.routing = routing; this.properties = properties; this.clock = clock;
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
        TechnicianLatestLocation saved = locations.saveAndFlush(row);
        saveHistory(request, technician, saved);
        updateGeofence(request, technician, saved);
        return view(saved, request);
    }

    @Transactional(readOnly = true)
    LocationView customerLocation(Long requestId) {
        User actor = identities.actor();
        if (actor.getRole() != Role.CUSTOMER) throw denied();
        ServiceRequest request = requests.withCustomer(requestId).orElseThrow(() -> missing("Service request"));
        if (!request.getCustomer().getUser().getId().equals(actor.getId())) throw denied();
        if (!TRACKABLE.contains(request.getStatus())) throw new TrackingException(409, "Tracking is not active for this request");
        TechnicianLatestLocation row = locations.findByServiceRequestId(requestId).orElseThrow(() -> missing("Technician location"));
        return view(row, request);
    }

    @Transactional(readOnly = true)
    LocationView technicianLocation(Long requestId) {
        User actor = identities.actor();
        if (actor.getRole() != Role.TECHNICIAN) throw denied();
        TechnicianProfile technician = profiles.technician(actor);
        ServiceRequest request = requests.findById(requestId).orElseThrow(() -> missing("Service request"));
        TechnicianAssignment assignment = assignments.active(requestId).orElseThrow(() -> denied());
        if (!assignment.getTechnician().getId().equals(technician.getId())) throw denied();
        if (!TRACKABLE.contains(request.getStatus())) throw new TrackingException(409, "Tracking is not active for this job");
        TechnicianLatestLocation row = locations.findByServiceRequestId(requestId).orElseThrow(() -> missing("Technician location"));
        return view(row, request);
    }

    private void saveHistory(ServiceRequest request, TechnicianProfile technician, TechnicianLatestLocation latest) {
        TechnicianLocationHistory row = new TechnicianLocationHistory();
        row.serviceRequest = request; row.technician = technician; row.latitude = latest.getLatitude();
        row.longitude = latest.getLongitude(); row.recordedAt = latest.getRecordedAt();
        history.save(row);
    }

    private void updateGeofence(ServiceRequest request, TechnicianProfile technician, TechnicianLatestLocation latest) {
        var building = request.getLift().getBuilding();
        if (building.getLatitude() == null || building.getLongitude() == null) return;
        BigDecimal distance = BigDecimal.valueOf(distanceMeters(latest.getLatitude(), latest.getLongitude(), building.getLatitude(), building.getLongitude()))
                .setScale(2, RoundingMode.HALF_UP);
        int radius = properties.geofenceRadiusMeters();
        GeofenceState next = distance.compareTo(BigDecimal.valueOf(radius)) <= 0 ? GeofenceState.INSIDE : GeofenceState.OUTSIDE;
        ServiceRequestGeofenceState state = geofenceStates.findByServiceRequestId(request.getId()).orElseGet(ServiceRequestGeofenceState::new);
        GeofenceState previous = state.id == null ? GeofenceState.UNAVAILABLE : state.state;
        state.serviceRequest = request; state.technician = technician; state.state = next; state.distanceMeters = distance;
        state.radiusMeters = radius; state.evaluatedAt = latest.getRecordedAt();
        geofenceStates.save(state);
        if (previous != GeofenceState.UNAVAILABLE && previous != next) {
            ServiceRequestGeofenceEvent event = new ServiceRequestGeofenceEvent();
            event.serviceRequest = request; event.technician = technician;
            event.eventType = next == GeofenceState.INSIDE ? GeofenceEventType.ENTERED : GeofenceEventType.EXITED;
            event.distanceMeters = distance; event.radiusMeters = radius; event.occurredAt = latest.getRecordedAt();
            geofenceEvents.save(event);
        }
    }

    private LocationView view(TechnicianLatestLocation row, ServiceRequest request) {
        boolean stale = row.getRecordedAt().isBefore(LocalDateTime.now(clock).minusMinutes(10));
        GeofenceView geofence = geofenceStates.findByServiceRequestId(request.getId())
                .map(state -> new GeofenceView(state.state.name(), state.distanceMeters, state.radiusMeters, state.evaluatedAt, null))
                .orElse(new GeofenceView("UNAVAILABLE", null, properties.geofenceRadiusMeters(), null, null));
        RouteView route = route(row, request, stale);
        EtaView eta = route.available() && !stale
                ? new EtaView(true, LocalDateTime.now(clock).plusSeconds(route.durationSeconds()), route.durationSeconds(), "AVAILABLE")
                : new EtaView(false, null, null, stale ? "STALE_LOCATION" : route.status());
        return new LocationView(row.getLatitude(), row.getLongitude(), row.getRecordedAt(), stale, request.getStatus().name(), geofence, route, eta);
    }

    private RouteView route(TechnicianLatestLocation row, ServiceRequest request, boolean stale) {
        var building = request.getLift().getBuilding();
        if (stale) return new RouteView(false, null, null, "STALE_LOCATION", java.util.List.of());
        if (building.getLatitude() == null || building.getLongitude() == null) return new RouteView(false, null, null, "SITE_LOCATION_UNAVAILABLE", java.util.List.of());
        RouteResult result = routing.route(new RoutePoint(row.getLatitude(), row.getLongitude()), new RoutePoint(building.getLatitude(), building.getLongitude()));
        return new RouteView(result.available(), result.distanceMeters(), result.durationSeconds(), result.providerState(),
                result.polyline().stream().map(p -> new RoutePointView(p.latitude(), p.longitude())).toList());
    }

    private static double distanceMeters(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double earth = 6371000.0, a1 = Math.toRadians(lat1.doubleValue()), a2 = Math.toRadians(lat2.doubleValue());
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue()), dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(a1) * Math.cos(a2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earth * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
    private static RuntimeException missing(String name) { return new TrackingException(404, name + " not found"); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
}
