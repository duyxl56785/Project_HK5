import { execFileSync } from 'node:child_process';
import { expect, test } from '@playwright/test';
import { accounts, sessionFor, unique } from './support';

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
    await sellerPanel.getByLabel('Sản phẩm', { exact: true }).selectOption(product.id);
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
    await Promise.all([seller.context().close(), admin.context().close(), customer.context().close()]);
  }
});
