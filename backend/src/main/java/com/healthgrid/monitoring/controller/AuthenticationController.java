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
            
            AuthInfo info = AuthInfo.builder()
                    .module("MONITORING")
                    .userId(principal)
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
