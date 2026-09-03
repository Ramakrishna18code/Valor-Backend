package com.valor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class SmsServiceImpl implements SmsService {

    private static final Logger logger = LoggerFactory.getLogger(SmsServiceImpl.class);

    private final Environment environment;

    public SmsServiceImpl(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void sendOtp(String mobileNumber, String otp) {
        if (isProduction()) {
            logger.info("OTP generated for mobile number {}", mobileNumber);
            return;
        }
        logger.warn("[DEV OTP] Mobile number {} | Code: {} | Expires in 5 minutes", mobileNumber, otp);
    }

    private boolean isProduction() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
    }
}