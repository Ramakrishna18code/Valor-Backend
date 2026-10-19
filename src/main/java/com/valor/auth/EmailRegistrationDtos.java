package com.valor.auth;
import com.fasterxml.jackson.annotation.JsonAnySetter;import com.fasterxml.jackson.databind.JsonNode;import jakarta.validation.constraints.*;import java.time.LocalDateTime;
final class EmailRegistrationDtos {
 interface Strict { @JsonAnySetter default void unknown(String k,JsonNode v){throw new IllegalArgumentException("Unsupported field");} }
 record Start(@NotBlank String email,String phone,@io.swagger.v3.oas.annotations.media.Schema(accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY,format="password") @NotBlank @Size(max=72) String password,@NotBlank @Size(max=160) String fullName,@Size(max=20) String alternatePhone,@Size(max=200) String companyName,@Size(max=500) String address,Boolean hasLift,@Size(max=32) String referralCode) implements Strict{}
 record Resend(@NotBlank String challengeId) implements Strict{}
 record Verify(@NotBlank String challengeId,@NotBlank @Size(min=6,max=6) String otp) implements Strict{}
 record Started(String challengeId,LocalDateTime expiresAt,String status){}
}
