package com.healthgrid.monitoring.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoreEventPublisher {

    private final CoreAuthService authService;
    private final RestTemplate restTemplate;

    @Value("${healthgrid.module10.core.url:http://localhost:8081}")
    private String coreUrl;
    
    @Value("${healthgrid.module10.core.enabled:true}")
    private boolean coreEventsEnabled;

    /**
     * Publishes an event to M10 Core Event Bus.
     * 
     * @param eventTypeId 16 for alerta-emergencia, 17 for alerta-resuelta
     * @param escapedJsonPayload Escaped JSON string of the payload
     */
    public void publishEvent(int eventTypeId, String escapedJsonPayload) {
        if (!coreEventsEnabled) {
            log.info("Skipping Core event publish (id={}) because healthgrid.module10.core.enabled=false", eventTypeId);
            return;
        }

        String token = authService.getValidToken();
        if (token == null) {
            log.error("Failed to publish event to M10 Core: Could not obtain auth token");
            return;
        }

        boolean success = doPublish(token, eventTypeId, escapedJsonPayload);
        
        // If we got an unauthorized error, we retry once after invalidating the token
        if (!success) {
            log.info("Retrying event publish after token invalidation...");
            token = authService.getValidToken(); // will request a new one
            if (token != null) {
                doPublish(token, eventTypeId, escapedJsonPayload);
            }
        }
    }

    private boolean doPublish(String token, int eventTypeId, String payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("event_type_id", eventTypeId);
            requestBody.put("publisher_module", "monitoreo");
            requestBody.put("payload", payload); // String value, escaped JSON

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(coreUrl + "/events/log", request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✓ EVENT {} PUBLISHED TO M10 CORE", eventTypeId);
                return true;
            } else {
                log.warn("Unexpected response from M10 Core: {}", response.getStatusCode());
            }
        } catch (HttpClientErrorException.Unauthorized ex) {
            log.warn("Token rejected by M10 Core (401 Unauthorized). Invalidating local token cache.");
            authService.invalidateToken();
            return false;
        } catch (Exception ex) {
            log.error("Failed to publish event {} to M10 Core", eventTypeId, ex);
        }
        return true; // We return true to avoid infinite loops on other errors; it's a fire-and-forget.
    }
}
