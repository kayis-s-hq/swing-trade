# CI/CD Comprehensive Pipeline Plan

## Problem
- No CI workflow exists (issue #012 designed but never implemented)
- PMD disabled in 4 of 6 modules (data, strategy, llm, broker)
- Deploy workflows skip all quality gates (`-Dpmd.skip=true -Dcheckstyle.skip=true`)
- No main branch gate — PRs merge without automated review
- No frontend testing in CI

## Scope

### Phase 1: Enable PMD in 4 modules + fix violations
### Phase 2: Create CI workflow (PR + main gate)
### Phase 3: Update deploy workflows (remove skips)
### Phase 4: Create deploy-main workflow
### Phase 5: Generate static report sites (JaCoCo + PMD + Checkstyle) on Pi nginx

---

## Phase 1: Enable PMD in data, strategy, llm, broker

### 1a. Remove `<skip>true</skip>` from each module's POM

**`backend/data/pom.xml`** — find the PMD plugin configuration block and remove `<skip>true</skip>`
**`backend/strategy/pom.xml`** — same
**`backend/llm/pom.xml`** — same
**`backend/broker/pom.xml`** — same

### 1b. Run PMD on each module, fix violations

```bash
cd backend
mvn pmd:check -pl data,strategy,llm,broker
```

Fix any violations found (likely: null dereference, unused imports, string comparison issues).

### 1c. Verify all modules pass PMD

```bash
cd backend
mvn validate -pl core,data,strategy,llm,broker,api
```

---

## Phase 2: Create `.github/workflows/ci.yml`

Triggers:
- `pull_request` on `main` branch
- `push` on `main` branch

Jobs (all on `ubuntu-latest`):

### Job: backend
- Set up JDK 21 (Temurin), cache Maven
- Start PostgreSQL 16 + Redis service containers
- Run `mvn clean verify -B` (runs validate → PMD + Checkstyle → compile → test → package → jacoco)
- Upload JaCoCo report as artifact

### Job: frontend
- Set up Node 20, cache npm
- `npm ci` in `dashboard/`
- `npm run typecheck`
- `npm run test` (Vitest)
- `npx playwright install --with-deps chromium`
- `npx playwright test` (E2E)
- `npm run build`

### Job: security
- OWASP Dependency-Check on `backend/`
- TruffleHog secret scanning on full repo

### Job: notify
- Runs `always()`, reports status of all jobs

---

## Phase 5: Static report sites on Pi nginx (Docker)

### 5a. Create `backend/docker-compose.reports.yml` (or add to existing infra compose)

New service: `nginx-reports`
- Image: `nginx:alpine`
- Mount: `reports:/usr/share/nginx/html` (volume shared with CI artifact download)
- Config: serve directory listing for JaCoCo, PMD, Checkstyle HTML reports
- Port: `8082` (or use existing nginx on Pi if one exists)

### 5b. Add nginx config

`backend/reports-nginx/default.conf`:
```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    autoindex on;
    autoindex_exact_size off;
    autoindex_localtime on;

    location / {
        try_files $uri $uri/ =404;
    }
}
```

### 5c. CI step: copy reports to Pi

After `mvn verify` succeeds, SCP the HTML reports to the Pi:
```yaml
- name: Deploy reports to Pi
  run: |
    mkdir -p /tmp/reports/jacoco /tmp/reports/pmd /tmp/reports/checkstyle
    cp -r */target/site/jacoco/* /tmp/reports/jacoco/ 2>/dev/null || true
    cp -r */target/site/pmd.html /tmp/reports/pmd/ 2>/dev/null || true
    cp -r */target/site/checkstyle-result.xml /tmp/reports/checkstyle/ 2>/dev/null || true
    scp -r /tmp/reports/* pi@piworm.local:/home/dietpi/swing-trade/reports/
```

### 5d. Start nginx container on Pi

```yaml
- name: Start reports nginx
  run: |
    ssh piworm.local '
      mkdir -p ~/swing-trade/reports
      docker compose -f ~/swing-trade/docker-compose.reports.yml up -d
    '
```

---

## Phase 3: Update deploy-dev.yml and deploy-stage.yml

Remove `-Dpmd.skip=true -Dcheckstyle.skip=true` from both workflows.
Since CI gates code before it reaches dev/stage branches, these are no longer needed.

---

## Phase 4: Create `.github/workflows/deploy-main.yml`

Triggers: push to `main` branch

Deploys to production on the Pi:
- Checkout, JDK 21, Maven build
- Deploy JAR to production directory
- Health check

---

## Files to create/modify

**Create:**
- `.github/workflows/ci.yml`
- `.github/workflows/deploy-main.yml`

**Modify:**
- `backend/data/pom.xml` (remove PMD skip)
- `backend/strategy/pom.xml` (remove PMD skip)
- `backend/llm/pom.xml` (remove PMD skip)
- `backend/broker/pom.xml` (remove PMD skip)
- `.github/workflows/deploy-dev.yml` (remove skip flags)
- `.github/workflows/deploy-stage.yml` (remove skip flags)

**Create (Phase 5):**
- `backend/reports-nginx/default.conf` (nginx config for report serving)
- `backend/docker-compose.reports.yml` (nginx-reports service)

---

## Execution order

1. Phase 1 (PMD fixes) — must pass before CI can enable quality gates
2. Phase 2 (CI workflow) — the main deliverable
3. Phase 3 (deploy updates) — remove skip flags
4. Phase 4 (deploy-main) — production gate
5. Phase 5 (report sites) — nginx Docker on Pi serving JaCoCo/PMD/Checkstyle HTML