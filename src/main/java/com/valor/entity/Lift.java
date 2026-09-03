package com.valor.entity;

import com.valor.audit.AuditableEntity;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.valor.enums.LiftStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Entity
@Table(name = "lifts")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lift extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String liftNumber;

    private String model;
    private String manufacturer;
    private Integer capacity;
    private Integer floorCount;
    private String serialNumber;
    private LocalDate installationDate;
    private String location;
    @Enumerated(EnumType.STRING)
    private LiftStatus currentStatus;
    @Enumerated(EnumType.STRING)
    private com.valor.enums.AMCStatus amcStatus;
    private String warrantyStatus;
    private LocalDate warrantyStartDate;
    private LocalDate warrantyEndDate;
    private LocalDate lastMaintenanceDate;
    private LocalDate nextMaintenanceDate;
    private Integer totalBreakdowns;
    private Integer healthScore;
    private String machineRoom;
    private String qrCode;
    private String specifications;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @JsonIgnore
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    @JsonIgnore
    private Building building;
}
