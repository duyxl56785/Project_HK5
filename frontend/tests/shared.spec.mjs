import { test, expect } from '@playwright/test';

test.beforeEach(async ({ request }) => { await request.post('/__test/reset', { data: {} }); });
async function seed(context) {
  await context.addCookies([{ name: 'refresh_token', value: 'seed', domain: '127.0.0.1', path: '/api/auth', httpOnly: true, sameSite: 'Lax' }]);
}

test('memory session reload/rotation, HttpOnly cookie, CSRF/Origin, and no persisted identity', async ({ page, context, request }) => {
  await seed(context); await page.goto('/customer');
  await expect(page.getByRole('heading', { name: 'Customer', exact: true })).toBeVisible();
  const cookie = (await context.cookies()).find((item) => item.name === 'refresh_token');
  expect(cookie).toMatchObject({ httpOnly: true, sameSite: 'Lax', path: '/api/auth' });
  expect(cookie.value).toBe('cookie-1');
  expect(await page.evaluate(() => ({ cookie: document.cookie, local: { ...localStorage }, session: { ...sessionStorage } }))).toEqual({ cookie: '', local: {}, session: {} });
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Customer', exact: true })).toBeVisible();
  expect((await context.cookies()).find((item) => item.name === 'refresh_token').value).toBe('cookie-2');
  const state = await (await request.get('/__test/state')).json();
  expect(state.calls.filter((call) => call.path === '/api/auth/refresh')).toHaveLength(2);
  for (const call of state.calls.filter((item) => item.method === 'POST')) expect(call).toMatchObject({ origin: 'http://127.0.0.1:5188', csrf: '1', bearer: false, cookie: true });
  for (const call of state.calls.filter((item) => item.path === '/api/auth/me')) expect(call).toMatchObject({ bearer: true, cookie: false });
});

for (const [role, permitted, forbidden] of [['CUSTOMER', 'Customer', 'Seller'], ['SELLER', 'Seller', 'Admin'], ['ADMIN', 'Admin', 'Customer']]) {
  test(`${role} shell allowed; no implicit role hierarchy`, async ({ page, context, request }) => {
    await request.post('/__test/reset', { data: { roles: [role], tokenRoles: ['ADMIN', 'SELLER', 'CUSTOMER'] } });
    await seed(context); await page.goto(`/${permitted.toLowerCase()}`);
    await expect(page.getByRole('heading', { name: permitted, exact: true })).toBeVisible();
    await page.getByRole('link', { name: forbidden, exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('không có quyền');
    await expect(page.getByRole('heading', { name: forbidden, exact: true })).toHaveCount(0);
    await expect(page.locator('main')).toBeFocused();
    expect((await (await request.get('/__test/state')).json()).calls.every((call) => call.path.startsWith('/api/auth/'))).toBe(true);
  });
}

test('anonymous redirects to login, form labels and keyboard navigation work at 375px', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto('/seller'); await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel('Email', { exact: true }).fill('test@example.invalid');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('  exact password  ');
  await page.getByLabel('Mật khẩu', { exact: true }).press('Tab');
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeFocused();
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).press('Enter');
  await expect(page.getByRole('alert')).toContainText('không có quyền');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('pending verification never exposes protected shell; 503 remains error instead of login', async ({ page, context, request }) => {
  await request.post('/__test/reset', { data: { refreshStatus: 503, delay: 400 } });
  await seed(context); await page.goto('/admin');
  await expect(page.getByRole('status')).toContainText('Đang xác minh');
  await expect(page.getByRole('heading', { name: 'Admin', exact: true })).toHaveCount(0);
  await expect(page.getByRole('alert')).toContainText('tạm thời không khả dụng');
  await expect(page).toHaveURL(/\/admin$/);
  await expect(page.getByRole('button', { name: 'Khôi phục phiên' })).toBeVisible();
});

test('429 exposes Retry-After without automatic refresh retry', async ({ page, request }) => {
  await request.post('/__test/reset', { data: { refreshStatus: 429 } });
  await page.goto('/customer');
  await expect(page.getByRole('alert')).toContainText('Retry-After: 11');
  expect((await (await request.get('/__test/state')).json()).calls).toHaveLength(1);
});

test('two real tabs serialize refresh against one rotating HttpOnly cookie', async ({ context, request }) => {
  await request.post('/__test/reset', { data: { delay: 150 } });
  await seed(context);
  const a = await context.newPage(), b = await context.newPage();
  await Promise.all([a.goto('/customer'), b.goto('/customer')]);
  await expect(a.getByRole('heading', { name: 'Customer', exact: true })).toBeVisible();
  await expect(b.getByRole('heading', { name: 'Customer', exact: true })).toBeVisible();
  const state = await (await request.get('/__test/state')).json();
  expect(state.maxActive).toBe(1); expect(state.sequence).toBe(2);
  expect(state.calls.filter((call) => call.path === '/api/auth/refresh')).toHaveLength(2);
});

test('logout broadcasts memory/cache invalidation to another tab and clears cookie', async ({ page, context }) => {
  await seed(context); await page.goto('/customer');
  await expect(page.getByRole('heading', { name: 'Customer', exact: true })).toBeVisible();
  const other = await context.newPage(); await other.goto('/customer');
  await expect(other.getByRole('heading', { name: 'Customer', exact: true })).toBeVisible();
  for (const tab of [page, other]) await tab.evaluate(() => window.testClient.queryClient.setQueryData(['private'], 'private'));
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  for (const tab of [page, other]) {
    await expect(tab).toHaveURL(/\/login$/);
    expect(await tab.evaluate(() => window.testClient.queryClient.getQueryData(['private']))).toBeUndefined();
  }
  expect((await context.cookies()).some((item) => item.name === 'refresh_token')).toBe(false);
});

test('logout 503 reports that revocation is unconfirmed and removes protected data', async ({ page, context, request }) => {
  await request.post('/__test/reset', { data: { logoutStatus: 503 } });
  await seed(context); await page.goto('/customer');
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByText('Chưa xác nhận thu hồi phiên trên máy chủ.')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Customer', exact: true })).toHaveCount(0);
  expect(await page.evaluate(() => window.testClient.getSession().account)).toBeUndefined();
});

test('register does not auto-login; unknown routes have accessible fallback', async ({ page }) => {
  await page.goto('/register');
  await page.getByLabel('Email', { exact: true }).fill('test@example.invalid');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('valid-test-password');
  await page.getByRole('button', { name: 'Tạo tài khoản' }).click();
  await expect(page.getByRole('status')).toContainText('Đã tạo tài khoản');
  expect(await page.evaluate(() => window.testClient.getSession().account)).toBeUndefined();
  await page.goto('/unknown'); await expect(page.getByRole('heading', { name: 'Không tìm thấy trang' })).toBeVisible();
});

test('test server rejects missing CSRF; browser controls Origin; direct ports fail before fetch', async ({ page, context }) => {
  await seed(context); await page.goto('/');
  const result = await page.evaluate(async () => {
    const rejected = await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
    let blocked = false;
    try { await window.testClient.request('http://127.0.0.1:8101/api/auth/me'); } catch { blocked = true; }
    return { status: rejected.status, blocked };
  });
  expect(result).toEqual({ status: 403, blocked: true });
});

test('stored ADMIN identity cannot grant a route; production entrypoint stays isolated', async ({ page, context }) => {
  await context.addInitScript(() => {
    localStorage.setItem('account', JSON.stringify({ userId: 'spoof', roles: ['ADMIN'] }));
    localStorage.setItem('accessToken', 'spoof-token');
  });
  await seed(context); await page.goto('/admin');
  await expect(page.getByRole('alert')).toContainText('không có quyền');
  await expect(page.getByRole('heading', { name: 'Admin', exact: true })).toHaveCount(0);
  await page.goto('/index.html');
  // App's scaffold landing route is '/', while the fixture host owns that URL on this test server.
  await page.evaluate(() => { history.replaceState(null, '', '/'); window.dispatchEvent(new PopStateEvent('popstate')); });
  await expect(page.getByRole('heading', { name: 'Smart E-Commerce Marketplace V2', exact: true })).toBeVisible();
  expect(await page.evaluate(() => window.testClient)).toBeUndefined();
});
