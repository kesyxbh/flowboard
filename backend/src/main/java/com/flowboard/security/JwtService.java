package com.flowboard.security;

import com.flowboard.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(AppProperties props) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        // Fail at start-up, not at the first login, if the secret is too short to be a safe HMAC key.
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes (set the JWT_SECRET env var)");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.expiration = props.jwt().expiration();
    }

    /**
     * The token carries only the user id (subject) and the issue/expiry times. A JWT payload is Base64, not
     * encrypted, so nothing sensitive goes in. The username is looked up per request instead of being read from
     * a token that might be stale.
     */
    public String generateToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /** Throws a subclass of io.jsonwebtoken.JwtException for a bad signature, a malformed token or an expired one. */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
