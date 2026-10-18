package com.smartmall.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final Duration expire;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expire-hours:72}") long expireHours) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("app.jwt.secret 至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expire = Duration.ofHours(expireHours);
    }

    public String generate(String userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expire.toMillis()))
                .signWith(key)
                .compact();
    }

    public CurrentUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new CurrentUser(claims.getSubject(), claims.get("username", String.class),
                claims.get("role", String.class));
    }
}
