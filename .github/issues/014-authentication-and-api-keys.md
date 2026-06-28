# feat(api): add authentication and API key management

**Labels:** `enhancement` `tier-3-infra` `api` `security`
**Estimated effort:** 3-5 days

## Problem

All API endpoints are publicly accessible with no authentication. Anyone who knows the server IP can trigger trades, modify settings, or access position data.

## Proposed Solution

Implement JWT-based authentication with API key support. All endpoints except health checks require authentication.

## Architecture

```
Client Request
    ├── No auth → 401 Unauthorized
    ├── Invalid token → 401 Unauthorized
    ├── Expired token → 401 Unauthorized
    └── Valid token → Process request
        ├── Insufficient permissions → 403 Forbidden
        └── Authorized → 200 OK
```

## Authentication Methods

### 1. JWT Bearer Token (primary)
- Generated via `/api/auth/login`
- TTL: 24 hours
- Refresh token TTL: 7 days
- Stored in HTTP-only cookie

### 2. API Key (programmatic)
- Generated via admin panel
- Passed in `X-API-Key` header
- Can be scoped (read-only, trade, admin)
- No expiry (until manually revoked)

## API Endpoints

```
POST /api/auth/login              - Login with credentials → JWT + refresh token
POST /api/auth/refresh            - Refresh expired JWT → new JWT
POST /api/auth/logout             - Invalidate refresh token
GET  /api/auth/keys               - List API keys (admin)
POST /api/auth/keys               - Generate new API key (admin)
DELETE /api/auth/keys/{keyId}     - Revoke API key (admin)
```

## Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // API, use CORS instead
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/health/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                // Authenticated endpoints
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/trades/**").hasRole("TRADER")
                .requestMatchers("/api/**").hasAnyRole("TRADER", "ADMIN", "VIEWER")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

## JWT Implementation

```java
@Component
public class JwtService {

    private final String secretKey = System.getenv("JWT_SECRET_KEY");
    private final int accessTokenExpiration = 86400;  // 24h
    private final int refreshTokenExpiration = 604800; // 7d

    public String generateAccessToken(String username, List<String> roles) { ... }
    public String generateRefreshToken(String username) { ... }
    public String extractUsername(String token) { ... }
    public boolean isTokenValid(String token, String username) { ... }
    public Role extractRole(String token) { ... }
}
```

## API Key Implementation

### Database

```sql
CREATE TABLE api_keys (
    id SERIAL PRIMARY KEY,
    key_hash VARCHAR(64) NOT NULL,      -- SHA-256 hash of actual key
    key_prefix VARCHAR(8) NOT NULL,     -- First 8 chars for display (e.g., "abc12345")
    name VARCHAR(100) NOT NULL,         -- Human-readable name
    permissions VARCHAR(50) NOT NULL,   -- READ, TRADE, ADMIN
    user_id VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP,
    UNIQUE(key_hash)
);
```

### API Key Generation

```java
public class ApiKeyGenerator {
    public static String generate() {
        // 32-char random alphanumeric string
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encode(bytes);
    }
}
```

## Files to Create

- `api/src/main/java/com/swingtrade/api/config/SecurityConfig.java` - Spring Security config
- `api/src/main/java/com/swingtrade/api/config/JwtService.java` - JWT token management
- `api/src/main/java/com/swingtrade/api/filter/JwtAuthFilter.java` - JWT request filter
- `api/src/main/java/com/swingtrade/api/filter/ApiKeyAuthFilter.java` - API key filter
- `api/src/main/java/com/swingtrade/api/entity/ApiUserEntity.java` - User entity
- `api/src/main/java/com/swingtrade/api/entity/ApiKeyEntity.java` - API key entity
- `api/src/main/java/com/swingtrade/api/dto/LoginRequest.java`
- `api/src/main/java/com/swingtrade/api/dto/LoginResponse.java`
- `api/src/main/java/com/swingtrade/api/dto/ApiKeyResponse.java`
- `api/src/main/java/com/swingtrade/api/service/AuthService.java`
- `api/src/main/java/com/swingtrade/api/controller/AuthController.java`
- `data/src/main/resources/db/migration/V8__add_auth_tables.sql`
- `api/src/main/java/com/swingtrade/api/repository/ApiUserRepository.java`
- `api/src/main/java/com/swingtrade/api/repository/ApiKeyRepository.java`

## Files to Modify

- `api/pom.xml` - Add `spring-boot-starter-security`, `jjwt` dependency
- All controllers - Add `@PreAuthorize` annotations
- `application.properties` - Add JWT secret env var reference

## Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
```

## Acceptance Criteria

- [ ] JWT authentication implemented with access + refresh tokens
- [ ] API key authentication via `X-API-Key` header
- [ ] Health check endpoints remain public
- [ ] Auth endpoints (login, refresh) are public
- [ ] Admin endpoints require ADMIN role
- [ ] Trade endpoints require TRADER role or higher
- [ ] API keys are hashed (SHA-256) in database
- [ ] API key generation uses cryptographically secure random
- [ ] All other endpoints return 401 without valid auth
- [ ] Default admin user created on startup (seed)
- [ ] JWT secret loaded from environment variable
- [ ] Unit tests for JwtService and auth filters
- [ ] Integration tests for protected endpoints
- [ ] Code coverage >= 80%

## Notes

- Default credentials: `admin` / auto-generated password (logged on startup)
- In production, require password change on first login
- Consider adding rate limiting on login endpoint (future)
- CORS must be configured for frontend integration
- API keys should only be shown once at creation time
