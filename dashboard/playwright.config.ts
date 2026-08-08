import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: 'tests/e2e',
  use: {
    baseURL: 'http://localhost:3003',
    screenshot: 'only-on-failure',
    trace: 'off',
  },
  webServer: {
    command: 'yarn dev',
    port: 3003,
    reuseExistingServer: true,
  },
  timeout: 30000,
  reporter: 'list',
})
