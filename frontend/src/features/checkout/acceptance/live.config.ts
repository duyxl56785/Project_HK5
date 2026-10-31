import { defineConfig, devices } from '@playwright/test';

// Explicit projects: no app/server startup, seed, runtime flags or mocked network.
export default defineConfig({
  testDir: '.',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  workers: 1,
  retries: 0,
  reporter: 'list',
  outputDir: '/tmp/tv3-two-store-playwright',
  use: {
    ...devices['Desktop Chrome'],
    baseURL: process.env.TV3_E2E_BASE_URL,
    trace: 'off', // Auth responses contain real access tokens; do not save them in traces.
    screenshot: 'off',
    video: 'off',
  },
  projects: [
    { name: 'two-store', testMatch: 'two-store.live.spec.ts' },
    { name: 'unknown-replay', testMatch: 'unknown.live.spec.ts' },
    { name: 'tv4-lifecycle', testMatch: 'lifecycle.live.spec.ts' },
  ],
});
