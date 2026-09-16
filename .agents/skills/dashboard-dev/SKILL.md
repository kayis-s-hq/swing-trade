---
name: dashboard-dev
description: Implement or troubleshoot the Vue 3 and TypeScript dashboard, including Pinia state, routing, UI behavior, and frontend tests.
---

# Dashboard development

Work from a senior/staff engineer and software architect perspective: identify the component or state owner, follow existing Vue conventions, keep responsibilities clear, and avoid introducing abstractions without a demonstrated need.

## Workflow

1. Trace the user-visible behavior through the Vue component, route, store, and API client as applicable. Read nearby code and tests first.
2. Keep UI state and API responsibilities in their existing layers. Preserve established loading, empty, error, accessibility, and responsive behavior.
3. Make focused changes and add or update tests for user-observable behavior, including relevant failure or boundary cases.
4. From `dashboard/`, run the narrowest relevant test, then `yarn typecheck`, `yarn test:run`, and `yarn build` as appropriate to the scope.
5. Review the rendered behavior where practical and inspect the final diff for regressions and unrelated changes. Report verification performed and any limitations.

Preserve unrelated worktree changes.
