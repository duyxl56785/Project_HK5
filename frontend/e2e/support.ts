import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { Browser, Page } from '@playwright/test';

export const env = Object.fromEntries(readFileSync(resolve(import.meta.dirname, '../../.env.demo'), 'utf8').split('\n')
  .filter((line) => line.includes('=') && !line.startsWith('#'))
  .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1).trim()]));

export const accounts = {
  admin: { email: env.ADMIN_BOOTSTRAP_EMAIL, password: env.ADMIN_BOOTSTRAP_PASSWORD },
  customer: { email: 'customer@hk5.local', password: env.SEED_PASSWORD },
  seller: { email: 'seller1@hk5.local', password: env.SEED_PASSWORD },
};

export async function logIn(page: Page, { email, password }: { email: string; password: string }) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mật khẩu').fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('button', { name: 'Đăng xuất' }).waitFor();
}

/** Each role gets its own browser context, as separate people would. */
export async function sessionFor(browser: Browser, account: { email: string; password: string }) {
  const page = await (await browser.newContext()).newPage();
  await logIn(page, account);
  return page;
}

export const unique = () => Math.random().toString(36).slice(2, 10);
