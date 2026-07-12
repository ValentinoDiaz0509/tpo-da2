package com.healthgrid.monitoring.security;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JWT Token Provider for Module 10 (Core) token validation.
 * 
 * Uses JWKS (RS256) to validate tokens issued by Core.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final JwkProvider jwkProvider;

    public JwtTokenProvider(@Value("${healthgrid.module10.core.url:http://localhost:8081}") String coreUrl) throws Exception {
        // Build JWKS URL
        URL jwksUrl = new URL(coreUrl + "/.well-known/jwks.json");
        this.jwkProvider = new JwkProviderBuilder(jwksUrl)
                .cached(10, 24, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build();
        log.info("Initialized JWKS provider with URL: {}", jwksUrl);
    }

    /**
     * Validate a JWT token issued by Module 10 (Core).
     *
     * @param token the JWT token to validate
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            var claims = getClaimsFromToken(token);
            log.debug("JwtTokenProvider: ✓ Token validated. User: {}", claims.get("user_id"));
            return true;
        } catch (Exception e) {
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
            return Jwts.parser()
                    .keyLocator(new Locator<Key>() {
                        @Override
                        public Key locate(io.jsonwebtoken.Header header) {
                            try {
                                String kid = (String) header.get("kid");
                                if (kid == null) {
                                    throw new RuntimeException("kid missing in JWT header");
                                }
                                Jwk jwk = jwkProvider.get(kid);
                                return jwk.getPublicKey();
                            } catch (Exception e) {
                                throw new RuntimeException("Could not retrieve public key from JWKS", e);
                            }
                        }
                    })
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            log.warn("JwtTokenProvider: Error extracting claims from token", e);
            throw new RuntimeException("Failed to get claims from token", e);
        }
    }

    /**
     * Get user ID from token claims.
     *
     * @param token the JWT token
     * @return the user ID
     */
    public String getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return String.valueOf(claims.get("user_id"));
    }

    /**
     * Get permissions from token claims.
     *
     * @param token the JWT token
     * @return list of permissions
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("permissions", List.class);
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
