package com.valor.assets;

import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "AmcContract")
@Table(name = "amc_contracts")
@Getter @Setter
public class AmcContract extends AssetRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lift_id", nullable = false)
    private Lift lift;
    @Column(name = "amc_number", nullable = false, length = 80)
    private String amcNumber;
    @Column(name = "plan", nullable = false, length = 80)
    private String plan;
    @Column(name = "coverage_details", nullable = true, columnDefinition = "TEXT")
    private String coverageDetails;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private AmcStatus status = AmcStatus.ACTIVE;
    @Column(name = "renewal_date", nullable = true)
    private LocalDate renewalDate;
    @Column(name = "last_reminder_sent_at", nullable = true)
    private LocalDateTime lastReminderSentAt;
    @Column(name = "renewal_count", nullable = false)
    private Integer renewalCount = 0;
}
