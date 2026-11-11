package com.valor.protection;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ProtectionExpiryTask {
    private final ProtectionService protection;
    ProtectionExpiryTask(ProtectionService protection) { this.protection = protection; }

    @Scheduled(cron = "${valor.protection.expiry.cron:0 10 9 * * *}", zone = "${valor.protection.expiry.timezone:Asia/Kolkata}")
    public void generateDueNotices() { protection.generateExpiryNotices(); }
}
