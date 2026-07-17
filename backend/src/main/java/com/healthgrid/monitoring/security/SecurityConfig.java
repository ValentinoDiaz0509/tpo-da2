package com.healthgrid.monitoring.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security Configuration for JWT-based authentication.
 * 
 * Configured for stateless JWT authentication.
 * All requests (except auth endpoints) require valid JWT token from Module 10 (Core).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origin-patterns:http://localhost:3000,http://localhost:8080,http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOriginPatterns;

    /**
     * Configure HTTP security with JWT authentication.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()  // Disable CSRF for stateless JWT auth
            .cors()
            .and()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)  // Stateless auth
            .and()
            .authorizeHttpRequests(authz -> authz
                // Public endpoints (no auth required)
                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/openapi.json",
                    "/openapi.json/**",
                    "/openapi/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/v3/api-docs/swagger-config",
                    "/health",
                    "/actuator/**"
                ).permitAll()
                .requestMatchers("/auth/**").permitAll()  // Auth endpoints
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/ws").permitAll()
                
                // Protected endpoints (require JWT)
                .requestMatchers("/patients/**").authenticated()
                .requestMatchers("/telemetry/**").authenticated()
                .requestMatchers("/rules/**").authenticated()
                .requestMatchers("/alerts/**").authenticated()
                
                // Default: any other request requires authentication
                .anyRequest().authenticated()
            )
            // Add JWT filter before UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(
            Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList()
        );
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Password encoder for user credentials (if needed in future).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
