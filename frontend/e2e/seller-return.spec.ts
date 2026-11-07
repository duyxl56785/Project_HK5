import { expect, test, type Page } from '@playwright/test';
import { accounts, sessionFor, unique } from './support';

/**
 * #153/#156: Seller Return workflow through the real Gateway on the integrated #181+#182+#183 stack.
 * No restock or refund is checked here: neither exists yet.
 */
async function bearer(page: Page, path: string) {
  const sent = page.waitForRequest(r => r.url().includes('/api/') && !!r.headers().authorization);
  await page.goto(path);
  return (await sent).headers().authorization;
}

test('Seller approves and receives a Return; other roles are refused', async ({ browser, request }) => {
  const customer = await sessionFor(browser, accounts.customer);
  const seller = await sessionFor(browser, accounts.seller);
  const admin = await sessionFor(browser, accounts.admin);
  try {
    const catalog = await (await customer.request.get('/api/catalog/products?size=50')).json() as { items: {
      id: string; store: { name: string };
    }[] };
    const product = catalog.items.find(p => p.store.name === 'Tiệm Áo Sài Gòn');
    expect(product, 'Seed must include a buyable Store-1 product').toBeTruthy();
    await customer.goto(`/products/${product!.id}`);
    await customer.getByRole('button', { name: 'Mua ngay' }).click();
    await customer.getByRole('link', { name: 'Tiến hành đặt hàng' }).click();
    await customer.getByLabel('Người nhận').fill('Khách Return Seller E2E');
    await customer.getByLabel('Số điện thoại').fill('0901234567');
    await customer.getByLabel('Địa chỉ', { exact: true }).fill('Địa chỉ dữ liệu giả');
    await customer.getByRole('button', { name: /đặt hàng/i }).click();
    await expect(customer).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);
    const orderId = customer.url().split('/').pop()!;
    const code = orderId.slice(0, 8).toUpperCase();

    await seller.goto('/seller/orders');
    const card = seller.locator('article').filter({ hasText: `Đơn #${code}` });
    for (const step of ['Xác nhận đơn', 'Bắt đầu chuẩn bị', 'Sẵn sàng bàn giao', 'Giao cho vận chuyển', 'Xác nhận đã giao']) {
      await card.getByRole('button', { name: step }).click();
    }
    await expect(card.getByText('Đã giao')).toBeVisible();

    await customer.reload();
    const reason = `Sai màu ${unique()}`;
    await customer.getByLabel('Lý do trả hàng').fill(reason);
    await customer.getByRole('button', { name: 'Gửi yêu cầu trả hàng' }).click();
    await expect(customer.getByText('Đã yêu cầu trả')).toBeVisible();

    await seller.goto('/seller/returns');
    const row = seller.locator('article').filter({ hasText: reason });
    await row.getByRole('button', { name: 'Đồng ý trả hàng' }).click();
    const receipt = `VD-${unique()}`;
    await row.getByLabel('Mã nhận hàng').fill(receipt);
    await row.getByRole('button', { name: 'Xác nhận đã nhận hàng' }).click();
    await expect(row.getByText(`Mã nhận hàng: ${receipt}`)).toBeVisible();
    await seller.reload();
    await expect(seller.locator('article').filter({ hasText: reason }).getByText(`Mã nhận hàng: ${receipt}`)).toBeVisible();

    const adminAuth = await bearer(admin, '/admin/returns');
    const adminRows = await (await request.get('/api/admin/returns?status=RECEIVED', { headers: { Authorization: adminAuth } }))
      .json() as { reason: string }[];
    expect(adminRows.some(r => r.reason === reason)).toBe(true);

    const customerAuth = await bearer(customer, '/orders');
    for (const [who, auth] of [['customer', customerAuth], ['admin', adminAuth]] as const) {
      expect((await request.get('/api/seller/returns', { headers: { Authorization: auth } })).status(),
        `${who} must not list Seller Returns`).toBe(403);
    }
    const sellerAuth = await bearer(seller, '/seller/returns');
    const missing = await request.post('/api/seller/returns/00000000-0000-4000-8000-000000000000/approve',
      { headers: { Authorization: sellerAuth } });
    expect(missing.status()).toBe(404);
    expect((await request.get('/api/seller/returns')).status()).toBe(401);
  } finally {
    await customer.context().close();
    await seller.context().close();
    await admin.context().close();
  }
});
