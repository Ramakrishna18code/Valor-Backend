package com.valor.tracking;

import com.valor.auth.TechnicianProfile;
import com.valor.workflow.ServiceRequest;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "TechnicianLatestLocation")
@Table(name = "technician_latest_locations")
@Getter @Setter
class TechnicianLatestLocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "technician_id", nullable = false)
    private TechnicianProfile technician;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;
    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;
    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist void pre() { updatedAt = LocalDateTime.now(); }
    @PreUpdate void upd() { updatedAt = LocalDateTime.now(); }
}
