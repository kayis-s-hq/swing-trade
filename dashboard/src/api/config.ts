// API Configuration
// When running with Vite dev server, /api is proxied to backend (see vite.config.ts)
// In production, set this to the actual backend URL
export const API_BASE_URL = '/api'
export const REQUEST_TIMEOUT = 30000 // 30 seconds

// Default headers
export const DEFAULT_HEADERS = {
  Accept: 'application/json',
  'Content-Type': 'application/json',
}
