package com.valor.service;

import com.valor.dto.VerifyOtpRequest;
import com.valor.response.LoginResponse;
import com.valor.response.OtpResponse;

public interface OtpService {
    OtpResponse sendOtp(String mobileNumber);
    LoginResponse verifyOtp(VerifyOtpRequest request);
}