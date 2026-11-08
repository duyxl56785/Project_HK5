import { defineConfig } from '@playwright/test';
export default defineConfig({ testDir:'ui', timeout:30000, retries:0, workers:1, use:{baseURL:'http://127.0.0.1:4318',browserName:'chromium'}, reporter:'list' });
