import { test, expect } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';

test.afterEach(async ({page}, info) => {
  const dir = process.env.SOOPI_QA_SCREENSHOT_DIR;
  if (!dir) throw new Error('Thiếu thư mục bằng chứng riêng cho lần chạy');
  await mkdir(dir, {recursive:true});
  const label = info.title.replace(/[^a-zA-Z0-9-]/g,'-');
  await page.screenshot({path:path.join(dir, `${label}-${info.status}.png`),fullPage:true});
});

test('dashboard-hien-thi-suite-va-gates', async ({page}) => {
  await page.goto('/');
  await expect(page.getByRole('heading',{name:'Soopi TV5 QA Tester'})).toBeVisible();
  await expect(page.getByText('F15-RULES')).toBeVisible();
  await expect(page.getByText('FINANCE-PRODUCTION')).toBeVisible();
  await expect(page.getByText('BLOCKED_OWNER: Finance/Return production policy chưa được ký.')).toBeVisible();
});

test('dashboard-chi-goi-suite-allowlist', async ({page}) => {
  await page.goto('/');
  await page.getByText('F15-RULES').click();
  await expect(page.getByRole('button',{name:'Run Selected'})).toBeEnabled();
  await expect(page.getByRole('button',{name:'Run All Safe Tests'})).toBeVisible();
});

// Opt-in diagnostic proving that a real Playwright assertion failure also saves a screenshot.
// This is a tester self-check, not a product defect and never part of Run Selected.
if (process.env.SOOPI_QA_VERIFY_FAILURE === '1') test('diagnostic-co-y-fail-de-kiem-tra-anh', async ({page}) => {
  await page.goto('/');
  await expect(page.getByRole('heading',{name:'Không tồn tại'})).toBeVisible();
});
