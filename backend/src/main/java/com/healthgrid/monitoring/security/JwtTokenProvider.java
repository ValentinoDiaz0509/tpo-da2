package com.healthgrid.monitoring.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT Token Provider for Module 10 (Core) token validation.
 * 
 * Simulates Module 10 (Core) as the token issuer.
 * Provides token creation and validation functionality.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    // TODO(core): este secret/issuer deberian venir del contrato real con Core.
    // TODO(core): este componente deberia validar tokens emitidos por Core, no generarlos en produccion.
    @Value("${security.jwt.secret:healthgrid-monitoring-secret-key-module10-issuer-2026-hs512-secure-key-abcdef1234567890}")
    private String jwtSecret;

    @Value("${security.jwt.expiration:86400000}") // 24 hours in milliseconds
    private long jwtExpirationMs;

    @Value("${security.jwt.issuer:Module10-Core}")
    private String jwtIssuer;

    /**
     * Generate a JWT token signed by Module 10 (Core).
     *
     * @param moduleId the module identifier (e.g., "Monitoring")
     * @param userId the user/system ID
     * @return the signed JWT token
     */
    public String generateToken(String moduleId, String userId) {
        // TODO(core): remover la generacion local de JWT cuando Core sea el issuer unico del sistema.
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            
            String token = Jwts.builder()
                    .issuer(jwtIssuer)  // Module 10 (Core) as issuer
                    .subject(moduleId + ":" + userId)
                    .claim("module", moduleId)
                    .claim("userId", userId)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                    .signWith(key, SignatureAlgorithm.HS512)
                    .compact();

            log.info("JwtTokenProvider: ✓ Token generated for module: {}, userId: {}", moduleId, userId);
            return token;

        } catch (Exception e) {
            log.error("JwtTokenProvider: Error generating token", e);
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    /**
     * Validate a JWT token issued by Module 10 (Core).
     *
     * @param token the JWT token to validate
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            
            var claims = Jwts.parser()
                    .setSigningKey(key)
                    .requireIssuer(jwtIssuer)  // Verify issuer is Module 10 (Core)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            log.debug("JwtTokenProvider: ✓ Token validated. Subject: {}", claims.getSubject());
            return true;

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JwtTokenProvider: Invalid or expired token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get claims from a valid JWT token.
     *
     * @param token the JWT token
     * @return the Claims object
     */
    public Claims getClaimsFromToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            
            return Jwts.parser()
                    .setSigningKey(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (JwtException e) {
            log.warn("JwtTokenProvider: Error extracting claims from token", e);
            throw new RuntimeException("Failed to get claims from token", e);
        }
    }

    /**
     * Get module ID from token claims.
     *
     * @param token the JWT token
     * @return the module ID
     */
    public String getModuleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("module", String.class);
    }

    /**
     * Get user ID from token claims.
     *
     * @param token the JWT token
     * @return the user ID
     */
    public String getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("userId", String.class);
    }

    /**
     * Get subject (module:userId) from token.
     *
     * @param token the JWT token
     * @return the subject
     */
    public String getSubjectFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getSubject();
    }

    /**
     * Check if token is expired.
     *
     * @param token the JWT token
     * @return true if expired, false otherwise
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

}
