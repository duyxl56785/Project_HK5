import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests', testMatch: '**/*.spec.mjs', workers: 1, fullyParallel: false,
  use: { baseURL: 'http://127.0.0.1:5188', browserName: 'chromium',
    ...(process.env.TV1_BROWSER_CHANNEL ? { channel: process.env.TV1_BROWSER_CHANNEL } : {}),
    trace: 'retain-on-failure' },
  webServer: { command: 'node tests/auth-server.mjs', url: 'http://127.0.0.1:5188', reuseExistingServer: false },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
});
