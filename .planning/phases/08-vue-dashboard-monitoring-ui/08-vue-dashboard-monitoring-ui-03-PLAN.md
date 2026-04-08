---
phase: 08-vue-dashboard-monitoring-ui
plan: 03
type: execute
wave: 2
depends_on:
  - "08-vue-dashboard-monitoring-ui-01"
  - "08-vue-dashboard-monitoring-ui-02"
files_modified:
  - swing-trade-dashboard/tailwind.config.js
  - swing-trade-dashboard/src/styles/main.css
  - swing-trade-dashboard/src/components/LoadingSpinner.vue
  - swing-trade-dashboard/src/components/ErrorMessage.vue
  - swing-trade-dashboard/src/components/ConfirmDialog.vue
  - swing-trade-dashboard/vite.config.ts
  - swing-trade-dashboard/.env.example
  - swing-trade-dashboard/README.md
autonomous: true
requirements:
  - DASH-06
  - DASH-07
user_setup: []

must_haves:
  truths:
    - "Dashboard builds successfully for production"
    - "Error states are handled gracefully with user-friendly messages"
    - "Loading states show during data fetch"
    - "Responsive design works on mobile devices"
  artifacts:
    - path: "swing-trade-dashboard/README.md"
      provides: "Setup and deployment instructions"
      min_lines: 80
    - path: "swing-trade-dashboard/tailwind.config.js"
      provides: "Tailwind CSS configuration"
      min_lines: 50
  key_links:
    - from: "swing-trade-dashboard/src/components/LoadingSpinner.vue"
      to: "swing-trade-dashboard/src/views/*.vue"
      via: "import LoadingSpinner"
      pattern: "import.*LoadingSpinner"
    - from: "swing-trade-dashboard/vite.config.ts"
      to: "swing-trade-dashboard/dist/"
      via: "npm run build"
      pattern: "build.*outDir"
---

<objective>
Add polish, error handling, responsive design, and build configuration

Purpose: Ensure the dashboard is production-ready with proper error states, loading states, responsive design, and build configuration

Output:
- Complete Tailwind CSS styling
- Reusable UI components (LoadingSpinner, ErrorMessage, ConfirmDialog)
- Production build configuration
- README with setup instructions
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/phases/08-vue-dashboard-monitoring-ui/08-vue-dashboard-monitoring-ui-01-PLAN.md
@.planning/phases/08-vue-dashboard-monitoring-ui/08-vue-dashboard-monitoring-ui-02-PLAN.md

# Design Requirements
- Responsive: Mobile-first, works on 320px to 1920px
- Theme: Dark mode default (trading-focused)
- Colors: Green (profit/BUY), Red (loss/SELL), Yellow (neutral/HOLD)
- Typography: Inter font family
- Spacing: Tailwind standard spacing scale
</context>

<interfaces>
<!-- UI Component interfaces -->

LoadingSpinner:
  - props: size?: 'sm' | 'md' | 'lg'
  - slots: default content while loading

ErrorMessage:
  - props: message: string, onRetry?: () => void
  - slots: default error content

ConfirmDialog:
  - props: title: string, message: string, confirmText?: string, cancelText?: string
  - emits: confirm, cancel
  - slots: custom content

</interfaces>

<tasks>

<task type="auto">
  <name>Task 1: Configure Tailwind and create reusable components</name>
  <files>swing-trade-dashboard/tailwind.config.js, swing-trade-dashboard/src/components/LoadingSpinner.vue, swing-trade-dashboard/src/components/ErrorMessage.vue, swing-trade-dashboard/src/components/ConfirmDialog.vue</files>
  <action>
Create Tailwind configuration and reusable UI components:

1. tailwind.config.js:
   - Configure content paths for template scanning
   - Add custom colors for trading theme:
     * primary: trading system brand color
     * success: #10b981 (green for profit/BUY)
     * danger: #ef4444 (red for loss/SELL)
     * warning: #f59e0b (yellow for HOLD/neutral)
     * background: #1a1a2e (dark background)
     * surface: #16213e (card backgrounds)
   - Extend font family with Inter
   - Enable responsive breakpoints

2. LoadingSpinner component:
   - Circular spinner using CSS animate-spin
   - Props: size (sm=24px, md=40px, lg=64px)
   - Color variant (primary, secondary)
   - Usage: Wrap in <div class="flex justify-center"> during async operations

3. ErrorMessage component:
   - Styled error banner with icon
   - Props: message (string), onRetry (optional callback)
   - Red background with white text
   - X button to dismiss
   - Retry button if onRetry provided

4. ConfirmDialog component:
   - Modal overlay with centered dialog
   - Props: title, message, confirmText (default "Confirm"), cancelText (default "Cancel")
   - emits: confirm, cancel on button clicks
   - Escape key closes dialog
   - Backdrop click closes dialog
   - Focus trap for accessibility
  </action>
  <verify>
    <automated>cd swing-trade-dashboard && grep -c "colors:" tailwind.config.js && ls -la src/components/LoadingSpinner.vue src/components/ErrorMessage.vue src/components/ConfirmDialog.vue</automated>
  </verify>
  <done>
    - Tailwind configured with trading theme colors
    - LoadingSpinner component created and importable
    - ErrorMessage component created with optional retry
    - ConfirmDialog component created with confirm/cancel events
    - All components pass TypeScript checking
  </done>
</task>

<task type="auto">
  <name>Task 2: Add error and loading states to all views</name>
  <files>swing-trade-dashboard/src/views/DashboardView.vue, swing-trade-dashboard/src/views/PositionsView.vue, swing-trade-dashboard/src/views/SignalsView.vue, swing-trade-dashboard/src/views/PortfolioView.vue</files>
  <action>
Add error handling and loading states to all views:

1. DashboardView:
   - Add LoadingSpinner wrapper during initial fetch
   - Add ErrorMessage for health check failures
   - Show "Last updated: X seconds ago" when loaded

2. PositionsView:
   - Loading state for positions fetch
   - Empty state when no positions match filter
   - ErrorMessage for API failures
   - Error boundary around position cards

3. SignalsView:
   - Loading state for signal fetch
   - Empty state when no signals in tab
   - ErrorMessage for signal generation failures
   - Toast notification on successful generation

4. PortfolioView:
   - Loading state for performance data
   - Placeholder values if performance data unavailable
   - ErrorMessage for chart rendering failures
   - Fallback UI if sector allocation is empty

5. Shared error handling:
   - Use try/catch around all API calls
   - Extract error messages to ErrorResult interface
   - Display user-friendly messages (not technical errors)
  </action>
  <verify>
    <automated>cd swing-trade-dashboard && grep -c "LoadingSpinner" src/views/*.vue && grep -c "ErrorMessage" src/views/*.vue</automated>
  </verify>
  <done>
    - All views have LoadingSpinner during data fetch
    - All views have ErrorMessage for failures
    - Empty states handled gracefully
    - Error messages are user-friendly
    - No unhandled promise rejections
  </done>
</task>

<task type="auto">
  <name>Task 3: Configure production build and deployment</name>
  <files>swing-trade-dashboard/vite.config.ts, swing-trade-dashboard/.env.example, swing-trade-dashboard/package.json</files>
  <action>
Configure Vite for production deployment:

1. vite.config.ts:
   - Configure build.outDir to 'dist'
   - Configure build.rollupOptions for chunk splitting
   - Add optimizeDeps for dependency pre-bundling
   - Configure server.proxy for dev mode (proxy to localhost:8080)
   - Add server.host for accessible localhost binding

2. .env.example:
   - VITE_API_BASE_URL=http://localhost:8080
   - VITE_APP_TITLE=SwingTrade Dashboard
   - VITE_REFRESH_INTERVAL=30000

3. package.json scripts:
   - dev: vite (development server)
   - build: vue-tsc && vite build
   - preview: vite preview (preview production build)
   - lint: eslint src --ext .vue,.js,.ts

4. README.md:
   - Project overview and features
   - Prerequisites (Node.js 18+, Java 21, PostgreSQL)
   - Installation steps
   - Configuration options
   - Running development server
   - Building for production
   - Deployment options (Docker, VPS)
   - API endpoint documentation
   - Troubleshooting common issues
  </action>
  <verify>
    <automated>cd swing-trade-dashboard && cat .env.example && grep "build:" package.json</automated>
  </verify>
  <done>
    - vite.config.ts configured for production build
    - .env.example documents all environment variables
    - package.json has dev, build, preview, lint scripts
    - README.md with complete setup instructions
    - Build output goes to dist/ directory
  </done>
</task>

<task type="auto">
  <name>Task 4: Responsive design and mobile polish</name>
  <files>swing-trade-dashboard/src/styles/main.css, swing-trade-dashboard/src/App.vue, swing-trade-dashboard/src/views/*.vue</files>
  <action>
Ensure responsive design across all screen sizes:

1. main.css enhancements:
   - Custom scrollbar styling (thinner, dark theme)
   - Touch-friendly hit targets (min 44px)
   - Mobile-safe viewport adjustments
   - Prevent zoom on input focus

2. Mobile navigation:
   - Sidebar becomes hamburger menu on mobile (< 768px)
   - Off-canvas drawer for mobile menu
   - Active route indicator in mobile menu
   - Close button and backdrop

3. Grid layouts:
   - DashboardView: 1 column on mobile, 2x2 on desktop
   - PositionsView: 1 column cards on mobile, 3 column on desktop
   - SignalsView: 1 column on mobile, 2 column on tablet, 3 on desktop
   - PortfolioView: stacked on mobile, side-by-side on desktop

4. Typography scaling:
   - Use Tailwind responsive text classes (text-sm, md:text-base)
   - Ensure readability on small screens
   - Adjustable heading sizes

5. Touch interactions:
   - Swipe gestures for signal cards (optional)
   - Pull-to-refresh on mobile (optional)
   - Prevent default touch behaviors
  </action>
  <verify>
    <automated>cd swing-trade-dashboard && grep -c "md:" src/views/*.vue && grep -c "lg:" src/views/*.vue</automated>
    <manual>Test on mobile simulator (Chrome DevTools responsive mode)</manual>
  </verify>
  <done>
    - Mobile navigation works with hamburger menu
    - All grids reflow properly on mobile
    - Touch targets are accessible size
    - Typography scales appropriately
    - No horizontal scroll on mobile
  </done>
</task>

</tasks>

<verification>
<automated>cd swing-trade-dashboard && npm run build 2>&1 && ls -la dist/</automated>
<manual>
1. Run npm run dev and visit http://localhost:3000
2. Open Chrome DevTools and toggle device toolbar
3. Test on iPhone SE, iPad, and Desktop breakpoints
4. Verify hamburger menu works on mobile
5. Verify all charts render on mobile
6. Run npm run build and verify dist/ contains production assets
</manual>
</verification>

<success_criteria>
- Production build completes without errors
- All views handle loading and error states gracefully
- Mobile-responsive on iPhone SE, iPad, and Desktop
- Tailwind theme colors applied consistently
- README.md complete with setup instructions
- All TypeScript compilation succeeds
- No console errors in production build
</success_criteria>

<output>
After completion, create `.planning/phases/08-vue-dashboard-monitoring-ui/08-vue-dashboard-monitoring-ui-03-SUMMARY.md`
</output>
