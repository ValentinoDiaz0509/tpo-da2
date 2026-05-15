package com.healthgrid.monitoring.controller;

import com.healthgrid.monitoring.dto.auth.AuthInfo;
import com.healthgrid.monitoring.dto.auth.TokenRequest;
import com.healthgrid.monitoring.dto.auth.TokenResponse;
import com.healthgrid.monitoring.dto.auth.TokenValidationResponse;
import com.healthgrid.monitoring.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication Controller - Simulates Module 10 (Core) token issuer.
 * 
 * Provides endpoints for:
 * - Token generation (Module 10 issues tokens)
 * - Token validation
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "JWT token endpoints (Module 10 - Core)")
public class AuthenticationController {

    // TODO(core): eliminar este controller del flujo real cuando Core emita y valide JWT.
    // TODO(core): conservarlo solo bajo perfil mock/dev si sigue siendo util para pruebas locales.
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Generate a JWT token (issued by Module 10 - Core).
     * 
     * @param request the token request with module and userId
     * @return the generated JWT token
     */
    @PostMapping("/token")
    @Operation(
            summary = "Generate JWT token from Module 10 (Core)",
            description = "Request a JWT token from Module 10. Token is valid for 24 hours."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token generated successfully",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Token generation failed")
    })
    public ResponseEntity<TokenResponse> generateToken(@RequestBody TokenRequest request) {
        log.info("AuthenticationController: Generating token for module: {}, userId: {}", 
                request.getModule(), request.getUserId());

        try {
            String token = jwtTokenProvider.generateToken(request.getModule(), request.getUserId());
            
            TokenResponse response = TokenResponse.builder()
                    .token(token)
                    .type("Bearer")
                    .expiresIn(86400)  // 24 hours in seconds
                    .module(request.getModule())
                    .userId(request.getUserId())
                    .issuer("Module10-Core")
                    .build();

            log.info("AuthenticationController: ✓ Token generated successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("AuthenticationController: Failed to generate token", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Validate a JWT token.
     * 
     * @param authHeader the Authorization header with Bearer token
     * @return validation result
     */
    @PostMapping("/validate")
    @Operation(
            summary = "Validate JWT token",
            description = "Check if a JWT token is valid and issued by Module 10"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token validation result",
                    content = @Content(schema = @Schema(implementation = TokenValidationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<TokenValidationResponse> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        log.info("AuthenticationController: Validating token");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            TokenValidationResponse response = TokenValidationResponse.builder()
                    .valid(false)
                    .message("Invalid Authorization header format")
                    .build();
            return ResponseEntity.ok(response);
        }

        String token = authHeader.substring(7);
        boolean isValid = jwtTokenProvider.validateToken(token);

        TokenValidationResponse response = TokenValidationResponse.builder()
                .valid(isValid)
                .message(isValid ? "Token is valid and issued by Module 10" : "Token is invalid or expired")
                .build();

        if (isValid) {
            response.setModule(jwtTokenProvider.getModuleFromToken(token));
            response.setUserId(jwtTokenProvider.getUserIdFromToken(token));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get current authentication info.
     * 
     * @return current authenticated user/module info
     */
    @GetMapping("/me")
    @Operation(
            summary = "Get current authentication information",
            description = "Returns information about the currently authenticated module"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current auth info",
                    content = @Content(schema = @Schema(implementation = AuthInfo.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<AuthInfo> getCurrentAuthInfo() {
        try {
            String principal = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            String[] parts = principal.split(":");
            
            AuthInfo info = AuthInfo.builder()
                    .module(parts.length > 0 ? parts[0] : "UNKNOWN")
                    .userId(parts.length > 1 ? parts[1] : "UNKNOWN")
                    .authenticated(true)
                    .issuer("Module10-Core")
                    .build();

            return ResponseEntity.ok(info);

        } catch (Exception e) {
            log.warn("AuthenticationController: Error getting auth info", e);
            AuthInfo info = AuthInfo.builder()
                    .authenticated(false)
                    .build();
            return ResponseEntity.ok(info);
        }
    }

}
