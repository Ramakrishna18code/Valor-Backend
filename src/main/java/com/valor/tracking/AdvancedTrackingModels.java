package com.valor.tracking;

import com.valor.auth.TechnicianProfile;
import com.valor.workflow.ServiceRequest;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity(name = "TechnicianLocationHistory")
@Table(name = "technician_location_history")
class TechnicianLocationHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_request_id", nullable = false)
    ServiceRequest serviceRequest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "technician_id", nullable = false)
    TechnicianProfile technician;
    @Column(nullable = false, precision = 9, scale = 6) BigDecimal latitude;
    @Column(nullable = false, precision = 9, scale = 6) BigDecimal longitude;
    @Column(nullable = false) LocalDateTime recordedAt;
    @Column(nullable = false) LocalDateTime createdAt;
    @PrePersist void pre() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}

enum GeofenceState { UNAVAILABLE, OUTSIDE, INSIDE }
enum GeofenceEventType { ENTERED, EXITED }

@Entity(name = "ServiceRequestGeofenceState")
@Table(name = "service_request_geofence_states")
class ServiceRequestGeofenceState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_request_id", nullable = false)
    ServiceRequest serviceRequest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "technician_id", nullable = false)
    TechnicianProfile technician;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    GeofenceState state = GeofenceState.UNAVAILABLE;
    @Column(precision = 10, scale = 2) BigDecimal distanceMeters;
    @Column(nullable = false) Integer radiusMeters;
    @Column(nullable = false) LocalDateTime evaluatedAt;
    @Column(nullable = false) LocalDateTime createdAt;
    @Column(nullable = false) LocalDateTime updatedAt;
    @PrePersist void pre() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void upd() { updatedAt = LocalDateTime.now(); }
}

@Entity(name = "ServiceRequestGeofenceEvent")
@Table(name = "service_request_geofence_events")
class ServiceRequestGeofenceEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_request_id", nullable = false)
    ServiceRequest serviceRequest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "technician_id", nullable = false)
    TechnicianProfile technician;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 20)
    GeofenceEventType eventType;
    @Column(precision = 10, scale = 2) BigDecimal distanceMeters;
    @Column(nullable = false) Integer radiusMeters;
    @Column(nullable = false) LocalDateTime occurredAt;
}

interface TechnicianLocationHistoryRepository extends JpaRepository<TechnicianLocationHistory, Long> {}
interface ServiceRequestGeofenceStateRepository extends JpaRepository<ServiceRequestGeofenceState, Long> {
    Optional<ServiceRequestGeofenceState> findByServiceRequestId(Long serviceRequestId);
}
interface ServiceRequestGeofenceEventRepository extends JpaRepository<ServiceRequestGeofenceEvent, Long> {}
