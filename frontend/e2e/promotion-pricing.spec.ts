import { execFileSync } from 'node:child_process';
import { expect, test, type Page } from '@playwright/test';
import { accounts, sessionFor, unique } from './support';

async function checkVoucherPromotionConflict(seller: Page, customer: Page, origin: string) {
  const writeHeaders = { Origin: origin, 'X-CSRF-Protection': '1' };
  const login = await seller.request.post('/api/auth/login', { headers: writeHeaders, data: accounts.seller });
  expect(login.status()).toBe(200);
  const headers = { ...writeHeaders, Authorization: 'Bearer ' + (await login.json()).accessToken };
  const code = 'PROMO' + unique().toUpperCase();
  const voucherPath = '/api/stores/me/vouchers';
  const now = Date.now();
  const created = await seller.request.post(voucherPath, { headers, data: {
    code, name: 'Voucher kiểm thử MD17', discountType: 'FIXED', discountValue: 1, minOrderAmount: 1, usageLimit: 1,
    startsAt: new Date(now - 3600000).toISOString(), endsAt: new Date(now + 86400000).toISOString(),
  } });
  expect(created.status()).toBe(201);
  const { id, version } = await created.json();
  let expectedVersion = version;
  try {
    const published = await seller.request.post(voucherPath + '/' + id + '/publish', { headers, data: { expectedVersion } });
    expect(published.status()).toBe(200);
    const active = await published.json();
    expectedVersion = active.version;
    expect(active.status).toBe('ACTIVE');

    await customer.getByLabel(/Mã voucher/).fill(code);
    const quoted = customer.waitForResponse(r => r.url().endsWith('/api/orders/voucher-quote') && r.request().method() === 'POST');
    await customer.getByRole('button', { name: 'Áp dụng voucher' }).click();
    const refusal = await quoted;
    expect(refusal.status()).toBe(409);
    expect(await refusal.json()).toMatchObject({ code: 'VOUCHER_PROMOTION_CONFLICT' });
    await expect(customer.getByRole('alert')).toContainText('không thể dùng chung voucher');
    await customer.getByRole('button', { name: 'Bỏ mã' }).click();
  } finally {
    const canceled = await seller.request.post(voucherPath + '/' + id + '/cancel', { headers, data: { expectedVersion } });
    expect(canceled.status()).toBe(200);
    expect((await canceled.json()).status).toBe('CANCELLED');
  }
}

test('Seller clearance flows through Catalog and Checkout into Order price and fee', async ({ browser }) => {
  test.setTimeout(180_000);
  const project = process.env.COMPOSE_PROJECT_NAME ?? 'hk5-demo';
  const baseUrl = new URL(process.env.E2E_BASE_URL ?? 'http://localhost:5173');
  expect(['localhost', '127.0.0.1']).toContain(baseUrl.hostname);
  expect(project).toMatch(/^(hk5-demo|hk5-promo-test-[a-z0-9-]+)$/);
  const postgres = project + '-postgres-1';
  expect(execFileSync('docker', ['inspect', '--format',
    '{{ index .Config.Labels "com.docker.compose.project" }}', postgres], { encoding: 'utf8' }).trim()).toBe(project);
  async function database(db: string, sql: string) {
    return execFileSync('docker', ['exec', postgres, 'psql', '-XAt', '-v', 'ON_ERROR_STOP=1',
      '-U', 'hk5', '-d', db, '-c', sql], { encoding: 'utf8' }).trim();
  }
  const seller = await sessionFor(browser, accounts.seller);
  const admin = await sessionFor(browser, accounts.admin);
  const customer = await sessionFor(browser, accounts.customer);
  try {
    const catalog = await (await customer.request.get('/api/catalog/products?size=50')).json();
    const product = catalog.items.find((p: {store: {name: string}}) => p.store.name === 'Tiệm Áo Sài Gòn');
    expect(product?.id).toMatch(/^[0-9a-f-]{36}$/);
    const price = Math.floor(Number(product.unitPrice) / 2);
    const name = 'Xả kho E2E ' + unique();
    const vn = (offset: number) => new Date(Date.now() + offset + 7 * 3600000).toISOString().slice(0,16);
    await seller.goto('/seller/promotions');
    await seller.getByLabel('Tên chương trình').fill(name);
    await seller.getByLabel('Bắt đầu (giờ Việt Nam)').fill(vn(2 * 3600000));
    await seller.getByLabel('Kết thúc (giờ Việt Nam)').fill(vn(5 * 3600000));
    const created = seller.waitForResponse(r => r.url().endsWith('/api/seller/promotions') && r.request().method() === 'POST');
    await seller.getByRole('button', { name: 'Tạo chương trình' }).click();
    const response = await created;
    expect(response.status()).toBe(201);
    const id = (await response.json()).id;
    expect(id).toMatch(/^[0-9a-f-]{36}$/);
    const sellerPanel = seller.locator('article').filter({ hasText: name });
    const productPicker = sellerPanel.getByRole('combobox', { name: 'Sản phẩm', exact: true });
    await expect(productPicker.locator('option', { hasText: product.name })).toHaveAttribute('value', product.id);
    await productPicker.selectOption(product.id, { timeout: 10000 });
    await sellerPanel.getByLabel('Giá sale (VND)').fill(String(price));
    await sellerPanel.getByRole('button', { name: 'Đăng ký sản phẩm' }).click();
    await expect(sellerPanel.getByText('Chờ duyệt', {exact: false})).toBeVisible();
    await sellerPanel.getByRole('button', { name: 'Gửi duyệt xả kho' }).click();
    await expect(sellerPanel.getByRole('button', { name: 'Gửi duyệt xả kho' })).toHaveCount(0);
    await admin.goto('/admin/promotions');
    const adminPanel = admin.locator('article').filter({ hasText: name });
    await adminPanel.getByRole('button', { name: 'Duyệt chương trình' }).click();
    await expect(adminPanel.getByRole('button', { name: 'Tạm dừng' })).toBeVisible();
    await adminPanel.getByRole('button', { name: 'Duyệt sản phẩm' }).click();
    await expect(adminPanel.getByRole('button', { name: 'Duyệt sản phẩm' })).toHaveCount(0);
    // Fast-forward the approved fixture only on the disposable live demo database.
    expect(await database('product_db', `WITH moved AS (
      UPDATE promotion_campaigns SET starts_at=clock_timestamp()-interval '1 minute',
      submitted_at=clock_timestamp()-interval '2 minutes', ends_at=clock_timestamp()+interval '2 hours'
      WHERE id='${id}' AND kind='SELLER_CLEARANCE' AND status='APPROVED' RETURNING id
      ) SELECT count(*) FROM moved`)).toBe('1');
    await expect(async () => {
      await customer.goto('/products/' + product.id);
      await expect(customer.getByText(/Đang khuyến mãi đến/)).toBeVisible({ timeout: 1500 });
    }).toPass({ timeout: 20000 });
    await customer.getByRole('button', { name: 'Mua ngay' }).click();
    await customer.getByRole('link', { name: 'Tiến hành đặt hàng' }).click();
    await customer.getByLabel('Người nhận').fill('Khách demo khuyến mãi');
    await customer.getByLabel('Số điện thoại').fill('0900000000');
    await customer.getByLabel('Địa chỉ', { exact: true }).fill('Địa chỉ demo');
    if (process.env.E2E_VOUCHER_C2 === '1') {
      await test.step('MD17 rejects a Store voucher when a Checkout item is on sale', async () => {
        await checkVoucherPromotionConflict(seller, customer, baseUrl.origin);
      });
    }
    await customer.getByRole('button', { name: /đặt hàng/i }).click();
    await expect(customer).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);
    const orderId = new URL(customer.url()).pathname.split('/').pop();
    expect(orderId).toMatch(/^[0-9a-f-]{36}$/);
    expect(await database('order_db', `SELECT i.unit_price_snapshot::bigint,s.gross::bigint,s.platform_fee::bigint
      FROM order_items i JOIN seller_orders s ON s.id=i.seller_order_id
      WHERE s.order_id='${orderId}' AND i.product_id='${product.id}'`))
      .toBe([price, price, Math.floor((price * 5 + 50) / 100)].join('|'));
    await adminPanel.getByRole('button', { name: 'Tạm dừng' }).click();
    await expect(adminPanel.getByRole('button', { name: 'Tiếp tục' })).toBeVisible();
  } finally {
    await Promise.allSettled([seller.context().close(), admin.context().close(), customer.context().close()]);
  }
});
