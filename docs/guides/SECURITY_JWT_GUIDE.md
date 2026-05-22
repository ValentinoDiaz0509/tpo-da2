# Spring Security & JWT Authentication Guide

## Overview

This guide explains how to use the JWT (JSON Web Token) authentication system in the Hospital Patient Monitoring Service. The system simulates **Module 10 (Core)** as the token issuer.

---

## Architecture

### Components

1. **JwtTokenProvider** (`security/JwtTokenProvider.java`)
   - Generates JWT tokens signed by Module 10 (Core)
   - Validates tokens and verifies the issuer
   - Extracts claims from valid tokens
   - Uses HS512 (HMAC-SHA512) algorithm

2. **JwtAuthenticationFilter** (`security/JwtAuthenticationFilter.java`)
   - Intercepts HTTP requests
   - Extracts Bearer token from Authorization header
   - Validates token via JwtTokenProvider
   - Sets authentication in Spring Security context
   - Returns 401 Unauthorized for invalid tokens

3. **SecurityConfig** (`security/SecurityConfig.java`)
   - Configures Spring Security for stateless JWT authentication
   - Defines protected and public endpoints
   - Sets up CORS configuration
   - Disables CSRF (not needed for stateless auth)

4. **AuthenticationController** (`controller/AuthenticationController.java`)
   - Generates tokens: `POST /api/v1/auth/token`
   - Validates tokens: `POST /api/v1/auth/validate`
   - Gets current auth info: `GET /api/v1/auth/me`

---

## Configuration

Add properties to `application.yml`:

```yaml
security:
  jwt:
    secret: ${JWT_SECRET:healthgrid-monitoring-secret-key-module10-issuer-2026}
    expiration: 86400000  # 24 hours in milliseconds
    issuer: Module10-Core
```

**For production**, set `JWT_SECRET` environment variable with a strong secret key.

---

## API Endpoints

### 1. Generate Token (Module 10 Issues)

**Request:**
```bash
POST http://localhost:8080/api/v1/auth/token
Content-Type: application/json

{
  "module": "Monitoring",
  "userId": "system_user"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "expiresIn": 86400,
  "module": "Monitoring",
  "userId": "system_user",
  "issuer": "Module10-Core"
}
```

### 2. Validate Token

**Request:**
```bash
POST http://localhost:8080/api/v1/auth/validate
Authorization: Bearer eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...
```

**Response:**
```json
{
  "valid": true,
  "message": "Token is valid and issued by Module 10",
  "module": "Monitoring",
  "userId": "system_user"
}
```

### 3. Get Current Authentication Info

**Request:**
```bash
GET http://localhost:8080/api/v1/auth/me
Authorization: Bearer eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...
```

**Response:**
```json
{
  "module": "Monitoring",
  "userId": "system_user",
  "authenticated": true,
  "issuer": "Module10-Core"
}
```

---

## Protected Endpoints

These endpoints **require a valid JWT token:**

- `GET/POST /api/v1/patients/**` - Patient management
- `GET/POST /api/v1/readings/**` - Telemetry readings
- `GET/POST /api/v1/rules/**` - Rules management
- `GET/POST /api/v1/alerts/**` - Alerts management
- `/ws` - WebSocket endpoint

### Making Authenticated Requests

**Include the Authorization header:**

```bash
curl -X GET http://localhost:8080/api/v1/patients \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## Public Endpoints

These endpoints **do NOT require authentication:**

- `/swagger-ui/**` - Swagger UI documentation
- `/v3/api-docs/**` - OpenAPI specification
- `/health` - Health check
- `/actuator/**` - Actuator endpoints
- `/api/v1/auth/**` - Authentication endpoints (token generation/validation)

---

## JWT Token Structure

### Header
```json
{
  "alg": "HS512",
  "typ": "JWT"
}
```

### Payload
```json
{
  "iss": "Module10-Core",
  "sub": "Monitoring:system_user",
  "module": "Monitoring",
  "userId": "system_user",
  "iat": 1710999268,
  "exp": 1711085668
}
```

- **iss** (issuer): "Module10-Core" (validates this is from Module 10)
- **sub** (subject): "{module}:{userId}" format
- **module**: Module identifier
- **userId**: User/system identifier
- **iat** (issued at): Token creation timestamp
- **exp** (expiration): When token expires (24 hours later)

### Signature
```
HMAC-SHA512(base64(header) + "." + base64(payload), secret_key)
```

---

## Frontend Integration (React Example)

### 1. Get Token from Module 10

```javascript
async function getToken() {
  const response = await fetch('http://localhost:8080/api/v1/auth/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      module: 'Monitoring',
      userId: 'frontend_user'
    })
  });
  
  const data = await response.json();
  localStorage.setItem('jwt_token', data.token);
  return data.token;
}
```

### 2. Send Token with API Requests

```javascript
async function makeAuthenticatedRequest(url, options = {}) {
  const token = localStorage.getItem('jwt_token');
  
  const headers = {
    ...options.headers,
    'Authorization': `Bearer ${token}`
  };
  
  return fetch(url, { ...options, headers });
}

// Example: Get patients
const patients = await makeAuthenticatedRequest('/api/v1/patients');
```

### 3. Connect to WebSocket with JWT

```javascript
function connectWebSocket() {
  const token = localStorage.getItem('jwt_token');
  
  const stompClient = new StompJs.Client({
    brokerURL: 'ws://localhost:8080/ws',
    connectHeaders: {
      'Authorization': `Bearer ${token}`
    }
  });
  
  stompClient.onConnect = () => {
    // Subscribe to updates
    stompClient.subscribe('/topic/monitoring/patient123', 
      (message) => console.log(message.body)
    );
  };
  
  stompClient.activate();
}
```

---

## Error Handling

### 401 Unauthorized
**Cause:** Missing or invalid token
**Response:**
```json
{
  "error": "Invalid or expired token"
}
```

**Solution:**
1. Check Authorization header format: `Bearer {token}`
2. Verify token hasn't expired
3. Get a new token from `/api/v1/auth/token`

### 403 Forbidden
**Cause:** Token valid but insufficient permissions (future feature)
**Solution:** Check module claims and role requirements

---

## Security Best Practices

### 1. Secret Key Management
- **Development:** Use default secret (in `application.yml`)
- **Production:** 
  - Set `JWT_SECRET` environment variable with strong key (minimum 256 bits)
  - Never commit secrets to version control
  - Use Azure Key Vault or similar service

```bash
# Generate strong secret (Linux/Mac)
openssl rand -base64 32
```

### 2. Token Expiration
- Current: 24 hours (86400000 ms)
- Consider shorter times for high-security scenarios (1 hour)
- Implement refresh token mechanism for long-lived sessions

### 3. HTTPS Only
- Always use HTTPS in production
- Tokens contain sensitive claims and should not be transmitted in plaintext

### 4. Token Storage (Frontend)
- **Secure:** HTTP-Only Cookies (preferred, immune to XSS)
- **Acceptable:** SessionStorage (cleared on browser close)
- **Avoid:** LocalStorage (vulnerable to XSS attacks)

### 5. CORS Configuration
- Current: `localhost:3000` and `localhost:8080`
- Update for production domains:

```yaml
# In SecurityConfig.java
        if (corsConfig != null) {
            corsConfig.setAllowedOrigins(Arrays.asList(
                "https://yourdomain.com",
                "https://api.yourdomain.com"
            ));
        }
```

---

## Testing with cURL

### Generate Token
```bash
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"module":"Monitoring","userId":"test_user"}'
```

### Access Protected Endpoint
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"module":"Monitoring","userId":"test_user"}' | jq -r '.token')

curl -X GET http://localhost:8080/api/v1/patients \
  -H "Authorization: Bearer $TOKEN"
```

### Validate Token
```bash
curl -X POST http://localhost:8080/api/v1/auth/validate \
  -H "Authorization: Bearer $TOKEN"
```

---

## Troubleshooting

### Issue: "Invalid JWT signature"
- **Cause:** Token signed with different secret key
- **Solution:** Ensure same `JWT_SECRET` across all instances

### Issue: "Expired JWT token"
- **Cause:** Token older than 24 hours
- **Solution:** Generate a new token

### Issue: "Unsupported JWT token"
- **Cause:** Token format is invalid
- **Solution:** Ensure token includes "Bearer " prefix in Authorization header

### Issue: CORS Error in Frontend
- **Cause:** Frontend origin not in allowed list
- **Solution:** Update CORS configuration in SecurityConfig.java

---

## Architecture Diagram

```
┌─────────────────┐
│     Frontend    │
│    (React)      │
└────────┬────────┘
         │
         │ 1. POST /api/v1/auth/token (module, userId)
         │ {"module": "Monitoring", "userId": "user1"}
         ▼
┌─────────────────────────────────────┐
│  AuthenticationController          │
│  ✓ generateToken()                  │
│  ✓ validateToken()                  │
│  ✓ getCurrentAuthInfo()             │
└────────┬────────────────────────────┘
         │
         │ 2. Generate JWT (HS512 signed)
         │    Issuer: Module10-Core
         ▼
┌─────────────────────────────────────┐
│    JwtTokenProvider                 │
│    ✓ generateToken(module, userId)  │
│    ✓ validateToken(token)           │
│    ✓ getClaimsFromToken(token)      │
└────────┬────────────────────────────┘
         │
         │ 3. Return token to frontend
         │    {"token": "eyJ...", "expiresIn": 86400}
         │
         │ 4. Frontend stores token in localStorage/cookies
         │
         │ 5. Request with Authorization header
         │    Authorization: Bearer eyJ...
         ▼
┌─────────────────────────────────────┐
│     Incoming HTTP Request           │
│     Authorization: Bearer eyJ...    │
└────────┬────────────────────────────┘
         │
         │ 6. Filter intercepts request
         ▼
┌─────────────────────────────────────┐
│   JwtAuthenticationFilter           │
│   ✓ Extract Bearer token            │
│   ✓ Validate via JwtTokenProvider   │
│   ✓ Set SecurityContext             │
└────────┬────────────────────────────┘
         │
         │ 7. Token valid?
         │
    ┌────┴────┐
    │          │
   YES        NO
    │          │
    │          └──► Return 401 Unauthorized
    │
    ▼
┌─────────────────────────────────────┐
│    Protected Endpoint               │
│    /api/v1/patients/**              │
│    /api/v1/readings/**              │
│    /api/v1/rules/**                 │
│    /api/v1/alerts/**                │
│    /ws                              │
└─────────────────────────────────────┘
```

---

## Module 10 Integration

This system simulates **Module 10 (Core)** as the JWT issuer. In production:

1. **Module 10** generates tokens with issuer claim: "Module10-Core"
2. **This Service** validates tokens only if issuer == "Module10-Core"
3. **Other Services** can validate claims but reject tokens from different issuers

This ensures:
- Only Module 10-issued tokens are accepted
- Token forgery is prevented
- Multi-module communication is secured

---

## Future Enhancements

- [ ] Refresh token mechanism (short-lived access + long-lived refresh)
- [ ] Token revocation/blacklist (logout functionality)
- [ ] Role-Based Access Control (RBAC) with fine-grained permissions
- [ ] Audit logging for authentication events
- [ ] OAuth2/OIDC support
- [ ] Multi-tenancy support with tenant claims
- [ ] Rate limiting on token generation
- [ ] Asymmetric signing (RS256) instead of HS512

---

## References

- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [JJWT Library](https://github.com/jwtk/jjwt)
- [JWT.io Debugger](https://jwt.io/)
- [RFC 7519 - JSON Web Token](https://tools.ietf.org/html/rfc7519)
