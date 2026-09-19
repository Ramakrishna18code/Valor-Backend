package com.valor.auth;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "onboarding_tokens")
class OnboardingToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) User user;
    @Column(nullable = false, length = 64, unique = true) String tokenHash;
    @Column(nullable = false) LocalDateTime expiresAt;
    LocalDateTime usedAt;
    @Column(nullable = false) LocalDateTime createdAt;
    @PrePersist void pre() { createdAt = LocalDateTime.now(); }
}
