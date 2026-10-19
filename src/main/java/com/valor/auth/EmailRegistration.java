package com.valor.auth;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name="email_registration_challenges")
class EmailRegistrationChallenge {
 @Id @Column(length=64) String id;
 @Column(nullable=false,length=254,unique=true) String email;
 @Column(length=20) String phone;
 @Column(name="password_hash",nullable=false,length=255) String passwordHash;
 @Column(name="full_name",nullable=false,length=160) String fullName;
 @Column(name="alternate_phone",length=20) String alternatePhone;
 @Column(name="company_name",length=200) String companyName;
 @Column(length=500) String address;
 @Column(name="has_lift") Boolean hasLift;
 @Column(name="referral_code",length=32) String referralCode;
 @Column(name="otp_hash",nullable=false,length=255) String otpHash;
 @Column(name="attempts_remaining",nullable=false) short attemptsRemaining=3;
 @Column(name="expires_at",nullable=false) LocalDateTime expiresAt;
 @Column(name="locked_until") LocalDateTime lockedUntil;
 @Column(name="last_sent_at",nullable=false) LocalDateTime lastSentAt;
 @Column(name="delivery_status",nullable=false,length=30) String deliveryStatus;
 @Column(name="verified_at") LocalDateTime verifiedAt;
 @Column(name="created_at",nullable=false) LocalDateTime createdAt;
 @Column(name="updated_at",nullable=false) LocalDateTime updatedAt;
 @PrePersist void create(){createdAt=LocalDateTime.now();updatedAt=createdAt;}
 @PreUpdate void update(){updatedAt=LocalDateTime.now();}
}

interface EmailRegistrationRepository extends org.springframework.data.jpa.repository.JpaRepository<EmailRegistrationChallenge,String> {
 java.util.Optional<EmailRegistrationChallenge> findByEmail(String email);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select r from EmailRegistrationChallenge r where r.id=:id")
 java.util.Optional<EmailRegistrationChallenge> lock(@org.springframework.data.repository.query.Param("id") String id);
}
