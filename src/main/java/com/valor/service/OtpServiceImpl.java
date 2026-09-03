package com.valor.service;

import com.valor.dto.VerifyOtpRequest;
import com.valor.entity.Customer;
import com.valor.entity.OtpVerification;
import com.valor.enums.CustomerStatus;
import com.valor.exception.AuthApiException;
import com.valor.repository.CustomerRepository;
import com.valor.repository.OtpVerificationRepository;
import com.valor.response.LoginResponse;
import com.valor.response.OtpResponse;
import com.valor.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Arrays;
import java.util.List;

@Service
public class OtpServiceImpl implements OtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int OTP_LENGTH = 6;

    private final OtpVerificationRepository otpVerificationRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsService smsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final Environment environment;
    private final int validityMinutes;
    private final int maxAttempts;
    private final int lockMinutes;
    private final int resendCooldownSeconds;
    private final int hourlyLimit;

    public OtpServiceImpl(OtpVerificationRepository otpVerificationRepository,
                          CustomerRepository customerRepository,
                          PasswordEncoder passwordEncoder,
                          SmsService smsService,
                          JwtTokenProvider jwtTokenProvider,
                          Environment environment,
                          @Value("${otp.validity-minutes:5}") int validityMinutes,
                          @Value("${otp.max-attempts:3}") int maxAttempts,
                          @Value("${otp.lock-minutes:15}") int lockMinutes,
                          @Value("${otp.resend-cooldown-seconds:30}") int resendCooldownSeconds,
                          @Value("${otp.hourly-limit:5}") int hourlyLimit) {
        this.otpVerificationRepository = otpVerificationRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsService = smsService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.environment = environment;
        this.validityMinutes = validityMinutes;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.hourlyLimit = hourlyLimit;
    }

    @Override
    @Transactional
    public OtpResponse sendOtp(String mobileNumber) {
        LocalDateTime now = LocalDateTime.now();
        List<OtpVerification> history = otpVerificationRepository.findByMobileNumberOrderByCreatedAtDesc(mobileNumber);
        if (!history.isEmpty()) {
            OtpVerification latest = history.get(0);
            if (latest.getLockedUntil() != null && latest.getLockedUntil().isAfter(now)) {
                long remaining = java.time.Duration.between(now, latest.getLockedUntil()).getSeconds();
                throw new com.valor.exception.LockedAuthException("Too many incorrect OTP attempts. Please try again later.", HttpStatus.TOO_MANY_REQUESTS, remaining);
            }
            if (latest.getCreatedAt() != null && latest.getCreatedAt().isAfter(now.minusSeconds(resendCooldownSeconds))) {
                throw new AuthApiException("Please wait before requesting another OTP.", HttpStatus.TOO_MANY_REQUESTS);
            }
        }

        long hourlyRequests = otpVerificationRepository.countByMobileNumberAndCreatedAtAfter(mobileNumber, now.minusHours(1));
        if (hourlyRequests >= hourlyLimit) {
            throw new AuthApiException("Too many OTP requests. Please try again later.", HttpStatus.TOO_MANY_REQUESTS);
        }

        otpVerificationRepository.invalidateActiveOtps(mobileNumber, now);

        String otp = generateOtp();
        OtpVerification otpVerification = OtpVerification.builder()
                .mobileNumber(mobileNumber)
                .otpHash(passwordEncoder.encode(otp))
                .attemptsRemaining(maxAttempts)
                .maxAttempts(maxAttempts)
                .expiresAt(now.plusMinutes(validityMinutes))
                .verified(false)
                .build();

        otpVerificationRepository.save(otpVerification);
        smsService.sendOtp(mobileNumber, otp);
        return new OtpResponse(mobileNumber, validityMinutes * 60, maxAttempts, isProduction() ? null : otp);
    }

    @Override
    @Transactional
    public LoginResponse verifyOtp(VerifyOtpRequest request) {
        LocalDateTime now = LocalDateTime.now();
        List<OtpVerification> otpHistory = otpVerificationRepository.findByMobileNumberOrderByCreatedAtDesc(request.mobileNumber());
        if (otpHistory.isEmpty()) {
            throw new AuthApiException("OTP not found. Please request a new OTP.", HttpStatus.BAD_REQUEST);
        }

        OtpVerification matchingRecord = otpHistory.stream()
                .filter(otp -> passwordEncoder.matches(request.otp(), otp.getOtpHash()))
                .findFirst()
                .orElse(null);

        if (matchingRecord != null) {
            if (Boolean.TRUE.equals(matchingRecord.getVerified())) {
                throw new AuthApiException("OTP is no longer valid.", HttpStatus.BAD_REQUEST);
            }
            if (matchingRecord.getLockedUntil() != null && matchingRecord.getLockedUntil().isAfter(now)) {
                long remaining = java.time.Duration.between(now, matchingRecord.getLockedUntil()).getSeconds();
                throw new com.valor.exception.LockedAuthException("Too many incorrect OTP attempts. Please try again later.", HttpStatus.TOO_MANY_REQUESTS, remaining);
            }
            if (matchingRecord.getExpiresAt() != null && matchingRecord.getExpiresAt().isBefore(now)) {
                throw new AuthApiException("OTP expired. Please request a new OTP.", HttpStatus.BAD_REQUEST);
            }

            matchingRecord.setVerified(true);
            matchingRecord.setVerifiedAt(now);
            matchingRecord.setAttemptsRemaining(0);
            otpVerificationRepository.save(matchingRecord);

            Customer customer = customerRepository.findByPhone(request.mobileNumber())
                    .orElseThrow(() -> new AuthApiException("Customer not registered. Please complete registration.", HttpStatus.BAD_REQUEST));

            validateCustomer(customer);
            String accessToken = jwtTokenProvider.generateToken(request.mobileNumber());
            return new LoginResponse(accessToken, "Bearer", new LoginResponse.CustomerSummary(customer.getId(), customer.getName(), customer.getPhone()));
        }

        OtpVerification latestActive = otpHistory.stream()
                .filter(otp -> Boolean.FALSE.equals(otp.getVerified()))
                .filter(otp -> otp.getExpiresAt() == null || otp.getExpiresAt().isAfter(now))
                .max(Comparator.comparing(OtpVerification::getCreatedAt))
                .orElse(null);

        if (latestActive == null) {
            OtpVerification latestAny = otpHistory.get(0);
            if (Boolean.TRUE.equals(latestAny.getVerified())) {
                throw new AuthApiException("OTP is no longer valid.", HttpStatus.BAD_REQUEST);
            }
            if (latestAny.getExpiresAt() != null && latestAny.getExpiresAt().isBefore(now)) {
                throw new AuthApiException("OTP expired. Please request a new OTP.", HttpStatus.BAD_REQUEST);
            }
            throw new AuthApiException("OTP not found. Please request a new OTP.", HttpStatus.BAD_REQUEST);
        }

        if (latestActive.getLockedUntil() != null && latestActive.getLockedUntil().isAfter(now)) {
            long remaining = java.time.Duration.between(now, latestActive.getLockedUntil()).getSeconds();
            throw new com.valor.exception.LockedAuthException("Too many incorrect OTP attempts. Please try again later.", HttpStatus.TOO_MANY_REQUESTS, remaining);
        }

        if (latestActive.getExpiresAt() != null && latestActive.getExpiresAt().isBefore(now)) {
            throw new AuthApiException("OTP expired. Please request a new OTP.", HttpStatus.BAD_REQUEST);
        }

        int remainingAttempts = Math.max(0, latestActive.getAttemptsRemaining() - 1);
        latestActive.setAttemptsRemaining(remainingAttempts);
        if (remainingAttempts == 0) {
            latestActive.setLockedUntil(now.plusMinutes(lockMinutes));
            otpVerificationRepository.save(latestActive);
            throw new AuthApiException("Too many incorrect OTP attempts. Please try again later.", HttpStatus.TOO_MANY_REQUESTS);
        }

        otpVerificationRepository.save(latestActive);
        throw new AuthApiException("Invalid OTP.", HttpStatus.BAD_REQUEST);
    }

    private String generateOtp() {
        StringBuilder otp = new StringBuilder(OTP_LENGTH);
        for (int index = 0; index < OTP_LENGTH; index++) {
            otp.append(SECURE_RANDOM.nextInt(10));
        }
        return otp.toString();
    }

    private boolean isProduction() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
    }

    private void validateCustomer(Customer customer) {
        if (!Boolean.TRUE.equals(customer.getEnabled()) || customer.getAccountStatus() != CustomerStatus.ACTIVE) {
            throw new AuthApiException("Customer account is inactive.", HttpStatus.FORBIDDEN);
        }
    }
}
