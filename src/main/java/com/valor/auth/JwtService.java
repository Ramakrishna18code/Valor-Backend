package com.valor.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService {
    private final SecretKey key;
    @Value("${jwt.expiration:86400000}") long exp;
    JwtService(@Value("${jwt.secret}") String secret) { if (secret == null || secret.length() < 32) throw new IllegalStateException("JWT_SECRET must be configured"); key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }
    String issue(User user) { return Jwts.builder().subject(String.valueOf(user.getId())).claim("role", user.getRole().name()).issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + exp)).signWith(key).compact(); }
    public Long subject(String token) { return Long.valueOf(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject()); }
}
