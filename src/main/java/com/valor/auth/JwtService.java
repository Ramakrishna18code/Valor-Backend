package com.valor.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService {
    private final SecretKey key;
    @Value("${jwt.expiration:86400000}") long exp;
    JwtService(@Value("${jwt.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32 || isPlaceholder(secret)) {
            throw new IllegalStateException("JWT_SECRET must be a secure random value of at least 32 bytes");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isPlaceholder(String secret) {
        String normalized = secret.toLowerCase(Locale.ROOT);
        return normalized.contains("change_me") || normalized.contains("changeme")
                || normalized.contains("placeholder") || normalized.contains("your_local")
                || normalized.contains("example") || normalized.matches("(.)\\1{15,}");
    }

    String issue(User user) { return Jwts.builder().subject(String.valueOf(user.getId())).claim("role", user.getRole().name()).issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + exp)).signWith(key).compact(); }
    public Long subject(String token) { return Long.valueOf(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject()); }
}
