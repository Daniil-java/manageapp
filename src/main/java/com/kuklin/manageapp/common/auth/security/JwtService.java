package com.kuklin.manageapp.common.auth.security;

import com.kuklin.manageapp.common.entities.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expirationMs;

    public String generateToken(AppUser user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("appUserId", user.getId())
                .claim("roles", user.getRoles().stream()
                        .map(r -> r.getRoleName().name())
                        .toList())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .compact();
    }

    public Long extractAppUserId(String token) {
        return getClaims(token).get("appUserId", Long.class);
    }

    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    // Добавлен метод — нужен в JwtAuthenticationFilter для authorities
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object roles = getClaims(token).get("roles");
        if (roles instanceof List<?> list) {
            return list.stream()
                    .filter(o -> o instanceof String)
                    .map(o -> (String) o)
                    .toList();
        }
        return List.of();
    }

    public boolean isTokenValid(String token) {
        try {
            getClaims(token); // бросит исключение если истёк или невалиден
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
