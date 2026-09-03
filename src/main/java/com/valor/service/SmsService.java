package com.valor.service;

public interface SmsService {
    void sendOtp(String mobileNumber, String otp);
}