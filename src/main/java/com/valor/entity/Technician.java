package com.valor.entity;

import com.valor.audit.AuditableEntity;
import com.valor.enums.RoleName;
import com.valor.enums.TechnicianAvailabilityStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Builder.Default;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "technicians")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Technician extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;

    @Column(unique = true)
    private String employeeId;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String phone;

    private String assignedArea;
    private String specialization;

    @Builder.Default
    private Integer currentWorkload = 0;

    @Builder.Default
    private Integer pendingJobs = 0;

    private Double rating;

    private LocalDate lastWorkingDay;

    private LocalDateTime lastActiveAt;

    @Enumerated(EnumType.STRING)
    @Default
    private RoleName role = RoleName.TECHNICIAN;

    @Enumerated(EnumType.STRING)
    @Default
    private TechnicianAvailabilityStatus availabilityStatus = TechnicianAvailabilityStatus.AVAILABLE;
}
