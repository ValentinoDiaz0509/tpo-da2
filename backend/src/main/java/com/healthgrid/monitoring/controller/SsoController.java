package com.healthgrid.monitoring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "SSO Authentication", description = "Endpoints para Single Sign-On con Módulo 10 (Core)")
public class SsoController {

    private final RestTemplate restTemplate;

    @Value("${healthgrid.module10.core.url:http://localhost:8081}")
    private String coreUrl;

    @GetMapping("/sso")
    @Operation(summary = "Callback para SSO que recibe y canjea el ticket por un JWT")
    public void ssoCallback(
            @RequestParam("ticket") String ticket,
            @RequestParam(value = "redirect", defaultValue = "/") String redirect,
            HttpServletResponse response) throws IOException {
        
        log.info("Recibido SSO ticket. Intercambiando por JWT...");
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("ticket", ticket);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            // POST /auth/sso-exchange a Core
            ResponseEntity<Map> coreResponse = restTemplate.postForEntity(
                coreUrl + "/auth/sso-exchange", 
                request, 
                Map.class
            );

            if (coreResponse.getStatusCode().is2xxSuccessful() && coreResponse.getBody() != null) {
                String token = (String) coreResponse.getBody().get("token");
                if (token != null) {
                    // Configurar cookie de sesión (HttpOnly, Secure, Lax)
                    Cookie sessionCookie = new Cookie("session", token);
                    sessionCookie.setHttpOnly(true);
                    // sessionCookie.setSecure(true); // Omitido localmente si no usamos HTTPS
                    sessionCookie.setPath("/");
                    sessionCookie.setMaxAge(24 * 3600); // 24 horas
                    // En un ambiente moderno se debería configurar SameSite=Lax explícitamente pero Cookie de Servlet no lo tiene por defecto en versiones antiguas, en Tomcat 9+ se envía automáticamente Lax si no hay config. Alternativa: header Set-Cookie.
                    
                    response.addCookie(sessionCookie);
                    response.setHeader("Set-Cookie", "session=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=86400");
                    
                    // Validar redirect para evitar open-redirect
                    if (redirect.startsWith("/") && !redirect.startsWith("//")) {
                        response.sendRedirect(redirect);
                    } else {
                        response.sendRedirect("/");
                    }
                    return;
                }
            }
            log.error("Fallo al canjear ticket en M10 Core, respuesta: {}", coreResponse.getStatusCode());
        } catch (Exception e) {
            log.error("Error al comunicarse con M10 Core para sso-exchange", e);
        }
        
        // Si falla, redirigir al login genérico (del frontend o fallback)
        response.sendRedirect("/login");
    }
}
