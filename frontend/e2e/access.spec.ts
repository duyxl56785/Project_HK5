import { expect, test } from '@playwright/test';
import { accounts, logIn, sessionFor, unique } from './support';

test('forms validate in the browser before calling the API', async ({ page }) => {
  await page.goto('/register');
  let called = false;
  page.on('request', (request) => { if (request.url().includes('/api/auth/register')) called = true; });
  await page.getByRole('button', { name: 'Đăng ký' }).click();
  await expect(page.getByText('Email không hợp lệ')).toBeVisible();
  await page.getByLabel('Email').fill(`e2e-${unique()}@hk5.local`);
  await page.getByLabel('Mật khẩu').fill('short');
  await page.getByRole('button', { name: 'Đăng ký' }).click();
  await expect(page.getByText('Mật khẩu tối thiểu 12 ký tự')).toBeVisible();
  expect(called).toBe(false);
});

test('a guest is sent to login and returned to the page', async ({ page }) => {
  await page.goto('/orders');
  await expect(page).toHaveURL(/\/login\?next=%2Forders/);
  await page.getByLabel('Email').fill(accounts.customer.email);
  await page.getByLabel('Mật khẩu').fill(accounts.customer.password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đơn hàng của tôi' })).toBeVisible();
});

test('a wrong password shows the server error', async ({ page }) => {
  await logIn(page, accounts.customer).catch(() => undefined);
  await page.getByRole('button', { name: 'Đăng xuất' }).click();
  // Leaving before logout finishes would restore the session and redirect away from /login.
  await page.getByRole('link', { name: 'Đăng nhập', exact: true }).waitFor();
  await page.goto('/login');
  await page.getByLabel('Email').fill(accounts.customer.email);
  await page.getByLabel('Mật khẩu').fill('wrong-password-123');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
});

test('a customer cannot open seller or admin pages', async ({ page }) => {
  await logIn(page, accounts.customer);
  for (const path of ['/admin/sellers', '/seller/products']) {
    await page.goto(path);
    await expect(page.getByText('Tài khoản của bạn không có quyền truy cập trang này.')).toBeVisible();
  }
});

test('an admin hides a category from the public tree only', async ({ browser, request }) => {
  const name = `E2E ẩn ${Date.now()}`;
  const admin = await sessionFor(browser, accounts.admin);
  await admin.goto('/admin/categories');
  await admin.getByLabel('Tên danh mục').fill(name);
  await admin.getByRole('button', { name: 'Thêm' }).click();
  const status = admin.getByLabel(`Trạng thái ${name}`);
  await status.selectOption('HIDDEN');
  await expect(status).toHaveValue('HIDDEN');
  await expect.poll(async () => {
    const rows = await (await request.get('/api/categories')).json() as { name: string }[];
    return rows.some((row) => row.name === name);
  }).toBe(false);
  await admin.reload();
  await expect(admin.getByLabel(`Trạng thái ${name}`)).toHaveValue('HIDDEN');
  await admin.getByRole('row').filter({ has: admin.getByLabel(`Tên ${name}`) }).getByRole('button', { name: 'Xóa' }).click();
  await expect(admin.getByLabel(`Tên ${name}`)).toHaveCount(0);
  await admin.context().close();
});

test('an admin locks an account, ending its session, and unlocks it', async ({ browser, page }) => {
  const account = { email: `e2e-${unique()}@hk5.local`, password: accounts.customer.password };
  await page.goto('/register');
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Mật khẩu').fill(account.password);
  await page.getByRole('button', { name: 'Đăng ký' }).click();
  await page.getByRole('button', { name: 'Đăng xuất' }).waitFor();

  const admin = await sessionFor(browser, accounts.admin);
  await admin.goto('/admin/accounts');
  await admin.getByLabel('Tìm theo email').fill(account.email);
  const row = admin.getByRole('row', { name: account.email });
  admin.once('dialog', (dialog) => void dialog.accept());
  await row.getByRole('button', { name: 'Khóa', exact: true }).click();
  await expect(row.getByText('Đã khóa')).toBeVisible();

  await page.reload();
  await expect(page.getByRole('link', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await page.goto('/login');
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Mật khẩu').fill(account.password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();

  await row.getByRole('button', { name: 'Mở khóa' }).click();
  await expect(row.getByText('Hoạt động')).toBeVisible();
  await logIn(page, account);
});

test('a signed-in customer is not sent to login while the session is restoring', async ({ browser }) => {
  const customer = await sessionFor(browser, accounts.customer);
  const { items } = await (await customer.request.get('/api/catalog/products?size=1')).json();
  await customer.route('**/api/auth/refresh', async (route) => {
    await new Promise((done) => setTimeout(done, 1500));
    await route.continue();
  });
  await customer.goto(`/products/${items[0].id}`);
  const add = customer.getByRole('button', { name: 'Thêm vào giỏ hàng' });
  await expect(add).toBeVisible();
  await expect(add).toBeDisabled();
  await expect(add).toBeEnabled();
});

test('a page chunk that fails to load offers a reload instead of a blank app', async ({ page }) => {
  await page.goto('/login');
  await page.route('**/assets/CatalogPage-*', (route) => route.abort());
  await page.getByRole('search').getByRole('button').click();
  await expect(page.getByRole('alert')).toContainText('Không tải được trang này');
  await expect(page.getByRole('button', { name: 'Tải lại' })).toBeVisible();
  await expect(page.getByRole('search')).toBeVisible();
});

test('the home banner holds its slide while the pointer is on it', async ({ page }) => {
  await page.goto('/');
  const banner = page.getByRole('region', { name: 'Giới thiệu Soopi' });
  const title = await banner.getByRole('heading').textContent();
  await banner.hover();
  await page.waitForTimeout(5500);
  await expect(banner.getByRole('heading')).toHaveText(title!);
  await page.mouse.move(0, 0);
  await expect(banner.getByRole('heading')).not.toHaveText(title!, { timeout: 7000 });
});

test('a deep link and a refresh both load the app', async ({ page }) => {
  await page.goto('/products?q=ao');
  await expect(page.getByRole('search')).toBeVisible();
  await page.reload();
  await expect(page.getByRole('search')).toBeVisible();
});
