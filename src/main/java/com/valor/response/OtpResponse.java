package com.valor.response;

public record OtpResponse(
        String mobileNumber,
        int validitySeconds,
        int attemptsRemaining,
        String developmentOtp
) {
}
