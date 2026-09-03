package com.valor.response;

public record LoginResponse(
        String accessToken,
        String tokenType,
        CustomerSummary customer
) {
    public record CustomerSummary(
            Long id,
            String name,
            String mobileNumber
    ) {
    }
}