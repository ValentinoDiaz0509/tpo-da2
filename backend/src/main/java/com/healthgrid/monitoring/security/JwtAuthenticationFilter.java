package com.healthgrid.monitoring.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * JWT Authentication Filter.
 * 
 * Intercepts all HTTP requests and validates the JWT token.
 * Token must be provided in Authorization header: "Bearer <token>"
 * Token must be issued by Module 10 (Core).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JwtTokenProvider jwtTokenProvider;
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // PASO 1: Extraer token (Bearer header o Cookie "session")
            String token = extractToken(request);
            
            if (token == null) {
                // No hay token, pasar al siguiente filtro (endpoint público)
                filterChain.doFilter(request, response);
                return;
            }
            
            // PASO 2: Validar token
            if (!jwtTokenProvider.validateToken(token)) {
                // Token inválido o expirado
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or expired token");
                return;
            }
            
            // PASO 3: Verificar expiración EXPLÍCITAMENTE
            if (jwtTokenProvider.isTokenExpired(token)) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Token has expired");
                return;
            }
            
            // PASO 4: Extraer claims (user_id y permissions)
            String userId = jwtTokenProvider.getUserIdFromToken(token);
            List<String> permissions = jwtTokenProvider.getPermissionsFromToken(token);
            
            if (userId == null) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid token claims: missing user_id");
                return;
            }
            
            // PASO 5: Crear Authentication token con permisos
            List<GrantedAuthority> authorities = permissions != null 
                ? permissions.stream().map(SimpleGrantedAuthority::new).map(auth -> (GrantedAuthority) auth).toList()
                : List.of();

            UsernamePasswordAuthenticationToken authToken = 
                new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    authorities
                );
            
            // PASO 6: Establecer en SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authToken);
            
            log.debug("✓ JWT authentication successful - UserId: {}", userId);
            
        } catch (MalformedJwtException e) {
            log.warn("⚠️ Invalid JWT signature: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                "Invalid JWT signature");
            return;
        } catch (ExpiredJwtException e) {
            log.warn("⚠️ Expired JWT token");
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                "Token has expired");
            return;
        } catch (UnsupportedJwtException e) {
            log.warn("⚠️ Unsupported JWT token: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                "Unsupported token format");
            return;
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ JWT claims string is empty: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                "Invalid token");
            return;
        } catch (Exception e) {
            log.error("❌ Unexpected error in JWT filter", e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Authentication error");
            return;
        }
        
        // Pasar al siguiente filtro
        filterChain.doFilter(request, response);
    }
    
    /**
     * Extrae el token del header Authorization (Bearer) o de la cookie "session".
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7); // Remover "Bearer "
        }
        
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("session".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Envía respuesta JSON de error estructurada.
     */
    private void sendErrorResponse(HttpServletResponse response, int status, String message) 
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> errorBody = Map.of(
            "error", message,
            "timestamp", LocalDateTime.now().toString(),
            "status", status
        );

        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(errorBody));
    }
    
    /**
     * Excluir endpoints públicos del filtro JWT.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = getPathWithoutContext(request);

        return path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/health") ||
               path.startsWith("/actuator") ||
               path.startsWith("/auth");
    }

    private String getPathWithoutContext(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }

        return path;
    }
}
