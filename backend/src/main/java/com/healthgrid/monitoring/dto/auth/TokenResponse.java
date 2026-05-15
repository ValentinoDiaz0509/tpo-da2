package com.healthgrid.monitoring.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    private String token;
    private String type;
    private long expiresIn;
    private String module;
    private String userId;
    private String issuer;
}
