package com.civicconnect.backend.service;

import com.civicconnect.backend.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Issues and validates stateless JWTs.
 *
 * Deliberately minimal claims: user id, username, role, and ward id
 * (officers only). Never password, email, or phone — a JWT payload is
 * only base64-encoded, not encrypted, so anything in it should be
 * treated as visible to whoever holds the token.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryMillis;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiry-hours:24}") long expiryHours
    ) {
        // HMAC-SHA key derived from the configured secret. Must be at
        // least 256 bits — application.properties documents this.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMillis = expiryHours * 60 * 60 * 1000;
    }

    public String issueToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMillis);

        var builder = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key);

        if (user.getWard() != null) {
            builder.claim("wardId", user.getWard().getId());
        }

        return builder.compact();
    }

    /** Throws JwtException (expired/invalid/tampered) if the token doesn't check out. */
    public Claims parseAndValidate(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
