package com.valor.service;

import com.valor.repository.OtpVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OtpCleanupTask {
    private static final Logger logger = LoggerFactory.getLogger(OtpCleanupTask.class);

    private final OtpVerificationRepository otpRepository;
    private final int retentionDays;

    public OtpCleanupTask(OtpVerificationRepository otpRepository,
                          @Value("${otp.retention-days:7}") int retentionDays) {
        this.otpRepository = otpRepository;
        this.retentionDays = retentionDays;
    }

    // Run daily at 03:00 AM
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanOldOtps() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        long deleted = otpRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            logger.info("Deleted {} old OTP verification records older than {} days", deleted, retentionDays);
        } else {
            logger.debug("No old OTP records to delete");
        }
    }
}
