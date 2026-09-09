package com.valor.assets;

import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "Lift")
@Table(name = "lifts")
@Getter @Setter
public class Lift extends AssetRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;
    @Column(name = "name", nullable = false, length = 160)
    private String name;
    @Column(name = "lift_number", nullable = true, length = 80)
    private String liftNumber;
    @Column(name = "model", nullable = true, length = 120)
    private String model;
    @Column(name = "manufacturer", nullable = true, length = 120)
    private String manufacturer;
    @Column(name = "capacity", nullable = true)
    private Integer capacity;
    @Column(name = "floor_count", nullable = true)
    private Integer floorCount;
    @Column(name = "serial_number", nullable = true, length = 120)
    private String serialNumber;
    @Column(name = "installation_date", nullable = true)
    private LocalDate installationDate;
    @Column(name = "location", nullable = true, length = 200)
    private String location;
    @Column(name = "current_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private LiftStatus currentStatus = LiftStatus.ACTIVE;
    @Column(name = "warranty_status", nullable = true, length = 80)
    private String warrantyStatus;
    @Column(name = "warranty_start_date", nullable = true)
    private LocalDate warrantyStartDate;
    @Column(name = "warranty_end_date", nullable = true)
    private LocalDate warrantyEndDate;
    @Column(name = "last_maintenance_date", nullable = true)
    private LocalDate lastMaintenanceDate;
    @Column(name = "next_maintenance_date", nullable = true)
    private LocalDate nextMaintenanceDate;
    @Column(name = "health_score", nullable = true)
    private Byte healthScore;
    @Column(name = "machine_room", nullable = true, length = 200)
    private String machineRoom;
    @Column(name = "qr_code", nullable = true, length = 255)
    private String qrCode;
    @Column(name = "specifications", nullable = true, columnDefinition = "TEXT")
    private String specifications;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
