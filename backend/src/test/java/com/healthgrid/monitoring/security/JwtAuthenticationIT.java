package com.healthgrid.monitoring.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthgrid.monitoring.dto.auth.TokenRequest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.sqs.SqsClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for JWT authentication flow.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Slf4j
class JwtAuthenticationIT {

    private static final String JWT_SECRET =
        "healthgrid-monitoring-secret-key-module10-issuer-2026-hs512-secure-key-abcdef1234567890";
    private static final String JWT_ISSUER = "Module10-Core";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SqsClient sqsClient;

    private String validToken;
    private final String invalidToken = "invalid.token.here";

    @BeforeEach
    void setup() {
        validToken = jwtTokenProvider.generateToken("Monitoring", "test_user");
    }

    @Test
    void testGenerateToken_Success() throws Exception {
        TokenRequest request = new TokenRequest("Monitoring", "system_user");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token", notNullValue()))
            .andExpect(jsonPath("$.type", is("Bearer")))
            .andExpect(jsonPath("$.expiresIn", is(86400)))
            .andExpect(jsonPath("$.module", is("Monitoring")))
            .andExpect(jsonPath("$.userId", is("system_user")))
            .andExpect(jsonPath("$.issuer", is("Module10-Core")))
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("token");
    }

    @Test
    void testGenerateToken_WithDifferentModule() throws Exception {
        TokenRequest request = new TokenRequest("PatientService", "module_5");

        mockMvc.perform(post("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.module", is("PatientService")))
            .andExpect(jsonPath("$.userId", is("module_5")));
    }

    @Test
    void testValidateToken_ValidToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, bearer(validToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid", is(true)))
            .andExpect(jsonPath("$.message", notNullValue()))
            .andExpect(jsonPath("$.module", is("Monitoring")))
            .andExpect(jsonPath("$.userId", is("test_user")));
    }

    @Test
    void testValidateToken_InvalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, bearer(invalidToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid", is(false)))
            .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void testValidateToken_MissingAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/validate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid", is(false)))
            .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void testValidateToken_BadAuthorizationFormat() throws Exception {
        mockMvc.perform(post("/api/v1/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, "Basic some_token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid", is(false)));
    }

    @Test
    void testProtectedEndpoint_WithValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(validToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.module", is("anonymousUser")))
            .andExpect(jsonPath("$.userId", is("UNKNOWN")))
            .andExpect(jsonPath("$.authenticated", is(true)))
            .andExpect(jsonPath("$.issuer", is("Module10-Core")));
    }

    @Test
    void testProtectedEndpoint_WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.module", is("anonymousUser")));
    }

    @Test
    void testProtectedEndpoint_WithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(invalidToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.module", is("anonymousUser")));
    }

    @Test
    void testPublicEndpoint_HealthCheck() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    void testPublicEndpoint_SwaggerUI() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk());
    }

    @Test
    void testJwtTokenProvider_GenerateToken() {
        String token = jwtTokenProvider.generateToken("TestModule", "test_user");
        assertThat(token).isNotBlank();
        assertThat(token).contains(".");
    }

    @Test
    void testJwtTokenProvider_ValidateToken() {
        String token = jwtTokenProvider.generateToken("TestModule", "test_user");
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void testJwtTokenProvider_InvalidateToken() {
        assertThat(jwtTokenProvider.validateToken(invalidToken)).isFalse();
    }

    @Test
    void testJwtTokenProvider_ExtractClaims() {
        String token = jwtTokenProvider.generateToken("TestModule", "test_user");

        assertThat(jwtTokenProvider.getModuleFromToken(token)).isEqualTo("TestModule");
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo("test_user");
        assertThat(jwtTokenProvider.getSubjectFromToken(token)).isEqualTo("TestModule:test_user");
    }

    @Test
    void testJwtTokenProvider_IsTokenExpired() {
        String token = jwtTokenProvider.generateToken("TestModule", "test_user");
        assertThat(jwtTokenProvider.isTokenExpired(token)).isFalse();
    }

    @Test
    void testCompleteAuthenticationFlow() throws Exception {
        TokenRequest tokenRequest = new TokenRequest("Monitoring", "integration_test");

        MvcResult generateResult = mockMvc.perform(post("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token", notNullValue()))
            .andReturn();

        String responseBody = generateResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).get("token").asText();

        mockMvc.perform(post("/api/v1/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid", is(true)));

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.module", is("anonymousUser")));

        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.module", is("anonymousUser")));
    }

    @Test
    void testSecurityHeaders_CORS() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(validToken))
                .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
            .andExpect(status().isOk());
    }

    @Test
    void testTokenInvalid() {
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/patients",
            HttpMethod.GET,
            new HttpEntity<>(headers(bearer(invalidToken))),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid or expired token");
    }

    @Test
    void testTokenMissingBearerPrefix() {
        String token = jwtTokenProvider.generateToken("Monitoring", "test_user");

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/patients",
            HttpMethod.GET,
            new HttpEntity<>(headers(token)),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testTokenExpired() {
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/patients",
            HttpMethod.GET,
            new HttpEntity<>(headers(bearer(generateExpiredToken()))),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("expired");
    }

    @Test
    void testPublicEndpointNoToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testProtectedEndpointNoToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/patients", String.class);
        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    private HttpHeaders headers(String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        return headers;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String generateExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();

        return Jwts.builder()
            .issuer(JWT_ISSUER)
            .subject("Monitoring:test_user")
            .claim("module", "Monitoring")
            .claim("userId", "test_user")
            .issuedAt(new Date(now.getTime() - 60_000))
            .expiration(new Date(now.getTime() - 1_000))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact();
    }
}
