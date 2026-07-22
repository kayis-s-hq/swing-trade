import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: '.',
  testMatch: '**/validate.mjs',
  timeout: 40000,
  use: { headless: true },
  projects: [{ name: 'validate', testMatch: '**/validate.mjs' }],
})