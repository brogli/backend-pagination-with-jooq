import process from 'node:process'
import { defineConfig, devices } from '@playwright/test'

const BACKEND_URL = 'http://localhost:8080'

export default defineConfig({
  testDir: './e2e',
  timeout: 30 * 1000,
  expect: {
    timeout: 5000,
  },
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: BACKEND_URL,
    trace: 'on-first-retry',
    headless: !!process.env.CI,
  },

  /* Chromium only; add engines here and enable them in `flake.nix`. */
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],

  /* The real stack: backend serving the built SPA, Postgres testcontainer, 100k-row seed. */
  webServer: {
    command: './gradlew :backend:bootTestRun --args=--spring.liquibase.contexts=seed-medium',
    cwd: '..',
    url: `${BACKEND_URL}/actuator/health`,
    timeout: 5 * 60 * 1000,
    reuseExistingServer: !process.env.CI,
  },
})
