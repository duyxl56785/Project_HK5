import { expect, test } from '@playwright/test';
import type { APIRequestContext } from '@playwright/test';
import { accounts, env, sessionFor, unique } from './support';

const origin = process.env.E2E_BASE_URL;
const allowed = process.env.E2E_VOUCHER_C2 === '1'
  && /^hk5-c2-qa-\d{8}$/.test(process.env.E2E_C2_PROJECT ?? '')
  && /^http:\/\/(localhost|127\.0\.0\.1):25173$/.test(origin ?? '');
const format = (amount: bigint) => new Intl.NumberFormat('vi-VN').format(amount) + ' ₫';
const vnd = (amount: string | number) => BigInt(String(amount).replace(/\.0+$/, ''));

test('C2 uses two Store vouchers and an identical replay returns the same customer order', async ({ browser, request }) => {
  test.skip(!allowed, 'Requires the separate, loopback-only C2 QA project');
  test.setTimeout(180_000);
  const customerAccount = { email: 'c2-' + unique() + '@hk5.local', password: env.SEED_PASSWORD };
  const headers = { Origin: origin!, 'X-CSRF-Protection': '1' };
  const login = async (account: typeof customerAccount) => {
    const response = await request.post('/api/auth/login', { headers, data: account });
    expect(response.status()).toBe(200);
    return { ...headers, Authorization: 'Bearer ' + (await response.json()).accessToken };
  };
  const register = await request.post('/api/auth/register', { headers, data: customerAccount });
  expect(register.status()).toBe(201);
  const customerHeaders = await login(customerAccount);
  const response = await request.get('/api/catalog/products?size=50');
  expect(response.status()).toBe(200);
  type FixtureProduct = { id: string; name: string; unitPrice: number; store: { id: string; name: string }; available: number; promotion?: unknown };
  const products: FixtureProduct[] = (await response.json()).items;
  const stores = ['Tiệm Áo Sài Gòn', 'Nhà Xinh'];
  const discounts = [10000n, 5000n];
  const fixture = stores.map((name, i) => products.find(p =>
    p.store.name === name && p.available > 0 && !p.promotion && vnd(p.unitPrice) > discounts[i]));
  expect(fixture.filter(Boolean)).toHaveLength(2);
  const secondAccount = { email: 'seller2@hk5.local', password: env.SEED_PASSWORD };
  const sellerHeaders = [await login(accounts.seller), await login(secondAccount)];
  expect((await request.get('/api/orders/voucher-capability')).status()).toBe(401);
  expect((await request.get('/api/orders/voucher-capability', { headers: sellerHeaders[0] })).status()).toBe(403);
  expect(await (await request.get('/api/orders/voucher-capability', { headers: customerHeaders })).json())
    .toEqual({ enabled: true });
  const prefix = 'C2QA' + unique().toUpperCase();
  const liveCodes = [prefix + 'A', prefix + 'B'];
  for (const [i, code] of liveCodes.entries()) {
    const created = await request.post('/api/stores/me/vouchers', { headers: sellerHeaders[i], data: {
      code, name: 'C2 isolated QA', discountType: 'FIXED', discountValue: Number(discounts[i]),
      minOrderAmount: Number(discounts[i]) + 1, usageLimit: 1,
      startsAt: new Date(Date.now() - 3600000).toISOString(), endsAt: new Date(Date.now() + 86400000).toISOString(),
    } });
    expect(created.status()).toBe(201);
    const { id, version } = await created.json();
    const published = await request.post('/api/stores/me/vouchers/' + id + '/publish',
      { headers: sellerHeaders[i], data: { expectedVersion: version } });
    expect(published.status()).toBe(200);
    expect((await published.json()).status).toBe('ACTIVE');
  }

  const stock = async (req: APIRequestContext, p: FixtureProduct) =>
    (await (await req.get('/api/catalog/products/' + p.id)).json()).available;
  const before = await Promise.all(fixture.map(p => stock(request, p!)));
  const customer = await sessionFor(browser, customerAccount);
  try {
    for (const product of fixture) {
      await customer.goto('/products/' + product!.id);
      const added = customer.waitForResponse(r => r.url().endsWith('/api/cart/items') && r.request().method() === 'POST');
      await customer.getByRole('button', { name: 'Thêm vào giỏ hàng' }).click();
      expect((await added).ok()).toBeTruthy();
    }
    await customer.goto('/cart');
    await expect(customer.locator('article.tv3-cart__store')).toHaveCount(2);
    await customer.getByRole('link', { name: 'Tiến hành đặt hàng' }).click();
    await customer.getByLabel('Người nhận').fill('Khách QA');
    await customer.getByLabel('Số điện thoại').fill('0900000000');
    await customer.getByLabel('Địa chỉ', { exact: true }).fill('Địa chỉ thử nghiệm');
    const first = customer.locator('article').filter({ hasText: stores[0] });
    await first.getByLabel(/Mã voucher/).fill(liveCodes[1]);
    await first.getByRole('button', { name: 'Áp dụng voucher' }).click();
    await expect(customer.getByRole('alert')).toContainText(/Không tìm thấy mã|không thuộc cửa hàng/);
    for (const [i, code] of liveCodes.entries()) {
      const card = customer.locator('article').filter({ hasText: stores[i] });
      await card.getByLabel(/Mã voucher/).fill(code);
      await card.getByRole('button', { name: 'Áp dụng voucher' }).click();
      await expect(card.getByLabel('Báo giá voucher')).toContainText(format(discounts[i]));
    }
    const expected = fixture.map((p, i) => vnd(p!.unitPrice) - discounts[i]);
    await expect(customer.locator('.tv3-checkout__summary')).toContainText(format(expected[0] + expected[1]));
    const placed = customer.waitForResponse(r => r.url().endsWith('/api/orders') && r.request().method() === 'POST');
    await customer.getByRole('button', { name: 'Xác nhận đặt hàng COD' }).click();
    const placedResponse = await placed;
    expect(placedResponse.status()).toBe(200);
    const result = await placedResponse.json();
    const key = placedResponse.request().headers()['idempotency-key'];
    const payload = placedResponse.request().postDataJSON();
    expect(key).toBeTruthy();
    expect(payload.vouchers).toHaveLength(2);
    await expect(customer).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);
    await expect(customer.locator('article')).toHaveCount(2);
    const order = await request.get('/api/orders/' + result.orderId, { headers: customerHeaders });
    expect(order.status()).toBe(200);
    const detail = await order.json();
    expect(detail.storeOrders).toHaveLength(2);
    expect(new Set(detail.storeOrders.map((child: { id: string }) => child.id)).size).toBe(2);
    const children = detail.storeOrders;
    for (const [i, product] of fixture.entries()) {
      const child = children.find((item: { storeName: string }) => item.storeName === product!.store.name);
      expect(child.orderId).toBe(result.orderId);
      expect(child.voucherCode).toBe(liveCodes[i]);
      expect(vnd(child.grossExact)).toBe(expected[i]);
      expect(vnd(child.voucherDiscount)).toBe(discounts[i]);
      expect(vnd(child.platformFee)).toBe((expected[i] * 5n + 50n) / 100n);
    }
    const checkoutHeaders = { ...customerHeaders, 'Idempotency-Key': key };
    const replay = await request.post('/api/orders', { headers: checkoutHeaders, data: payload });
    expect(replay.status()).toBe(200);
    expect((await replay.json()).orderId).toBe(result.orderId);
    const changed = await request.post('/api/orders', { headers: checkoutHeaders,
      data: { ...payload, vouchers: [payload.vouchers[0]] } });
    expect(changed.status()).toBe(409);
    await expect(async () => {
      expect(await Promise.all(fixture.map(p => stock(request, p!)))).toEqual(before.map(n => n - 1));
    }).toPass({ timeout: 35_000 });
  } finally {
    await customer.context().close();
  }
});
