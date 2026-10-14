package com.valor.installation;

import com.valor.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "installation_request_history")
@Getter @Setter
public class InstallationRequestHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "installation_request_id", nullable = false)
    private InstallationRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "changed_by_user_id", nullable = false)
    private User changedBy;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 40) private InstallationStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 40) private InstallationStatus toStatus;
    @Column(length = 2000) private String notes;
    @Column(name = "changed_at", nullable = false) private LocalDateTime changedAt;
    @PrePersist void created() { if (changedAt == null) changedAt = LocalDateTime.now(); }
}
