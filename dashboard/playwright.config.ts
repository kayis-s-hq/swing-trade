import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: 'tests/e2e',
  use: {
    baseURL: 'http://127.0.0.1:3003',
    screenshot: 'off',
    trace: 'off',
  },
  webServer: {
    command: 'yarn dev --host 127.0.0.1',
    port: 3003,
    reuseExistingServer: true,
  },
  timeout: 30000,
  reporter: 'list',
})
