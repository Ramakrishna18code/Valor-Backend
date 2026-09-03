package com.valor.entity;

import com.valor.audit.AuditableEntity;
import com.valor.enums.PriorityLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "emergency_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyRequest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lift_id")
    private Lift lift;

    private String emergencyNumber;
    @Column(length = 2000)
    private String problemDescription;
    @Enumerated(EnumType.STRING)
    private PriorityLevel emergencyLevel;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;
}