import { expect, test, type Page } from '@playwright/test';
import { accounts, sessionFor } from './support';

/**
 * #153: wrong-role calls to Return APIs through the real Gateway are refused.
 * Seller Return routes (#182) are not on main yet; add them here when they land.
 */
async function bearer(page: Page, path: string) {
  const sent = page.waitForRequest(r => r.url().includes('/api/') && !!r.headers().authorization);
  await page.goto(path);
  return (await sent).headers().authorization;
}

const someId = '00000000-0000-4000-8000-000000000000';

test('Return APIs refuse the wrong role and anonymous callers', async ({ browser, request }) => {
  const customer = await sessionFor(browser, accounts.customer);
  const seller = await sessionFor(browser, accounts.seller);
  try {
    const customerAuth = await bearer(customer, '/orders');
    const sellerAuth = await bearer(seller, '/seller/orders');

    // Every account also holds CUSTOMER, so the buyer Return API is guarded by ownership:
    // another buyer's real order answers 404 and never reveals that it exists.
    // The order is delivered here, so its owner could ask for a Return and only ownership refuses.
    const catalog = await (await customer.request.get('/api/catalog/products?size=50')).json() as { items: {
      id: string; store: { name: string };
    }[] };
    const product = catalog.items.find(p => p.store.name === 'Tiệm Áo Sài Gòn');
    expect(product, 'Seed must include a buyable Store-1 product').toBeTruthy();
    await customer.goto(`/products/${product!.id}`);
    await customer.getByRole('button', { name: 'Mua ngay' }).click();
    await customer.getByRole('link', { name: 'Tiến hành đặt hàng' }).click();
    await customer.getByLabel('Người nhận').fill('Khách kiểm quyền Return');
    await customer.getByLabel('Số điện thoại').fill('0901234567');
    await customer.getByLabel('Địa chỉ', { exact: true }).fill('Địa chỉ dữ liệu giả');
    await customer.getByRole('button', { name: /đặt hàng/i }).click();
    await expect(customer).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);
    const orderId = customer.url().split('/').pop()!;
    await seller.goto('/seller/orders');
    const card = seller.locator('article').filter({ hasText: `Đơn #${orderId.slice(0, 8).toUpperCase()}` });
    for (const step of ['Xác nhận đơn', 'Bắt đầu chuẩn bị', 'Sẵn sàng bàn giao', 'Giao cho vận chuyển', 'Xác nhận đã giao']) {
      await card.getByRole('button', { name: step }).click();
    }
    await expect(card.getByText('Đã giao')).toBeVisible();
    const order = await (await request.get(`/api/orders/${orderId}`, { headers: { Authorization: customerAuth } }))
      .json() as { storeOrders: { id: string }[] };
    const [{ id: storeOrderId }] = order.storeOrders;
    const otherCreates = await request.post(`/api/orders/${orderId}/store-orders/${storeOrderId}/returns`, {
      headers: { Authorization: sellerAuth, 'Idempotency-Key': `role-${Date.now()}` }, data: { reason: 'Không phải đơn của tôi' },
    });
    expect(otherCreates.status()).toBe(404);
    expect((await otherCreates.json() as { code: string }).code).toBe('ORDER_NOT_FOUND');
    const otherReads = await request.get(`/api/orders/${orderId}/returns`, { headers: { Authorization: sellerAuth } });
    expect(otherReads.status()).toBe(404);

    for (const [who, auth] of [['customer', customerAuth], ['seller', sellerAuth]] as const) {
      const list = await request.get('/api/admin/returns', { headers: { Authorization: auth } });
      expect(list.status(), `${who} must not read Admin Returns`).toBe(403);
      const one = await request.get(`/api/admin/returns/${someId}`, { headers: { Authorization: auth } });
      expect(one.status(), `${who} must not read one Admin Return`).toBe(403);
    }

    expect((await request.get('/api/admin/returns')).status()).toBe(401);
    expect((await request.get(`/api/orders/${someId}/returns`)).status()).toBe(401);
  } finally {
    await customer.context().close();
    await seller.context().close();
  }
});
