import { expect, test } from '@playwright/test';
import { accounts, sessionFor, unique } from './support';

test('an order goes from checkout to delivery, review, COD and payout', async ({ browser }) => {
  const customer = await sessionFor(browser, accounts.customer);
  const catalog = await (await customer.request.get('/api/catalog/products?size=50')).json();
  const product = catalog.items.find((item: { store: { name: string } }) => item.store.name === 'Tiệm Áo Sài Gòn');
  expect(product).toBeTruthy();

  await test.step('customer checks out', async () => {
    await customer.goto(`/products/${product.id}`);
    await customer.getByRole('button', { name: 'Mua ngay' }).click();
    await customer.getByRole('link', { name: 'Tiến hành đặt hàng' }).click();
    await customer.getByLabel('Người nhận').fill('Khách E2E');
    await customer.getByLabel('Số điện thoại').fill('abc');
    await customer.getByLabel('Người nhận').focus();
    await expect(customer.getByText('Số điện thoại không hợp lệ')).toBeVisible();
    await customer.getByLabel('Số điện thoại').fill('0901234567');
    await customer.getByLabel('Địa chỉ', { exact: true }).fill('1 Lê Lợi, Quận 1');
    await customer.getByRole('button', { name: /đặt hàng/i }).click();
    await expect(customer).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);
  });
  const code = customer.url().split('/').pop()!.slice(0, 8).toUpperCase();

  await test.step('seller receives the notice and delivers', async () => {
    const seller = await sessionFor(browser, accounts.seller);
    await expect(async () => {
      await seller.goto('/notifications');
      await expect(seller.getByText(`Đơn ${code}`).first()).toBeVisible({ timeout: 1000 });
    }).toPass({ timeout: 30_000 });
    await seller.goto('/seller/orders');
    const card = seller.locator('article').filter({ hasText: `Đơn #${code}` });
    for (const step of ['Xác nhận đơn', 'Bắt đầu chuẩn bị', 'Sẵn sàng bàn giao', 'Giao cho vận chuyển', 'Xác nhận đã giao']) {
      await card.getByRole('button', { name: step }).click();
    }
    await expect(card.getByText('Đã giao')).toBeVisible();
    await seller.context().close();
  });

  await test.step('customer reviews once the delivered event arrives', async () => {
    const comment = `Tốt ${unique()}`;
    await expect(async () => {
      await customer.reload();
      await customer.getByLabel('Nhận xét').first().fill(comment);
      await customer.getByRole('button', { name: 'Gửi' }).first().click();
      await expect(customer.getByText('Đã đánh giá').first()).toBeVisible({ timeout: 2000 });
    }).toPass({ timeout: 30_000 });
  });

  await test.step('admin collects COD and pays the seller', async () => {
    const admin = await sessionFor(browser, accounts.admin);
    await admin.goto('/admin/orders');
    const row = admin.getByRole('row').filter({ hasText: `#${code}` });
    await row.getByRole('button', { name: 'Xác nhận đã thu' }).click();
    await row.getByRole('button', { name: /^Thanh toán/ }).click();
    await expect(row.getByText(/Đã thanh toán/)).toBeVisible();
    await admin.context().close();
  });
});
