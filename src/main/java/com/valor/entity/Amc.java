package com.valor.entity;

import com.valor.audit.AuditableEntity;
import com.valor.enums.AMCStatus;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Entity
@Table(name = "amcs")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Amc extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String amcNumber;
    private String plan;
    private String coverageDetails;

    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private AMCStatus status;

    private LocalDate renewalDate;
    private LocalDate lastReminderSentAt;
    private Integer renewalCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lift_id")
    @JsonIgnore
    private Lift lift;
}
