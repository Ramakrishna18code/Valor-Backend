package com.valor.repository;

import com.valor.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    List<OtpVerification> findByMobileNumberOrderByCreatedAtDesc(String mobileNumber);

    Optional<OtpVerification> findFirstByMobileNumberAndVerifiedFalseOrderByCreatedAtDesc(String mobileNumber);

    long countByMobileNumberAndCreatedAtAfter(String mobileNumber, LocalDateTime createdAtAfter);

    long deleteByCreatedAtBefore(LocalDateTime cutoff);

    @Modifying
    @Query("update OtpVerification otp set otp.verified = true, otp.verifiedAt = :verifiedAt where otp.mobileNumber = :mobileNumber and otp.verified = false")
    int invalidateActiveOtps(@Param("mobileNumber") String mobileNumber, @Param("verifiedAt") LocalDateTime verifiedAt);
}