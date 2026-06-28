# feat(dashboard): wire up API client to real backend

**Labels:** `enhancement` `tier-2-frontend` `dashboard` `api-integration`
**Estimated effort:** 2-3 days

## Problem

`src/api/client.ts` is entirely mock data with hardcoded responses. The frontend renders correctly but shows fake data. The backend has real REST endpoints that the frontend should consume.

## Proposed Solution

Replace the mock API client with real fetch/axios calls to the Spring Boot backend. Map backend DTOs to frontend TypeScript types.

## Current State

```typescript
// src/api/client.ts - ALL MOCK
const samplePositions: Position[] = [
  { id: 'pos-001', symbol: 'RELIANCE', entryPrice: 2850.00, ... }
];
export const getPositionList = () => delay().then(() => ({ success: true, data: samplePositions }));
```

## Target State

```typescript
// src/api/client.ts - REAL API
const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

async function request<T>(endpoint: string, options?: RequestInit): Promise<ApiResponse<T>> {
  const response = await fetch(`${API_BASE}${endpoint}`, {
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    ...options,
  });
  if (!response.ok) throw new Error(`${response.status}: ${response.statusText}`);
  const json = await response.json();
  return { success: true, data: json as T };
}

export const getPositionList = () => request<PositionResponse[]>('/positions');
```

## API Mapping

| Frontend Function | Backend Endpoint | Method | Notes |
|------------------|-----------------|--------|-------|
| `getPositionList` | `/api/positions` | GET | Returns paginated response |
| `getPositionById` | `/api/positions/{symbol}` | GET | - |
| `closePosition` | `/api/positions/{symbol}/close` | POST | Body: `{ exitReason }` |
| `getSignalList` | `/api/signals/latest` | GET | Add filter params |
| `generateSignals` | `/api/signals/scan` | POST | Triggers market scan |
| `getPortfolioSummary` | `/api/trades/performance` | GET | Map to PortfolioMetricsResponse |
| `getEquityCurve` | `/api/portfolio/metrics` | GET | New endpoint needed |
| `getTradeHistory` | `/api/trades/{symbol}/history` | GET | Aggregate across symbols |
| `getMarketOverview` | `/api/positions` + `/api/trades/performance` | GET | Composite |

## Environment Configuration

Create `.env` and `.env.example`:
```env
VITE_API_URL=http://localhost:8080/api
VITE_WS_URL=ws://localhost:8080/ws
```

## Files to Modify

- `src/api/client.ts` - Complete rewrite
- `src/api/config.ts` - Add API base URL config
- `src/api/types.ts` - Update to match backend DTOs
- `vite.config.ts` - Add proxy for dev server
- `.env.example` - Environment variables template
- `package.json` - Consider adding `axios` for cleaner HTTP calls

## Vite Proxy Configuration

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
```

## Error Handling

```typescript
async function request<T>(endpoint: string, options?: RequestInit): Promise<ApiResponse<T>> {
  try {
    const response = await fetch(`${API_BASE}${endpoint}`, {
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      ...options,
    });
    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({}));
      return { success: false, error: errorBody.message || `${response.status}` };
    }
    const json = await response.json();
    return { success: true, data: json as T };
  } catch (networkError) {
    return { success: false, error: 'Network error - check backend connection' };
  }
}
```

## Acceptance Criteria

- [ ] All 9 frontend API functions call real backend endpoints
- [ ] Environment variable `VITE_API_URL` configures backend URL
- [ ] Vite dev server proxies `/api` to Spring Boot
- [ ] Error responses display in `ErrorMessage.vue` component
- [ ] Loading states work correctly for real API latency
- [ ] `src/api/types.ts` matches backend DTO structure
- [ ] Dashboard shows real data when backend is running
- [ ] Mock data completely removed from `client.ts`
- [ ] `.env.example` committed to repo
- [ ] Build succeeds with `npm run build`

## Notes

- Keep a `?useMock=true` query param option for development when backend is down
- The `ApiResponse` wrapper should match the backend `ApiResponse<T>` structure
- Consider adding an `interceptor` pattern for auth token management (future auth)
