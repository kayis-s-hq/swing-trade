# Phase 08: Vue Dashboard + Monitoring UI - Research

**Researched:** 2026-04-10
**Domain:** Vue 3 + Vite + TypeScript dashboard for SwingTrade system
**Confidence:** HIGH

## Summary

Phase 08-01 (Vue Dashboard Foundation) has been completed. The Vue 3 dashboard exists with Vite build tooling, Tailwind CSS, Vue Router, and four main views (Dashboard, Positions, Signals, Portfolio). The dashboard consumes REST APIs from the Spring Boot backend running on localhost:8080.

**Primary recommendation:** Phase 08-01 is complete. For Phase 08-02/08-03, focus on fixing API type mismatches, adding production-ready features (error boundaries, loading states), and ensuring responsive design for all breakpoints.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Vue | 3.5.13 | UI framework | Vue 3 Composition API for modern component patterns |
| Vite | 6.0.0 | Build tool | Fast HMR, native ES modules, optimized for Vue 3 |
| TypeScript | 5.7.0 | Type safety | Strict typing, better IDE support, catches errors at compile time |
| Vue Router | 4.5.0 | Navigation | Hash history for simple deployment without server config |
| Pinia | 2.3.0 | State management | Vue 3 native, simple API, good TypeScript support |
| Axios | 1.7.9 | HTTP client | Promise-based, interceptors for error handling |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Tailwind CSS | 4.0.0 | Styling | Utility-first CSS with custom TailAdmin palette |
| vue-tsc | 2.0.29 | Type checking | TypeScript compiler for Vue files |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Vue Router | React Router | Vue ecosystem lock-in |
| Pinia | Vuex | Vuex is legacy for Vue 3, Pinia is official |

**Installation:**
```bash
# Already exists in swing-trade-dashboard/
npm install
```

**Version verification:**
- Vue: 3.5.13 (current) - verified
- Vite: 6.0.0 (current) - verified
- TypeScript: 5.7.0 (current) - verified
- Vue Router: 4.5.0 (current) - verified
- Pinia: 2.3.0 (current) - verified
- Axios: 1.7.9 (current) - verified

## Architecture Patterns

### Recommended Project Structure
```
swing-trade-dashboard/
├── src/
│   ├── api/              # API client layer
│   │   ├── client.ts     # Axios instance + interceptors
│   │   ├── config.ts     # Environment config
│   │   └── types.ts      # TypeScript DTO interfaces
│   ├── components/       # Reusable components
│   │   ├── Sidebar.vue   # Navigation sidebar
│   │   └── Header.vue    # Top bar with dark mode
│   ├── views/            # Page-level views
│   │   ├── DashboardView.vue
│   │   ├── PositionsView.vue
│   │   ├── SignalsView.vue
│   │   ├── PortfolioView.vue
│   │   └── (optional) NotFoundView.vue
│   ├── router/           # Vue Router config
│   │   └── index.ts
│   ├── assets/           # CSS, images
│   │   └── main.css
│   ├── App.vue           # Root component
│   └── main.ts           # App entry point
├── dist/                 # Build output (generated)
├── index.html            # HTML entry point
├── package.json
├── vite.config.ts
├── tailwind.config.js
└── tsconfig.json
```

### Pattern 1: API Client Layer with Interceptors
**What:** Centralized HTTP client with request/response interceptors for error handling
**When to use:** All API communications with backend
**Example:**
```typescript
// Source: swing-trade-dashboard/src/api/client.ts
import axios, { type AxiosInstance } from 'axios'

const apiClient: AxiosInstance = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 30000,
  headers: {
    'Accept': 'application/json',
    'Content-Type': 'application/json',
  },
})

// Response interceptor for error handling
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const status = error.response.status
      if (status >= 400 && status < 500) {
        console.error('Client error:', status, error.response.data)
      } else if (status >= 500) {
        console.error('Server error:', status, error.response.data)
      }
    }
    return Promise.reject(error)
  }
)
```

### Pattern 2: Dark Mode with localStorage
**What:** Persist dark mode preference across sessions
**When to use:** Any UI with theme toggle
**Example:**
```typescript
// Source: swing-trade-dashboard/src/components/Sidebar.vue
const isDark = ref(false)

const toggleDarkMode = () => {
  isDark.value = !isDark.value
  localStorage.setItem('dark-mode', isDark.value.toString())
  if (isDark.value) {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
}
```

### Pattern 3: Computed Properties for Filtering
**What:** Reactive derived state for list filtering
**When to use:** Tables with search/sort/filter
**Example:**
```typescript
const filteredPositions = computed(() => {
  return positions.value.filter((pos) => {
    const matchesSearch = pos.symbol.toLowerCase().includes(searchTerm.value.toLowerCase())
    const matchesStatus = !statusFilter.value || pos.status === statusFilter.value
    return matchesSearch && matchesStatus
  })
})
```

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP Client | Custom fetch wrapper | Axios | Built-in interceptors, timeout, error handling |
| State Management | Vanilla Vue reactivity | Pinia | Vue 3 official, devtools support |
| Routing | Manual hash change | Vue Router | Navigation guards, lazy loading |
| Styling | Custom CSS framework | Tailwind CSS | Utility-first, responsive out of box |
| Icons | SVG sprite generation | Heroicons inline | Simple, no build step needed |

**Key insight:** The Vue ecosystem has mature, well-tested libraries for core functionality. Hand-rolling increases maintenance burden and reduces developer productivity.

## Runtime State Inventory

> Include this section for rename/refactor/migration phases only. Omit entirely for greenfield phases.

This is a Phase 08 continuation (08-01 completed, 08-02/08-03 planned). No runtime state inventory required as this is not a rename/refactor phase.

## Common Pitfalls

### Pitfall 1: API Type Mismatch
**What goes wrong:** TypeScript types don't match backend DTOs, causing runtime errors
**Why it happens:** Backend uses camelCase, frontend uses different naming; or missing/incorrect fields
**How to avoid:** Generate TypeScript types from OpenAPI spec, or maintain parallel type definitions
**Warning signs:** Console errors like "Cannot read property of undefined", API calls returning empty data

**Example from current code:**
- Backend `PositionResponse` has `entryPrice: BigDecimal`, frontend expects `entryPrice: number`
- Backend `SignalResponse` has `generatedAt: LocalDate`, frontend expects `createdDate: string`
- Backend uses `positionId` for trades, frontend uses `id`

### Pitfall 2: Console.log in Production
**What goes wrong:** Debug statements leak to production, clutter console
**Why it happens:** Developers forget to remove debug logging before build
**How to avoid:** Use Vite's `drop_console: true` (already configured), or use proper logger like pino
**Warning signs:** `console.log`, `console.error`, `console.warn` in source files

### Pitfall 3: Missing Error Boundaries
**What goes wrong:** Vue app crashes silently when API fails or component errors
**Why it happens:** No try-catch around async operations, no error state management
**How to avoid:** Implement ErrorBoundary component, show user-friendly error messages
**Warning signs:** Blank white screen, no error message shown to user

### Pitfall 4: Non-Responsive Layouts
**What goes wrong:** Dashboard looks broken on mobile/tablet
**Why it happens:** Tailwind responsive prefixes not applied consistently
**How to avoid:** Test on all breakpoints, use responsive prefixes (`sm:`, `md:`, `xl:`)
**Warning signs:** Content overflow, stacked layout on desktop, missing touch targets on mobile

### Pitfall 5: Hardcoded API URLs
**What goes wrong:** Dashboard can't connect to backend in different environments
**Why it happens:** API_BASE_URL hardcoded in config
**How to avoid:** Use environment variables, support `.env.local` for dev
**Warning signs:** Connection refused errors, dashboard only works on localhost:8080

## Code Examples

### Common Operation 1: Fetching Data on Mount
```typescript
// Source: swing-trade-dashboard/src/views/DashboardView.vue
import { ref, onMounted } from 'vue'
import { getMarketOverview, getPortfolioSummary } from '../api/client'

const marketOverview = ref<MarketOverview | null>(null)

onMounted(async () => {
  try {
    const response = await getMarketOverview()
    if (response.success && response.data) {
      marketOverview.value = response.data
    }
  } catch (error) {
    console.error('Failed to load data:', error)
  }
})
```

### Common Operation 2: Table with Status Badges
```typescript
// Source: swing-trade-dashboard/src/views/PositionsView.vue
<span
  class="inline-flex items-center rounded-full bg-success-50 px-2.5 py-0.5 text-xs font-medium text-success-600"
  v-if="position.status === 'OPEN'"
>
  Open
</span>
<span
  class="inline-flex items-center rounded-full bg-warning-50 px-2.5 py-0.5 text-xs font-medium text-warning-600"
  v-else-if="position.status === 'STOPPED'"
>
  Stopped
</span>
<span
  class="inline-flex items-center rounded-full bg-error-50 px-2.5 py-0.5 text-xs font-medium text-error-600"
  v-else
>
  Closed
</span>
```

### Common Operation 3: Modal with Close Handler
```typescript
// Source: swing-trade-dashboard/src/views/PositionsView.vue
const positionToClose = ref<Position | null>(null)

const openCloseModal = (position: Position) => {
  positionToClose.value = position
}

const closePosition = async () => {
  if (!positionToClose.value) return
  try {
    await closePosition(positionToClose.value.id)
    // Refresh
    positionToClose.value = null
  } catch (error) {
    console.error('Failed to close position:', error)
  }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual HTTP calls | Axios with interceptors | Vue 3 migration | Centralized error handling |
| Inline CSS | Tailwind utility classes | Phase 08-01 | Responsive design, consistent theming |
| Vuex (Vue 2) | Pinia (Vue 3) | Vue 3 ecosystem | Simpler state management |
| Hash history | Hash history (still) | Phase 08-01 | Simple deployment, no server config |

**Deprecated/outdated:**
- jQuery DOM manipulation - Vue's reactivity handles this
- Inline styles - use Tailwind utilities
- Manual JSON parsing - Axios handles this automatically

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Phase 08-01 is complete based on 08-01-SUMMARY.md | Summary | If incomplete, additional foundation work needed |
| A2 | Vue 3 + Vite stack is appropriate for this project | Standard Stack | Project may have constraints requiring different framework |
| A3 | Tailwind CSS 4.x is compatible with the project | Standard Stack | Tailwind 4 may have breaking changes not yet documented |
| A4 | Backend API on localhost:8080 is stable and versioned | API Client | API may change, breaking frontend |

## Open Questions

1. **What backend endpoints are actually implemented? (RESOLVED)**
   - What we know: PositionController, SignalController, HealthController, TradingController exist
   - What's unclear: Are `/market/overview`, `/portfolio/summary`, `/portfolio/equity-curve` endpoints implemented?
   - Answer: These endpoints are NOT implemented in Phase 08. They will be handled in Phase 09 when the backend API layer is complete. Phase 08 uses placeholder responses and mock data.

2. **Should we use a chart library for the Equity Curve? (RESOLVED)**
   - What we know: PortfolioView.vue has a chart placeholder
   - What's unclear: What's the recommended chart library for Vue 3? (Charts.js, ApexCharts, Recharts?)
   - Answer: No chart library is added in Phase 08. The chart is a placeholder with dashed border and icon. A chart library (likely ApexCharts) will be integrated in Phase 09 with proper backend data.

3. **Should we add Vue Router history mode? (RESOLVED)**
   - What we know: Currently using hash history (`createWebHashHistory`)
   - What's unclear: Is nginx/Apache configured for history mode?
   - Answer: Keep hash history for Phase 08. History mode requires server configuration (nginx/Apache rewrite rules) which is not currently set up. Hash mode works out of the box with static file serving. Migrate to history mode in Phase 09 if proper server configuration is added.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | Vite build | ✅ | 18+ | N/A (required for build) |
| npm | Package install | ✅ | 9+ | N/A (required for build) |
| Java 21 | Backend API | ✅ | 21 | N/A (required for backend) |
| PostgreSQL | Data storage | ✅ | 14+ | N/A (required for data) |

**Missing dependencies with no fallback:**
- None identified - all dependencies are available

**Missing dependencies with fallback:**
- None identified

## Validation Architecture

### Unit Testing (Recommended)
| Property | Value |
|----------|-------|
| Framework | Vitest (unit) |
| Config file | `vitest.config.ts` |
| Quick run command | `npx vitest run` |
| Full suite command | `npx vitest` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DASH-01 | Vue.js application scaffolding | Unit | `npx vitest run src/main.ts` | N/A - foundation |
| DASH-02 | REST API client integration | Unit | `npx vitest run src/api/` | N/A - integration |
| DASH-03 | Dashboard view with system overview | Unit | `npx vitest run src/views/DashboardView.vue` | N/A - E2E only |
| DASH-04 | Positions view with filtering | Unit | `npx vitest run src/views/PositionsView.vue` | N/A - E2E only |
| DASH-05 | Signals view with generation | Unit | `npx vitest run src/views/SignalsView.vue` | N/A - E2E only |
| DASH-06 | Portfolio view with metrics | Unit | `npx vitest run src/views/PortfolioView.vue` | N/A - E2E only |
| DASH-07 | Production build and responsive | Manual | `npm run build` + visual check | N/A - manual |

### Sampling Rate
- **Per task commit:** Run relevant unit tests for changed files
- **Per wave merge:** `npx vitest`
- **Phase gate:** Production build green before `/gsd-verify-work`

### Note on E2E Testing
Playwright E2E testing is NOT being implemented in this phase (08-02/08-03). The focus is on production-ready features (loading states, error handling, responsive design). E2E testing infrastructure can be added in a future phase as a dedicated Wave 0 task.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | N/A - No auth in dashboard |
| V3 Session Management | no | N/A - No session handling |
| V4 Access Control | no | N/A - No authorization checks |
| V5 Input Validation | yes | API client validates responses, input filtering in views |
| V6 Cryptography | no | N/A - No sensitive crypto operations |

**Note:** The Vue dashboard is a read-only monitoring interface. Security concerns are primarily:
- Input validation on "Generate Signals" button (prevents empty requests)
- Error message sanitization (prevents XSS in error display)
- API key handling (API_BASE_URL should not contain keys)

### Known Threat Patterns for Vue Dashboard

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| XSS via error messages | Spoofing | Escape HTML in error display, use v-text instead of v-html |
| API key in URL | Tampering | Use environment variables, never hardcode credentials |
| Insecure CORS | Elevation | Backend should have CORS whitelist for dashboard domain |
| Console.log leaks | Information Disclosure | Vite `drop_console: true` removes debug statements |

## Sources

### Primary (HIGH confidence)
- Context7: vue@3.5.13 - Vue 3 Composition API, component lifecycle, reactivity
- Context7: vite@6.0.0 - Build configuration, HMR, plugin system
- Context7: vue-router@4.5.0 - Hash history, route guards, navigation
- Context7: pinia@2.3.0 - State management API, stores
- Context7: axios@1.7.9 - Interceptors, request/response handling

### Secondary (MEDIUM confidence)
- Official Tailwind CSS docs - Utility classes, dark mode configuration
- Official Vue 3 docs - Component patterns, lifecycle hooks

### Tertiary (LOW confidence)
- None identified

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH - All library versions verified from package.json and Context7
- Architecture: HIGH - Project structure inferred from existing implementation
- Pitfalls: HIGH - Based on common Vue 3 issues and code review

**Research date:** 2026-04-10
**Valid until:** 2026-05-10 (30 days for stable stack)
