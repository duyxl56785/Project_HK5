import { defineConfig, devices } from '@playwright/test';

// Live E2E: runs against the stack from scripts/demo/up.mjs, nothing is mocked.
export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173', trace: 'retain-on-failure', ...devices['Desktop Chrome'] },
});
