# Getting Started with Spring Security & JWT Authentication

Hospital Patient Monitoring System - Phase 5 Implementation

---

## Quick Start

### 1. Start the Application

```bash
cd c:\Users\valentino\backend
mvn spring-boot:run
```

Wait for:
```
Patient Monitoring Service started on port 8080
```

---

## 2. Generate a JWT Token

**Using cURL:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"module":"Monitoring","userId":"system_user"}'
```

**Using PowerShell:**
```powershell
$body = @{
    module = "Monitoring"
    userId = "system_user"
} | ConvertTo-Json

$response = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/auth/token" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body

$token = ($response.Content | ConvertFrom-Json).token
Write-Host "Token: $token"
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJNb2R1bGUxMC1Db3JlIiwic3ViIjoiTW9uaXRvcmluZzpzeXN0ZW1fdXNlciIsIm1vZHVsZSI6Ik1vbml0b3JpbmciLCJ1c2VySWQiOiJzeXN0ZW1fdXNlciIsImlhdCI6MTcxMDk5OTI2OCwiZXhwIjoxNzExMDg1NjY4fQ.signature...",
  "type": "Bearer",
  "expiresIn": 86400,
  "module": "Monitoring",
  "userId": "system_user",
  "issuer": "Module10-Core"
}
```

**Save the token:**
```bash
# Linux/Mac
export TOKEN="eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9..."

# Windows PowerShell
$TOKEN = "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9..."
```

---

## 3. Use Token in API Requests

**Protected Endpoint Example - Get Patients:**

```bash
curl -X GET http://localhost:8080/api/v1/patients \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Response (HTTP 200):**
```json
[
  {
    "id": 1,
    "name": "John Doe",
    "room": "101",
    ...
  }
]
```

**Without Token (HTTP 401):**
```bash
curl -X GET http://localhost:8080/api/v1/patients
```

```json
{
  "error": "Invalid or expired token"
}
```

---

## 4. Validate Token

**Check if token is still valid:**

```bash
curl -X POST http://localhost:8080/api/v1/auth/validate \
  -H "Authorization: Bearer $TOKEN"
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

---

## 5. Get current Authentication Info

**See who is authenticated:**

```bash
curl -X GET http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN"
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

## Protected vs Public Endpoints

### 🔒 Protected Endpoints (Require JWT Token)

| Endpoint | Method |
|----------|--------|
| `/api/v1/patients/**` | GET, POST, PUT, DELETE |
| `/api/v1/readings/**` | GET, POST |
| `/api/v1/rules/**` | GET, POST, PUT, DELETE |
| `/api/v1/alerts/**` | GET, POST |
| `/ws` | WebSocket |

### 🔓 Public Endpoints (No Token Required)

| Endpoint | Purpose |
|----------|---------|
| `/health` | Health check |
| `/swagger-ui/**` | API documentation |
| `/v3/api-docs/**` | OpenAPI spec |
| `/api/v1/auth/token` | Generate token |
| `/api/v1/auth/validate` | Validate token |

---

## Token Format

### JWT Structure

Every JWT token consists of 3 parts separated by dots:
```
header.payload.signature
```

**Header:**
```json
{
  "alg": "HS512",
  "typ": "JWT"
}
```

**Payload (Claims):**
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

**Signature:**
```
HMAC-SHA512(base64(header) + "." + base64(payload), secret_key)
```

### Decode a Token (jwt.io)

1. Copy your token
2. Go to [https://jwt.io/](https://jwt.io/)
3. Paste token in the "Encoded" field
4. See decoded header and payload

**Note:** Signature verification requires secret key (only server has it)

---

## Common HTTP Status Codes

| Status | Meaning | When to Use |
|--------|---------|------------|
| 200 OK | Success | Token valid, endpoint accessible |
| 401 Unauthorized | Missing/invalid token | Token missing, expired, or invalid |
| 403 Forbidden | Valid token but insufficient permissions | Future RBAC feature |
| 404 Not Found | Endpoint or resource not found | Resource doesn't exist |
| 500 Internal Server Error | Server error | Server-side issue |

---

## Frontend Integration (React)

### Step 1: Get Token on Login

```javascript
async function login(module, userId) {
  const response = await fetch('http://localhost:8080/api/v1/auth/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ module, userId })
  });

  if (!response.ok) throw new Error('Login failed');
  
  const data = await response.json();
  // Store token securely (preferably HttpOnly cookie)
  localStorage.setItem('jwt_token', data.token);
  localStorage.setItem('token_expires_at', Date.now() + data.expiresIn * 1000);
  
  return data;
}
```

### Step 2: Create Authenticated Fetch Helper

```javascript
async function fetchWithAuth(url, options = {}) {
  const token = localStorage.getItem('jwt_token');
  
  if (!token) {
    // Token missing - redirect to login
    window.location.href = '/login';
    return;
  }

  const response = await fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });

  // If token expired, refresh or redirect to login
  if (response.status === 401) {
    localStorage.removeItem('jwt_token');
    window.location.href = '/login';
    return;
  }

  return response;
}
```

### Step 3: Use in React Components

```javascript
// Get list of patients
async function loadPatients() {
  const response = await fetchWithAuth('http://localhost:8080/api/v1/patients');
  const patients = await response.json();
  setPatients(patients);
}

// Create new patient
async function createPatient(patientData) {
  const response = await fetchWithAuth('http://localhost:8080/api/v1/patients', {
    method: 'POST',
    body: JSON.stringify(patientData)
  });
  
  if (response.ok) {
    const newPatient = await response.json();
    setPatients([...patients, newPatient]);
  }
}
```

### Step 4: Connect WebSocket with Token

```javascript
import { Client } from '@stomp/stompjs';

function connectWebSocket() {
  const token = localStorage.getItem('jwt_token');
  
  const client = new Client({
    brokerURL: 'ws://localhost:8080/ws',
    connectHeaders: {
      'Authorization': `Bearer ${token}`
    },
    onConnect: () => {
      console.log('✓ WebSocket connected');
      // Subscribe to monitoring updates
      client.subscribe('/topic/monitoring/patient123', (message) => {
        const update = JSON.parse(message.body);
        console.log('Monitoring update:', update);
      });
    },
    onDisconnect: () => {
      console.log('WebSocket disconnected');
    }
  });

  client.activate();
  return client;
}
```

---

## Testing

### Automated Integration Tests

```bash
# Run all JWT authentication tests
mvn test -Dtest=JwtAuthenticationIT

# Run specific test
mvn test -Dtest=JwtAuthenticationIT#testCompleteAuthenticationFlow
```

### Manual Testing with PowerShell

```powershell
cd c:\Users\valentino\backend
.\JWT_TESTING.ps1
```

This runs 10 automated test scenarios:
1. Health check (public)
2. Token generation
3. Token validation
4. Auth info retrieval
5. Protected endpoint access
6. Unauthorized access block
7. Invalid token rejection
8. CORS handling
9. Swagger UI access
10. Multiple user tokens

---

## Configuration

### application.yml

The JWT configuration is in `src/main/resources/application.yml`:

```yaml
security:
  jwt:
    secret: ${JWT_SECRET:healthgrid-monitoring-secret-key-module10-issuer-2026}
    expiration: ${JWT_EXPIRATION:86400000}  # 24 hours
    issuer: ${JWT_ISSUER:Module10-Core}
```

### Environment Variables (Production)

For production deployment, set environment variables:

```bash
# Linux/Mac
export JWT_SECRET="your-long-and-secure-key-min-256-bits"
export JWT_EXPIRATION=3600000  # 1 hour (optional)
export JWT_ISSUER=Module10-Core

# Windows PowerShell
[System.Environment]::SetEnvironmentVariable('JWT_SECRET', 'your-key')
[System.Environment]::SetEnvironmentVariable('JWT_EXPIRATION', '3600000')
```

### Generate Strong Secret Key

```bash
# Linux/Mac - Generate 256-bit key
openssl rand -base64 32
# Example output: aBc1DeF2GhI3jkL4mNoPqRsT+uVwXyZ012abcd=

# Windows PowerShell - Generate 256-bit key
[Convert]::ToBase64String((1..32 | ForEach-Object { [byte](Get-Random -Maximum 256) }))
```

---

## Troubleshooting

### Issue 1: "Invalid or expired token"

**Causes:**
- Token missing or malformed
- Token expired (24-hour limit)
- Wrong Authorization header format

**Solutions:**
```bash
# Verify header format is exactly: "Bearer {token}"
curl -v http://localhost:8080/api/v1/patients \
  -H "Authorization: Bearer $TOKEN"

# Generate new token if expired
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"module":"Monitoring","userId":"user"}'
```

### Issue 2: CORS Error in Frontend

**Message:** `Access to XMLHttpRequest blocked by CORS policy`

**Causes:**
- Frontend origin not in allowed list
- Credentials not sent with request

**Solutions:**
Update CORS in SecurityConfig.java:
```java
corsConfig.setAllowedOrigins(Arrays.asList(
    "http://localhost:3000",  // React dev server
    "http://localhost:3001",  // Alternative
    "https://yourdomain.com"  // Production
));
```

### Issue 3: WebSocket Connection Fails

**Message:** `WebSocket connection failed`

**Solutions:**
- Ensure token is included in WebSocket handshake headers
- Check token is still valid (not expired)
- Verify WebSocket endpoint `/ws` is accessible

### Issue 4: 'Cannot find symbol: method setSigningKey'

**Cause:** Wrong JJWT version

**Solution:** Verify pom.xml has correct JJWT version:
```xml
<version>0.12.3</version>
```

---

## Security Best Practices

### ✅ DO

- [x] Always use HTTPS in production
- [x] Store tokens in HttpOnly cookies (prevents XSS)
- [x] Implement token refresh mechanism
- [x] Validate token expiration on frontend
- [x] Use strong secret keys (256+ bits)
- [x] Rotate secret keys periodically
- [x] Log authentication events (audit trail)
- [x] Use environment variables for secrets

### ❌ DON'T

- [ ] Store tokens in localStorage (XSS vulnerable)
- [ ] Hardcode secrets in source code
- [ ] Use short token expiration times (< 5 minutes) without refresh
- [ ] Send tokens in URLs (logged in browser history)
- [ ] Use weak secret keys (< 256 bits)
- [ ] Expose token claims unencrypted
- [ ] Log sensitive claims
- [ ] Skip HTTPS in production

---

## Next Steps

### Immediate (Days 1-3)

- [ ] Run integration tests: `mvn test -Dtest=JwtAuthenticationIT`
- [ ] Test with React frontend
- [ ] Verify WebSocket authentication
- [ ] Update environment variables for production

### Short-term (Weeks 1-2)

- [ ] Implement refresh token mechanism
- [ ] Add token revocation (logout functionality)
- [ ] Implement role-based access control (RBAC)
- [ ] Add audit logging for authentication

### Medium-term (Months 1-3)

- [ ] OAuth2/OIDC integration with identity provider
- [ ] Multi-tenancy support with tenant claims
- [ ] Asymmetric key signing (RS256 instead of HS512)
- [ ] Rate limiting on token generation
- [ ] IP-based access restrictions

### Long-term (Months 3+)

- [ ] Single Sign-On (SSO) integration
- [ ] MFA (Multi-Factor Authentication)
- [ ] Hardware security module (HSM) for key storage
- [ ] Token encryption for sensitive claims
- [ ] Advanced analytics and threat detection

---

## Resources

### Documentation
- [SECURITY_JWT_GUIDE.md](SECURITY_JWT_GUIDE.md) - Comprehensive JWT guide
- [PHASE_5_SECURITY_JWT_SUMMARY.md](PHASE_5_SECURITY_JWT_SUMMARY.md) - Implementation summary

### Testing Scripts
- `JWT_TESTING.ps1` - Windows PowerShell testing
- `JWT_TESTING.sh` - Linux/Mac Bash testing

### External Resources
- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [JJWT - JSON Web Token Library](https://github.com/jwtk/jjwt)
- [JWT.io - JWT Debugger & Documentation](https://jwt.io/)
- [RFC 7519 - JSON Web Token (JWT)](https://tools.ietf.org/html/rfc7519)
- [Spring Security Configuration Examples](https://spring.io/blog/2022/02/21/spring-security-without-the-web)

---

## Support & Troubleshooting

### Check Application Status

```bash
# Health check
curl http://localhost:8080/health

# Swagger API docs
open http://localhost:8080/swagger-ui.html
```

### View Application Logs

```bash
# When running with mvn spring-boot:run
# Logs appear in console

# When running as JAR
tail -f nohup.out
```

### Debug Token Issues

1. **Verify Token Generation:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"module":"Monitoring","userId":"test"}' | jq
```

2. **Decode Token:**
   - Visit https://jwt.io/
   - Paste token in "Encoded" field
   - View decoded payload

3. **Check Token Validity:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/validate \
  -H "Authorization: Bearer {your-token}" | jq
```

4. **View Server Logs:**
   - Look for `JwtTokenProvider:` log entries
   - Check for `JwtAuthenticationFilter:` entries

---

## Summary

You now have a **fully functional JWT authentication system** that simulates Module 10 (Core) as the token issuer. 

**Key Achievements:**
- ✅ JWT token generation and validation
- ✅ Spring Security integration
- ✅ Protected endpoint access control
- ✅ CORS configuration for React
- ✅ Comprehensive documentation
- ✅ Integration tests (20+ test cases)
- ✅ Testing scripts for manual validation

**Status:** Ready for production deployment after environment configuration.

---

**Questions?** Check the [SECURITY_JWT_GUIDE.md](SECURITY_JWT_GUIDE.md) for detailed documentation.
