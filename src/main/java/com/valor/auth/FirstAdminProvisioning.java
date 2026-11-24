package com.valor.auth;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.valor.response.ApiResponse;

final class FirstAdminProvisioning {
    record Request(@NotBlank @Size(max = 254) String email,
                   @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") @NotBlank @Size(min = 12, max = 72) String password) {
        @JsonAnySetter
        public void unknown(String key, JsonNode value) {
            throw new IllegalArgumentException("Unsupported field");
        }
    }

    record Result(Long userId, String email, Role role) {}
}

@Service
class FirstAdminProvisioningService {
    private final UserRepo users;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final String secret;

    FirstAdminProvisioningService(
            UserRepo users,
            PasswordEncoder encoder,
            @Value("${first-admin.provisioning.enabled:false}") boolean enabled,
            @Value("${first-admin.provisioning.secret:}") String secret) {
        this.users = users;
        this.encoder = encoder;
        this.enabled = enabled;
        this.secret = secret == null ? "" : secret;
    }

    @Transactional
    FirstAdminProvisioning.Result provision(String suppliedSecret, FirstAdminProvisioning.Request request) {
        if (!enabled || !validSecret(suppliedSecret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "First-admin provisioning is unavailable");
        }
        if (users.countByRole(Role.ADMIN) > 0 || users.countByRole(Role.SUPER_ADMIN) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "First administrator is already provisioned");
        }
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email");
        }
        if (users.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with these details already exists.");
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(encoder.encode(request.password()));
        admin.setRole(Role.SUPER_ADMIN);
        admin.setActive(true);
        User saved = users.saveAndFlush(admin);
        return new FirstAdminProvisioning.Result(saved.getId(), saved.getEmail(), saved.getRole());
    }

    private boolean validSecret(String suppliedSecret) {
        if (secret.length() < 32 || suppliedSecret == null || suppliedSecret.isBlank()) return false;
        return MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8));
    }
}

@RestController
@RequestMapping("/api/v1/auth")
class FirstAdminProvisioningController {
    private final FirstAdminProvisioningService provisioning;

    FirstAdminProvisioningController(FirstAdminProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @PostMapping("/first-admin")
    ApiResponse<FirstAdminProvisioning.Result> provision(
            @RequestHeader(name = "X-Valor-First-Admin-Secret", required = false) String secret,
            @Valid @RequestBody FirstAdminProvisioning.Request request) {
        return ApiResponse.success("First administrator provisioned", provisioning.provision(secret, request), 200);
    }
}

