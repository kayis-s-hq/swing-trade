# Frontend Fixes — TDD Plan

Fix 8 frontend findings from architecture audit (docs/analysis/architecture-audit-2026-08-07.md).

## Phase Status

| Phase | Status | Result | Timestamp |
|-------|--------|--------|-----------|
| 1: ApiResult<T> types | [ ] PENDING | — | — |
| 2: ErrorBoundary component | [ ] PENDING | — | — |
| 3: useAsyncData composable | [ ] PENDING | — | — |
| 4: SSE timeout (AbortController) | [ ] PENDING | — | — |
| 5: Position type sync | [ ] PENDING | — | — |
| 6: rawFetch retry | [ ] PENDING | — | — |
| 7: Currency standardization | [ ] PENDING | — | — |
| 8: Allocation from settings | [ ] PENDING | — | — |

---

## Phase 1: ApiResult<T> types

### RED — Write failing test

**File**: `dashboard/tests/unit/api-types.test.ts` (create)

```typescript
describe('ApiResponse<T>', () => {
  it('should have success field', () => {
    const resp: ApiResponse<string> = { success: true, data: 'hello' };
    expect(resp.success).toBe(true);
  });

  it('should have data field on success', () => {
    const resp: ApiResponse<number[]> = { success: true, data: [1, 2, 3] };
    expect(resp.data).toEqual([1, 2, 3]);
  });

  it('should have error field on failure', () => {
    const resp: ApiResponse<null> = { success: false, error: 'bad request' };
    expect(resp.error).toBe('bad request');
  });
});
```

### GREEN — Fix the code

**File**: `dashboard/src/api/types.ts` (add after existing types)

```typescript
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
}
```

### Verification
```bash
cd dashboard && yarn test -- api-types.test.ts
```

---

## Phase 2: ErrorBoundary component

### RED — Write failing test

**File**: `dashboard/tests/unit/ErrorBoundary.test.ts` (create)

```typescript
describe('ErrorBoundary', () => {
  it('renders children when no error', () => {
    const { baseElement } = render(ErrorBoundary, {
      slots: { default: '<div>content</div>' }
    });
    expect(baseElement.textContent).toContain('content');
    expect(baseElement.querySelector('.error-boundary-error')).toBeNull();
  });

  it('shows error UI when error slot is triggered', async () => {
    const { baseElement } = render(ErrorBoundary, {
      slots: {
        default: '<div>content</div>',
        error: '<button data-testid="retry">Retry</button><span class="error-boundary-error">Error</span>'
      }
    });
    // ErrorBoundary uses v-slot:error — need to test the fallback
  });
});
```

### GREEN — Fix the code

**File**: `dashboard/src/components/ErrorBoundary.vue` (create)

```vue
<template>
  <slot />
  <div v-if="$slots.error" class="error-boundary-error">
    <slot name="error" />
  </div>
</template>
```

### Verification
```bash
cd dashboard && yarn test -- ErrorBoundary.test.ts
```

---

## Phase 3: useAsyncData composable

### RED — Write failing test

**File**: `dashboard/tests/unit/useAsyncData.test.ts` (create)

```typescript
describe('useAsyncData', () => {
  it('starts with loading=true', () => {
    const { loading, error, execute } = useAsyncData();
    expect(loading.value).toBe(true);
  });

  it('sets data on success', async () => {
    const { data, loading, error, execute } = useAsyncData();
    await execute(async () => ({ value: 42 }));
    expect(data.value).toEqual({ value: 42 });
    expect(loading.value).toBe(false);
    expect(error.value).toBe(null);
  });

  it('sets error on failure', async () => {
    const { data, loading, error, execute } = useAsyncData();
    await execute(async () => { throw new Error('fail'); });
    expect(error.value).toBe('fail');
    expect(loading.value).toBe(false);
  });
});
```

### GREEN — Fix the code

**File**: `dashboard/src/composables/useAsyncData.ts` (create)

```typescript
import { ref, Ref } from 'vue';

export function useAsyncData<T = unknown>() {
  const data = ref(null as T | null);
  const loading = ref(true);
  const error = ref<string | null>(null);
  const errorMessage = ref('');

  async function execute(fn: () => Promise<T>): Promise<void> {
    loading.value = true;
    error.value = null;
    errorMessage.value = '';
    try {
      data.value = await fn();
    } catch (err: unknown) {
      error.value = err as Error;
      errorMessage.value = err instanceof Error ? err.message : 'Unknown error';
    } finally {
      loading.value = false;
    }
  }

  return { data, loading, error, errorMessage, execute };
}
```

### Verification
```bash
cd dashboard && yarn test -- useAsyncData.test.ts
```

---

## Phase 4: SSE timeout (AbortController)

### RED — Write failing test

**File**: `dashboard/tests/unit/sse-timeout.test.ts` (create)

```typescript
describe('SSE timeout', () => {
  it('aborts after 60 seconds', async () => {
    // Mock fetch to return a stream that never ends
    // Verify AbortController is called with 60000ms timeout
  });
});
```

### GREEN — Fix the code

**File**: `dashboard/src/api/client.ts`

In both `generateAllSignalsStream()` and `runFullAnalysis()`:
- Add `const controller = new AbortController();`
- Add `const timeoutId = setTimeout(() => controller.abort(), 60_000);`
- Pass `{ signal: controller.signal }` to fetch
- Clear timeout in `finally` block

---

## Phase 5: Position type sync

### GREEN — Fix the code

**File**: `dashboard/src/api/types.ts`

Replace existing `Position` interface with full 24-field version matching backend.
Add `STOPPED` and `TARGET_HIT` to status union.

**File**: `dashboard/src/api/client.ts`

Update `mapPosition()` to include all fields.

---

## Phase 6: rawFetch retry

### GREEN — Fix the code

**File**: `dashboard/src/api/client.ts`

In `rawFetch()`: after `!response.ok`, retry 3 times with exponential backoff for 5xx + network errors.

---

## Phase 7: Currency standardization

### GREEN — Fix the code

**File**: `dashboard/src/views/DashboardView.vue`

Replace all `$` with `Rs.` (7 occurrences).

---

## Phase 8: Allocation from settings

### GREEN — Fix the code

**File**: `dashboard/src/views/SignalsView.vue`

Replace hardcoded `100000` with value from settings store.

---

## Files Summary

| Action | File | Type |
|--------|------|------|
| Create | `api/types.ts` (ApiResponse) | Type |
| Create | `components/ErrorBoundary.vue` | Component |
| Create | `composables/useAsyncData.ts` | Composable |
| Modify | `api/client.ts` (types + SSE + retry) | Source |
| Modify | `views/DashboardView.vue` (currency) | View |
| Modify | `views/SignalsView.vue` (allocation) | View |

## Verification

```bash
cd dashboard
yarn test                          # Vitest unit tests
yarn playwright test               # E2E tests
```
