// Re-export all API functions from domain-specific modules.
// New code should import from domain modules directly (e.g., '../api/signals').
// Migrated methods resolve confirmed domain values and throw AppError on failure.

export * from './index'
