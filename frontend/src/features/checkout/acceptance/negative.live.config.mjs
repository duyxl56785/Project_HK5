import { defineConfig } from '@playwright/test';

// API acceptance only: no browser, server startup, flags, retries or fault injection.
export default defineConfig({
  testDir: '.',
  testMatch: 'negative.live.spec.mjs',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90_000,
  outputDir: process.env.TV3_NEG_EVIDENCE + '/artifacts',
  reporter: [['list'], ['json', { outputFile: process.env.TV3_NEG_EVIDENCE + '/playwright.json' }],
    ['junit', { outputFile: process.env.TV3_NEG_EVIDENCE + '/junit.xml' }]],
  use: { trace: 'off', screenshot: 'off', video: 'off' },
});
