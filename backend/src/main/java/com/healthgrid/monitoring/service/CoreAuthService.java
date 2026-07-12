package com.healthgrid.monitoring.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoreAuthService {

    private final RestTemplate restTemplate;

    @Value("${healthgrid.module10.core.url:http://localhost:8081}")
    private String coreUrl;

    @Value("${healthgrid.module10.core.auth.email}")
    private String email;

    @Value("${healthgrid.module10.core.auth.password}")
    private String password;

    private String cachedToken = null;
    private long tokenExpirationTime = 0;

    /**
     * Obtains a valid JWT token from Module 10 (Core).
     * If a valid token is cached, it returns it.
     * Otherwise, it requests a new one.
     */
    public synchronized String getValidToken() {
        // Assume token expires in 24 hours. We'll refresh if it's older than 23 hours to be safe.
        if (cachedToken != null && System.currentTimeMillis() < tokenExpirationTime) {
            return cachedToken;
        }
        
        log.info("Requesting new JWT token from M10 Core...");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("email", email);
            requestBody.put("password", password);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(coreUrl + "/auth/login", request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String token = (String) response.getBody().get("token");
                if (token != null) {
                    this.cachedToken = token;
                    // Token is valid for 24h, we cache it for 23 hours (23 * 60 * 60 * 1000 ms)
                    this.tokenExpirationTime = System.currentTimeMillis() + (23 * 3600 * 1000L);
                    log.info("Successfully obtained new JWT token from M10 Core");
                    return this.cachedToken;
                }
            }
            log.error("Failed to parse token from M10 Core response: {}", response.getBody());
        } catch (Exception ex) {
            log.error("Exception occurred while requesting token from M10 Core", ex);
        }
        
        return null;
    }
    
    /**
     * Forces token invalidation (e.g. if we get a 401 using the cached token)
     */
    public synchronized void invalidateToken() {
        this.cachedToken = null;
        this.tokenExpirationTime = 0;
        log.info("Invalidated M10 Core cached token");
    }
}
