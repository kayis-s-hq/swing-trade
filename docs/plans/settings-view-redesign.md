# TDD Plan: Settings View Redesign

## Phase Status

| Phase | Status | Result | Timestamp |
|-------|--------|--------|-----------|
| 1: Toast Component | [x] COMPLETE | All 6 tests pass | 2026-08-11 |
| 2: Tab Navigation | [x] COMPLETE | All 5 tests pass | 2026-08-11 |
| 3: Broker Section | [x] COMPLETE | All 3 tests pass | 2026-08-11 |
| 4: LLM Section | [x] COMPLETE | All 3 tests pass | 2026-08-11 |
| 5: Trading Section | [x] COMPLETE | All 3 tests pass | 2026-08-11 |
| 6: Health Section | [x] COMPLETE | 1 test passes | 2026-08-11 |
| 7: Save Flow + Toast Integration | [x] COMPLETE | All 4 tests pass | 2026-08-11 |

## 1. Feature Map

| Feature | Tested? | Test Type |
|---------|---------|-----------|
| Toast auto-dismiss (4s) | No | Unit |
| Toast type rendering (success/error/warning/info) | No | Unit |
| Toast aria-live attributes | No | Unit |
| Toast Teleport to body | No | Unit |
| Tab switching (4 tabs) | No | Unit |
| Tab keyboard navigation | No | Unit |
| Tab content visibility | No | Unit |
| Broker card rendering | No | Unit |
| Broker selection toggle | No | Unit |
| Fyers connect flow | No | Unit |
| LLM endpoint inputs | No | Unit |
| Trading config inputs | No | Unit |
| Health status rendering | No | Unit |
| Save all settings flow | No | Unit |
| Save error handling | No | Unit |

## 2. Phase Breakdown

### Phase 1: Toast Component (RED)

**Create:** `dashboard/src/components/Toast.test.ts`

**Test methods:**
- `renders message with correct type styling` — mount Toast with `message: "Saved"` and `type: "success"`. Assert `.contains("Saved")` and the success SVG icon is present.
- `auto-dismisses after duration` — mount with `duration: 100`. Use `vi.useFakeTimers()`. Advance 150ms. Assert component is not in DOM.
- `shows error type with danger styling` — mount with `type: "error"`. Assert CSS class contains `danger`.
- `aria-live set to assertive for errors` — mount with `type: "error"`. Assert `role="alert"` and `aria-live="assertive"` on root element.
- `teleports to body` — mount Toast. Assert root element is a child of `document.body`.

**Why it fails:** Toast.vue doesn't exist yet.

**Source to create:** `dashboard/src/components/Toast.vue`
- Props: `message: string`, `type: 'success'|'error'|'warning'|'info'`, `duration: number`
- Uses `<Teleport to="body">` for positioning
- CSS: fixed bottom-right, fade-in animation, type-based color classes
- Auto-dismiss with setTimeout
- SVG icons per type (checkmark, x, warning, info) — matching existing Icons.ts stroke style

### Phase 2: Tab Navigation (RED)

**Create:** `dashboard/src/views/SettingsView.test.ts` — Tab tests

**Test methods:**
- `shows Broker tab content by default` — mount SettingsView. Assert broker section is visible, other sections hidden.
- `clicking AI/LLM tab shows LLM section` — click tab button. Assert LLM section visible, broker hidden.
- `clicking Trading tab shows trading config` — click tab. Assert trading grid visible.
- `clicking Health tab shows health status` — click tab. Assert health cards visible.
- `tab buttons have correct aria roles` — assert `role="tablist"` on container, `role="tab"` on buttons, `aria-selected` on active tab.

**Why it fails:** SettingsView currently has no tab system — all sections are visible at once.

**Source changes:** SettingsView.vue — wrap each section in tab content div, add tab bar with pill-style buttons.

### Phase 3: Broker Section (RED)

**Create:** `dashboard/src/views/SettingsView.test.ts` — Broker tests

**Test methods:**
- `renders 4 broker options as pill buttons` — mount. Count broker buttons. Assert 4 buttons with labels: Fyers, Upstox, Yahoo Finance, None.
- `selected broker has brand styling` — select Fyers. Assert selected button has `bg-brand-subtle` class.
- `Fyers connect button triggers auth` — connect Fyers, click connect. Assert `startFyersAuth` is called.
- `disconnect button shown when connected` — mock fyersStatus.connected = true. Assert disconnect button visible.
- `connection status indicator shows dot` — assert colored dot spans with pulse-dot class.

**Why it fails:** Broker section exists but in the old card-panel layout.

**Source changes:** SettingsView.vue — restructure broker section into compact card with pill selector, inline status indicator.

### Phase 4: LLM Section (RED)

**Create:** `dashboard/src/views/SettingsView.test.ts` — LLM tests

**Test methods:**
- `renders vLLM endpoint input with test button` — mount. Assert input with placeholder and test button visible.
- `renders PDF extraction section` — assert PDF input section present.
- `renders Discord toggle with webhook input` — assert toggle switch and webhook input.
- `test button shows loading state` — click test button. Assert button text changes to "Testing...".

**Why it fails:** LLM section exists but in old layout.

**Source changes:** SettingsView.vue — restructure into compact card with labeled inputs + inline test buttons.

### Phase 5: Trading Section (RED)

**Create:** `dashboard/src/views/SettingsView.test.ts` — Trading tests

**Test methods:**
- `renders trading mode selector` — mount. Assert select element with paper/live options.
- `renders max position size with % suffix` — assert input with "10" default and "%" label.
- `renders stop loss and take profit inputs` — assert both inputs with correct defaults (5, 15).
- `input changes update reactive state` — change maxPositionSize to 20. Assert `settings.tradingConfig.maxPositionSize === 20`.

**Why it fails:** Trading section exists but in old 2-column grid layout.

**Source changes:** SettingsView.vue — restructure into single-row labeled inputs with better density.

### Phase 6: Health Section (RED)

**Create:** `dashboard/src/views/SettingsView.test.ts` — Health tests

**Test methods:**
- `shows loading spinner when no health data` — mount without health mock. Assert LoadingSpinner visible.
- `renders health components as status pills` — mock healthStatus with 3 components. Assert 3 status pill rows.
- `UP status shows green pill` — assert component with status "UP" has success color class.
- `DOWN status shows red pill` — assert component with status "DOWN" has danger color class.

**Why it fails:** Health section exists but as full card-panel.

**Source changes:** SettingsView.vue — reduce health to compact status pills row, no full card.

### Phase 7: Save Flow + Toast Integration (RED)

**Create:** `dashboard/src/views/SettingsView.test.ts` — Save flow tests

**Test methods:**
- `save button triggers saveSettings` — mount with mocked saveSettings returning true. Click save. Assert toast with "Saved!" appears.
- `save button shows loading state` — click save. Assert button disabled + text "Saving...".
- `save failure shows error toast` — mock saveSettings returning false. Assert error toast rendered.
- `save button resets after success` — after save succeeds, wait 2s. Assert button text returns to "Save All Settings".
- `save button is in header bar` — assert save button positioned in header row next to title.

**Why it fails:** Save uses `alert()` for errors and has no toast feedback.

**Source changes:** SettingsView.vue — replace `alert()` calls with Toast component, move save button to header bar.

## 3. Files Summary

| Action | File | Type |
|--------|------|------|
| Create | `src/components/Toast.vue` | Component |
| Create | `src/components/Toast.test.ts` | Unit |
| Create | `src/views/SettingsView.test.ts` | Unit |
| Rewrite | `src/views/SettingsView.vue` | Component |

## 4. Verification

```bash
cd dashboard

# Phase 1: Toast — fails (no Toast.vue or tests)
yarn test -- --run Toast.test

# → Create Toast.vue
# Phase 1: Toast — passes
yarn test -- --run Toast.test

# Phase 2: Tabs — fails (no SettingsView.test.ts)
yarn test -- --run SettingsView.test

# → Add tab system to SettingsView.vue
# Phase 2: Tabs — passes
yarn test -- --run SettingsView.test

# ... repeat for each phase

# Final: all tests pass
yarn test -- --run

# Visual verification
yarn dev
# Navigate to Settings page — verify tab switching, toast feedback, compact layout
```

## 5. Design System Reference

- **Font:** Inter (already loaded in main.css)
- **Brand:** `#00d4a0` via `--color-brand`
- **Surface:** `--color-bg-surface` (#161923) with `--color-border-subtle` (#252a3a)
- **Radius:** `--radius-md` (8px) for cards, `--radius-lg` (12px) for tabs
- **Spacing:** 8dp rhythm (p-3=12px, p-4=16px, p-5=20px, gap-2=8px, gap-3=12px)
- **Animation:** fade-in (0.4s), pulse-dot (2s), toast slide-in (150ms)
- **Icons:** inline SVG matching existing Icons.ts stroke style (1.5px stroke, round caps/joins)

## 6. New Component: Toast

**Position:** Fixed bottom-right (bottom-6, right-6), z-50
**Appearance:** Type-colored background (20% opacity), matching border, icon + message
**Behavior:** Auto-dismiss after `duration` (default 4000ms), Teleport to body
**Accessibility:** `role="alert"`, `aria-live="polite"` (assertive for errors)

## 7. New Layout: SettingsView

```
┌─────────────────────────────────────────────────────┐
│ Settings                    [Save All Settings]     │
├─────────────────────────────────────────────────────┤
│ [● Broker]  [AI/LLM]  [Trading]  [Health]           │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Broker Connection                                  │
│  ┌──────────┬──────────┬──────────┬──────────┐     │
│  │ ● Fyers  │  Upstox  │  Yahoo   │   None   │     │
│  └──────────┴──────────┴──────────┴──────────┘     │
│                                                     │
│  ┌─────────────────────────────────────────────┐   │
│  │ ● Connected              ID: XXXXXXXXXXXX   │   │
│  └─────────────────────────────────────────────┘   │
│  [Disconnect]                                       │
│                                                     │
└─────────────────────────────────────────────────────┘
```

**Tab bar:** Pill-style buttons, active = brand bg + brand text, inactive = subtle border + muted text. Hover: border color shift.
**Cards:** `card-panel` (existing), tighter padding (p-4 instead of p-5), compact content.
**Save button:** Top-right in header, brand bg, disabled state during save.
