# feat(ci): add GitHub Actions CI pipeline

**Labels:** `enhancement` `tier-3-infra` `ci-cd`
**Estimated effort:** 1-2 days

## Problem

There is no CI/CD pipeline. Code is built and tested locally with no automated quality gates before merging.

## Proposed Solution

Create a GitHub Actions workflow that builds all Maven modules, runs tests, checks coverage, builds the Vue dashboard, and runs E2E tests.

## Workflow: `.github/workflows/ci.yml`

```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

env:
  JAVA_VERSION: 21
  NODE_VERSION: 20
  MAVEN_OPTS: -Xmx2g

jobs:
  backend:
    name: Backend (Java ${{ env.JAVA_VERSION }})
    runs-on: ubuntu-latest
    services:
      postgres:
        image: timescale/timescaledb:latest
        env:
          POSTGRES_USER: swingtrade_user
          POSTGRES_PASSWORD: swingtrade_password
          POSTGRES_DB: swingtrade_db_test
        ports: ["5432:5432"]
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
      redis:
        image: redis:alpine
        ports: ["6379:6379"]
        options: --health-cmd "redis-cli ping" --health-interval 10s --health-timeouts 5s --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK ${{ env.JAVA_VERSION }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: temurin
          cache: maven

      - name: Build and test
        run: |
          mvn clean verify -B \
            -Dspring.profiles.active=test \
            -Dspring.datasource.url=jdbc:postgresql://localhost:5432/swingtrade_db_test \
            -Dspring.datasource.username=swingtrade_user \
            -Dspring.datasource.password=swingtrade_password \
            -Dspring.redis.host=localhost \
            -Dspring.redis.port=6379

      - name: Upload coverage report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: jacoco-report
          path: */target/site/jacoco/index.html

  frontend:
    name: Frontend (Node ${{ env.NODE_VERSION }})
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: ${{ env.NODE_VERSION }}
          cache: 'npm'
          cache-dependency-path: swing-trade-dashboard/package-lock.json

      - name: Install dependencies
        run: npm ci
        working-directory: swing-trade-dashboard

      - name: Run lint
        run: npm run lint
        working-directory: swing-trade-dashboard

      - name: Run tests
        run: npm run test
        working-directory: swing-trade-dashboard

      - name: Build
        run: npm run build
        working-directory: swing-trade-dashboard

  security:
    name: Security Scan
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Run OWASP dependency check
        uses: dependency-check/Dependency-Check-action@latest
        with:
          project: swing-trade
          path: '.'
          format: XML

      - name: Check for secrets
        uses: trufflesecurity/trufflehog@main
        with:
          extra_args: --only-passed

  notify:
    name: Notify
    runs-on: ubuntu-latest
    needs: [backend, frontend, security]
    if: always()
    steps:
      - name: Status
        run: |
          echo "Backend: ${{ needs.backend.result }}"
          echo "Frontend: ${{ needs.frontend.result }}"
          echo "Security: ${{ needs.security.result }}"
```

## Files to Create

- `.github/workflows/ci.yml` - Main CI workflow
- `.github/workflows/deploy.yml` - Deployment workflow (future)
- `api/src/test/resources/application-test.properties` - Test profile config
- `data/src/test/resources/application-test.properties` - Test profile config

## Test Profile Requirements

Create `application-test.properties` in each module that needs a database:
```properties
spring.datasource.url=jdbc:postgresql://${POSTGRES_HOST:localhost}:5432/swingtrade_db_test
spring.datasource.username=swingtrade_user
spring.datasource.password=swingtrade_password
spring.flyway.baseline-on-migrate=true
spring.flyway.enabled=true
spring.redis.host=${REDIS_HOST:localhost}
spring.redis.port=${REDIS_PORT:6379}
```

## Coverage Threshold

The pom.xml already has `jacoco` configured with 80% minimum. The CI must pass this check.

## Acceptance Criteria

- [ ] `.github/workflows/ci.yml` created and committed
- [ ] Backend builds successfully on GitHub Actions
- [ ] All unit tests pass in CI
- [ ] Integration tests pass with TimescaleDB service container
- [ ] Redis service container available for tests
- [ ] JaCoCo coverage report uploaded as artifact
- [ ] Frontend builds successfully on GitHub Actions
- [ ] Frontend lint passes
- [ ] Frontend tests pass
- [ ] OWASP dependency check runs
- [ ] Secret scanning runs
- [ ] Workflow status badges added to README.md

## Notes

- Use `temurin` JDK (free, reliable)
- TimescaleDB service container: `timescale/timescaledb:latest`
- Increase Maven heap to 2g (multi-module build needs memory)
- Consider adding a `deploy.yml` workflow in a future issue
