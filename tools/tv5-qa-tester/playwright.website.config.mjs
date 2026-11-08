import {defineConfig} from '@playwright/test';
export default defineConfig({testDir:'website',timeout:45000,retries:0,workers:1,use:{baseURL:'http://localhost:5173',browserName:'chromium',trace:'off',video:'off'},reporter:'list'});
