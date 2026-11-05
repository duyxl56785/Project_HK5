import { expect, test } from '@playwright/test';
import { accounts, sessionFor, unique } from './support';

/**
 * TV3 #155: real-browser Customer→Gateway→Order Return smoke on the disposable
 * scripts/demo/up.mjs stack. No route mocking or fabricated responses.
 * NOT a TV4 restock/refund/UNKNOWN or final 2-Store UAT acceptance.
 */
test('delivered buyer requests one Return and replay with the same key keeps its ID', async ({ browser }) => {
  const customer = await sessionFor(browser, accounts.customer);
  const seller = await sessionFor(browser, accounts.seller);
  try {
    const catalogResponse = await customer.request.get('/api/catalog/products?size=50');
    expect(catalogResponse.ok()).toBeTruthy();
    const catalog = await catalogResponse.json() as { items: {
      id: string; store: { name: string };
    }[] };
    const product = catalog.items.find(p => p.store.name === 'Tiệm Áo Sài Gòn');
    expect(product, 'Seed must include a buyable Store-1 product').toBeTruthy();

    await customer.goto(`/products/${product!.id}`);
    await customer.getByRole('button', { name: 'Mua ngay' }).click();
    await customer.getByRole('link', { name: 'Tiến hành đặt hàng' }).click();
    await customer.getByLabel('Người nhận').fill('Khách Return E2E');
    await customer.getByLabel('Số điện thoại').fill('0901234567');
    await customer.getByLabel('Địa chỉ', { exact: true }).fill('Địa chỉ dữ liệu giả');
    await customer.getByRole('button', { name: /đặt hàng/i }).click();
    await expect(customer).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);

    const orderId = customer.url().split('/').pop()!;
    const code = orderId.slice(0, 8).toUpperCase();
    await seller.goto('/seller/orders');
    const card = seller.locator('article').filter({ hasText: `Đơn #${code}` });
    for (const step of ['Xác nhận đơn', 'Bắt đầu chuẩn bị',
      'Sẵn sàng bàn giao', 'Giao cho vận chuyển', 'Xác nhận đã giao']) {
      await card.getByRole('button', { name: step }).click();
    }
    await expect(card.getByText('Đã giao')).toBeVisible();

    await customer.reload();
    await expect(customer.getByRole('button', { name: 'Gửi yêu cầu trả hàng' })).toBeVisible();
    const reason = `Sản phẩm khác mô tả ${unique()}`;
    await customer.getByLabel('Lý do trả hàng').fill(reason);
    const sentRequest = customer.waitForRequest(r => r.url().includes('/store-orders/')
      && r.url().endsWith('/returns') && r.method() === 'POST');
    const acceptedResponse = customer.waitForResponse(r => r.url().includes('/store-orders/')
      && r.url().endsWith('/returns') && r.request().method() === 'POST');
    await customer.getByRole('button', { name: 'Gửi yêu cầu trả hàng' }).click();

    const wire = await sentRequest;
    const first = await acceptedResponse;
    expect(first.status()).toBe(201);
    const created = await first.json() as { requestId: string; storeOrderId: string; status: string };
    expect(created.requestId).toMatch(/^[0-9a-f-]{36}$/);
    expect(created.status).toBe('REQUESTED');
    // Read authorization only inside the test process; never log or persist it.
    const headers = await wire.allHeaders();
    expect(headers.authorization).toMatch(/^Bearer /);
    expect(headers['idempotency-key']).toBeTruthy();

    await expect(customer.getByText('Đã yêu cầu trả')).toBeVisible();
    const repeat = await customer.request.post(`/api/orders/${orderId}/store-orders/${created.storeOrderId}/returns`, {
      headers: { Authorization: headers.authorization, 'Idempotency-Key': headers['idempotency-key'] },
      data: { reason },
    });
    expect(repeat.status()).toBe(200);
    expect((await repeat.json() as { requestId: string }).requestId).toBe(created.requestId);

    const changed = await customer.request.post(`/api/orders/${orderId}/store-orders/${created.storeOrderId}/returns`, {
      headers: { Authorization: headers.authorization, 'Idempotency-Key': headers['idempotency-key'] },
      data: { reason: 'Lý do khác' },
    });
    expect(changed.status()).toBe(409);
    expect((await changed.json() as { code: string }).code).toBe('RETURN_IDEMPOTENCY_MISMATCH');

    const read = await customer.request.get(`/api/orders/${orderId}/returns`, {
      headers: { Authorization: headers.authorization },
    });
    expect(read.status()).toBe(200);
    const rows = await read.json() as { requestId: string }[];
    expect(rows.filter(r => r.requestId === created.requestId)).toHaveLength(1);
  } finally {
    await customer.context().close();
    await seller.context().close();
  }
});
