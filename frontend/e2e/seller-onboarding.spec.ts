import { expect, test } from '@playwright/test';
import { accounts, env, logIn, sessionFor, unique } from './support';

test('an applicant is rejected, resubmits, opens a store and gets a product on sale', async ({ browser, page }) => {
  const id = unique();
  const owner = `Người bán ${id}`;
  const productName = `Sản phẩm ${id}`;

  await page.goto('/register');
  await page.getByLabel('Email').fill(`e2e-${id}@hk5.local`);
  await page.getByLabel('Mật khẩu').fill(env.SEED_PASSWORD);
  await page.getByRole('button', { name: 'Đăng ký' }).click();
  await page.getByRole('button', { name: 'Đăng xuất' }).waitFor();

  await page.goto('/seller');
  await page.getByRole('button', { name: 'Gửi hồ sơ người bán' }).click();
  await expect(page.getByText('Nhập tên người đại diện')).toBeVisible();
  await page.getByLabel('Tên người đại diện').fill(owner);
  await page.getByLabel('Số điện thoại').fill('0912345678');
  await page.getByLabel('Địa chỉ lấy hàng').fill('2 Hai Bà Trưng');
  await page.getByRole('button', { name: 'Gửi hồ sơ người bán' }).click();

  const admin = await sessionFor(browser, accounts.admin);
  await admin.goto('/admin/sellers');
  const row = admin.getByRole('row').filter({ hasText: owner });
  await row.getByLabel('Lý do').fill('Sai địa chỉ');
  await row.getByRole('button', { name: 'Từ chối' }).click();

  await expect(async () => {
    await page.reload();
    await expect(page.getByText('Lý do từ quản trị viên: Sai địa chỉ')).toBeVisible({ timeout: 1000 });
  }).toPass({ timeout: 15_000 });
  await page.getByLabel('Địa chỉ lấy hàng').fill('3 Hai Bà Trưng');
  await page.getByRole('button', { name: 'Gửi lại hồ sơ' }).click();

  await admin.reload();
  await row.getByRole('button', { name: 'Duyệt' }).click();
  await admin.getByLabel('Trạng thái').selectOption({ label: 'Tất cả' });
  await row.getByRole('button', { name: 'Lịch sử' }).click();
  await expect(row.getByRole('listitem')).toHaveCount(4);
  await expect(row.getByText('Sai địa chỉ')).toBeVisible();

  await expect(async () => {
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Tạo cửa hàng' })).toBeVisible({ timeout: 2000 });
  }).toPass({ timeout: 45_000 });
  await page.getByLabel('Tên cửa hàng').fill(`Cửa hàng ${id}`);
  await expect(page.getByLabel('Đường dẫn')).toHaveValue(`cua-hang-${id}`);
  await page.getByRole('button', { name: 'Lưu cửa hàng' }).click();
  await expect(page.getByRole('heading', { name: 'Thông tin cửa hàng' })).toBeVisible();

  await page.goto('/seller/products/new');
  await page.getByLabel('Danh mục').selectOption({ index: 1 });
  await page.getByLabel('SKU').fill(`E2E-${id}`);
  await page.getByLabel('Tên sản phẩm').fill(productName);
  await page.getByLabel('Giá (VND)').fill('12.5');
  await page.getByLabel('Mô tả').fill('Sản phẩm kiểm thử');
  await page.getByLabel('Tồn kho ban đầu').fill('5');
  await page.getByRole('button', { name: 'Lưu', exact: true }).click();
  await expect(page.getByText(/Giá là số nguyên/)).toBeVisible();
  await page.getByLabel('Giá (VND)').fill('150000');
  await page.getByRole('button', { name: 'Lưu', exact: true }).click();
  await page.getByRole('button', { name: 'Gửi duyệt' }).click();
  await expect(page.getByText('Chờ duyệt').first()).toBeVisible();

  await admin.goto('/admin/products');
  await admin.getByRole('row').filter({ hasText: productName }).getByRole('button', { name: 'Duyệt' }).click();
  await admin.context().close();

  const customer = await (await browser.newContext()).newPage();
  await logIn(customer, accounts.customer);
  await expect(async () => {
    await customer.goto(`/products?q=${encodeURIComponent(productName)}`);
    await expect(customer.getByText(productName).first()).toBeVisible({ timeout: 2000 });
  }).toPass({ timeout: 30_000 });
});
