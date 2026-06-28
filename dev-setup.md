# Swing Trade Development Setup Skill

This skill provides instructions for setting up and running the Swing Trade project in development mode.

## Dev Stack (Recommended)

The **Dev Stack** runs infrastructure (PostgreSQL, Redis) on the pi-node via Docker, and the Spring Boot app locally on your Mac for fast iteration.

### Quick Start
```bash
# Start everything (infra on pi-node + local Spring Boot)
./dev-stack.sh start

# Check status
./dev-stack.sh status

# Stop everything
./dev-stack.sh stop
```

### Manual Setup

#### 1. Start Infrastructure on pi-node
```bash
cd backend
docker context use pi-node
docker compose -f docker-compose.infra.yml up -d
docker context use desktop-linux
```

#### 2. Run Spring Boot Locally
```bash
cd backend/api
mvn spring-boot:run -Dspring-boot.run.profiles=local,fyers
```

#### 3. Run Frontend Locally
```bash
cd dashboard
npm install
npm run dev
```

## Architecture
- **PostgreSQL**: Running on pi-node (port 5435)
- **Redis**: Running on pi-node (port 6379)
- **Spring Boot**: Running locally on Mac (port 8080)
- **Frontend**: Running locally on Mac (port 5173)

## Verification
- Backend API: `http://localhost:8080/actuator/health`
- Frontend Dashboard: `http://localhost:5173`
- Fyers Login: `http://localhost:8080/api/fyers/login`

## Prerequisites
- Docker and Docker Compose installed
- Docker context configured for pi-node (`docker context use pi-node`)
- Java 21 installed (NOT Java 25 - Spring Boot 3.4.2 is incompatible)
- Maven installed
- Node.js and npm installed

## Known Issues & Fixes

### LocalDateTime JSON Serialization
`java.time.LocalDateTime` fields are not serializable by default Jackson. Use a custom serializer:
```java
@JsonSerialize(using = LocalDateTimeSerializer.class)
private LocalDateTime createdAt;

public static class LocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(value != null ? value.format(FORMATTER) : null);
    }
}
```

### Java Version
- MUST use Java 21 (not Java 25) - Spring Boot 3.4.2 / Lombok 1.18.38 don't support Java 25
- Set `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home`

### Hibernate Dialect
- Remove explicit `hibernate.dialect` for PostgreSQL - it's auto-detected in Hibernate 6.6+

### Remote Infrastructure
- PostgreSQL/Redis run on pi-node (`piworm.local`), not localhost
- Set `spring.data.redis.host=piworm.local` in application config

## Notes
- The `application-local.properties` profile is configured to connect to `piworm.local:5435` for PostgreSQL and `piworm.local:6379` for Redis
- Use `./dev-stack.sh infra <command>` to manage the pi-node infrastructure directly
- Use `./dev-stack.sh logs` to view infrastructure logs
- Set `spring.jpa.open-in-view: false` to avoid lazy-loading warnings
