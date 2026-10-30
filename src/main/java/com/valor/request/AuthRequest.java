package com.valor.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthRequest {
    @NotBlank(message = "Email is required")
    private String email;

    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
    @NotBlank(message = "Password is required")
    private String password;
}
