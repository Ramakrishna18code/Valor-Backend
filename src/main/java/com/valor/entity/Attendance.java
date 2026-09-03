package com.valor.entity;

import com.valor.audit.AuditableEntity;
import com.valor.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_id", nullable = false)
    private Technician technician;

    @Column(nullable = false)
    private LocalDate attendanceDate;

    private LocalDateTime checkInAt;
    private LocalDateTime checkOutAt;
    private Double workingHours;
    private String location;

    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;
}