import base from '/workspace/tv3-pr145-live-acceptance/frontend/src/features/checkout/acceptance/live.config';
import { defineConfig } from '@playwright/test';
export default defineConfig({
  ...base,
  testDir: '/workspace/tv3-pr145-live-acceptance/frontend/src/features/checkout/acceptance',
  outputDir: process.env.TV3_CASE_DIR + '/artifacts',
  reporter: [['list'], ['json', {outputFile: process.env.TV3_CASE_DIR + '/results.private.json'}], ['junit', {outputFile: process.env.TV3_CASE_DIR + '/junit.private.xml'}]],
  use: {...base.use, launchOptions: {executablePath: '/usr/bin/chromium'}, trace: 'off', screenshot: 'off', video: 'off'},
});
