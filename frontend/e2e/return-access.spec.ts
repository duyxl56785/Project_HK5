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
    // Uses an order left by order-journey.spec.ts (workers: 1, files run in name order).
    const orders = await (await request.get('/api/orders', { headers: { Authorization: customerAuth } }))
      .json() as { id: string; storeOrders: { id: string }[] }[];
    expect(orders.length, 'customer needs one order from an earlier spec').toBeGreaterThan(0);
    const [{ id: orderId, storeOrders: [{ id: storeOrderId }] }] = orders;
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
