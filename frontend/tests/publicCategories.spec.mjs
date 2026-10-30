import { test, expect } from '@playwright/test';
const categories = [{ id: '11111111-1111-4111-8111-111111111111', name: 'Gốm Việt', parentId: null }];
test('category route default OFF makes no request', async ({ page }) => {
  let calls = 0;
  await page.route('**/api/categories', async route => { calls++; await route.fulfill({ json: categories }); });
  await page.goto('/catalog/categories');
  await expect(page.getByRole('heading', { name: 'Không tìm thấy trang' })).toBeVisible();
  expect(calls).toBe(0);
});
test('opt-in Category shared read selects real response projection', async ({ page }) => {
  await page.route('**/api/categories', async route => {
    expect(route.request().method()).toBe('GET');
    expect(route.request().headers().cookie).toBeUndefined();
    await route.fulfill({ json: categories });
  });
  await page.goto('/catalog/categories?categories=on');
  const item = page.getByRole('treeitem', { name: /Gốm Việt/ });
  await item.click();
  await expect(item).toHaveAttribute('aria-selected', 'true');
});
test('dependency error sanitizes details and manual retry recovers', async ({ page }) => {
  let calls = 0;
  let failing = true;
  await page.route('**/api/categories', async route => {
    calls++;
    await route.fulfill(failing ? { status: 503, json: { code: 'PRIVATE_SERVER_DETAIL' } } : { json: categories });
  });
  await page.goto('/catalog/categories?categories=on');
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.locator('body')).not.toContainText('PRIVATE_SERVER_DETAIL');
  const settledCalls = calls;
  await page.waitForTimeout(200);
  expect(calls).toBe(settledCalls);
  failing = false;
  await page.getByRole('button', { name: /Thử lại/ }).click();
  await expect(page.getByRole('treeitem', { name: /Gốm Việt/ })).toBeVisible();
});
test('malformed response fails closed rather than false empty', async ({ page }) => {
  await page.route('**/api/categories', route => route.fulfill({ json: { items: categories } }));
  await page.goto('/catalog/categories?categories=on');
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByRole('treeitem')).toHaveCount(0);
});
