import { expect, test } from '@playwright/test';
import { accounts, sessionFor, unique } from './support';

interface Offer { readonly id: string; readonly size: string; readonly color: string; readonly available: number }

test('a seller lists a shirt in sizes and colors and a customer buys the chosen one with COD', async ({ browser }) => {
  const id = unique();
  const name = `Áo thun biến thể ${id}`;
  const seller = await sessionFor(browser, accounts.seller);

  await test.step('seller creates one product with a size/color table', async () => {
    await seller.goto('/seller/products/new');
    await seller.getByLabel('Danh mục').selectOption({ label: 'Thời trang' });
    await seller.getByLabel('Sản phẩm có size/màu').check();
    await expect(seller.getByLabel('SKU', { exact: true })).toHaveCount(0);
    await seller.getByLabel('Tên sản phẩm').fill(name);
    await seller.getByLabel('Mô tả').fill('Áo thun kiểm thử biến thể');
    await seller.getByRole('button', { name: 'Lưu', exact: true }).click();
    await expect(seller.getByText('Nhập size hoặc màu rồi bấm “Tạo bảng biến thể”.')).toBeVisible();

    await seller.getByLabel('Các size, cách nhau bằng dấu phẩy').fill('M, L');
    await seller.getByLabel('Các màu, cách nhau bằng dấu phẩy').fill('Đỏ, Xanh');
    await seller.getByLabel('Tiền tố SKU').fill(`E2E-V-${id}`);
    await seller.getByLabel('Giá chung (VND)').fill('150000');
    await seller.getByRole('button', { name: 'Tạo bảng biến thể' }).click();
    await expect(seller.getByLabel('SKU L / Xanh')).toHaveValue(`E2E-V-${id.toUpperCase()}-L-XANH`);
    await seller.getByLabel('Giá L / Xanh').fill('170000');
    for (const label of ['M / Đỏ', 'M / Xanh', 'L / Đỏ']) await seller.getByLabel(`Tồn kho ${label}`).fill('5');
    await seller.getByLabel('Tồn kho L / Xanh').fill('3');
    await seller.getByRole('button', { name: 'Lưu', exact: true }).click();

    await expect(seller).toHaveURL(/\/seller\/products\/[0-9a-f-]{36}$/);
    await expect(seller.getByRole('heading', { name: `${name} (M / Đỏ)` })).toBeVisible();
    await expect(seller.getByRole('heading', { name: 'Biến thể (4)' })).toBeVisible();
    await seller.getByRole('button', { name: 'Gửi duyệt' }).click();
    await expect(seller.getByText('Chờ duyệt').first()).toBeVisible();
  });
  const leadId = seller.url().split('/').pop()!;

  await test.step('admin approves the first variant and the group follows', async () => {
    const admin = await sessionFor(browser, accounts.admin);
    await admin.goto('/admin/products');
    await admin.getByRole('row').filter({ hasText: `${name} (M / Đỏ)` }).getByRole('button', { name: 'Duyệt' }).click();
    await expect(async () => {
      await admin.reload();
      await expect(admin.getByRole('row').filter({ hasText: name })).toHaveCount(0, { timeout: 1000 });
    }).toPass({ timeout: 15_000 });
    await admin.context().close();
  });

  const customer = await sessionFor(browser, accounts.customer);
  await test.step('the catalog lists the shirt once, by its shared name', async () => {
    await expect(async () => {
      await customer.goto(`/products?q=${encodeURIComponent(name)}`);
      await expect(customer.getByRole('link', { name })).toHaveCount(1, { timeout: 2000 });
    }).toPass({ timeout: 30_000 });
    await expect(customer.getByText(`${name} (`)).toHaveCount(0);
  });

  await test.step('customer picks L / Xanh and orders it with COD', async () => {
    await customer.goto(`/products/${leadId}`);
    await expect(customer.getByRole('heading', { name, exact: true })).toBeVisible();
    await customer.getByRole('group', { name: 'Size' }).getByRole('button', { name: 'L', exact: true }).click();
    await customer.getByRole('group', { name: 'Màu' }).getByRole('button', { name: 'Xanh', exact: true }).click();
    await expect(customer.getByText('170.000 ₫')).toBeVisible();
    await expect(customer.getByText('Còn 3 sản phẩm')).toBeVisible();
    await customer.getByRole('button', { name: 'Mua ngay' }).click();

    await expect(customer).toHaveURL(/\/cart$/);
    await expect(customer.getByText(`${name} (L / Xanh)`)).toBeVisible();
    await customer.getByRole('link', { name: 'Tiến hành đặt hàng' }).click();
    await customer.getByLabel('Người nhận').fill('Khách biến thể');
    await customer.getByLabel('Số điện thoại').fill('0901234567');
    await customer.getByLabel('Địa chỉ', { exact: true }).fill('1 Lê Lợi, Quận 1');
    await customer.getByRole('button', { name: /đặt hàng/i }).click();
    await expect(customer).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);
    await expect(customer.getByRole('main').getByText('Đang xử lý')).toBeVisible();
    await expect(customer.getByRole('link', { name: `${name} (L / Xanh)` })).toBeVisible();
  });

  await test.step('only the chosen variant lost stock', async () => {
    await expect(async () => {
      const detail = await (await customer.request.get(`/api/catalog/products/${leadId}`)).json() as { variants: Offer[] };
      const stock = Object.fromEntries(detail.variants.map((v) => [`${v.size} / ${v.color}`, v.available]));
      expect(stock).toEqual({ 'M / Đỏ': 5, 'M / Xanh': 5, 'L / Đỏ': 5, 'L / Xanh': 2 });
    }).toPass({ timeout: 30_000 });
  });

  await test.step('stopping the first variant takes the whole shirt off sale', async () => {
    await seller.goto(`/seller/products/${leadId}`);
    await seller.getByRole('button', { name: 'Ngừng bán' }).click();
    await expect(seller.getByRole('button', { name: 'Bán lại' })).toBeVisible();
    await expect(async () => {
      expect((await customer.request.get(`/api/catalog/products/${leadId}`)).status()).toBe(404);
    }).toPass({ timeout: 30_000 });
    await seller.context().close();
    await customer.context().close();
  });
});
