import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { pathToFileURL } from 'node:url';
import { mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { createServer } from 'vite';
let server, browser, base;
before(async () => {
  const { chromium } = await import(process.env.TV2_BROWSER_MODULE ? pathToFileURL(process.env.TV2_BROWSER_MODULE).href : 'playwright');
  server = await createServer({ server: { host: '127.0.0.1', port: 0, hmr: { port: 0 }, watch: null } }); await server.listen();
  base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ headless: true, channel: 'chrome' });
});
after(async () => { await browser?.close(); await server?.close(); });
async function open(width = 1280) {
  const context = await browser.newContext({ viewport: { width, height: 900 }, reducedMotion: 'reduce' });
  const page = await context.newPage(), errors = [], api = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('request', request => { if (/\/api(?:\/|\?|$)/.test(request.url()) || request.method() !== 'GET') api.push(request.url()); });
  await page.goto(`${base}/demo/`); await page.getByRole('heading', { name: /Một chút/ }).waitFor();
  return { page, context, errors, api };
}
async function shot(page, name) { if (!process.env.TV2_DEMO_EVIDENCE) return; await mkdir(process.env.TV2_DEMO_EVIDENCE, { recursive: true }); await page.screenshot({ path: join(process.env.TV2_DEMO_EVIDENCE, name), fullPage: true }); }
async function role(page, value) { await page.getByLabel('Vai trò demo', { exact: true }).selectOption(value); }
async function go(page, path) { await page.evaluate(value => { window.location.hash = value; }, path); await page.waitForFunction(value => location.hash === `#${value}`, path); }
async function png(page, name = 'anh-demo.png') {
  const bytes = await page.evaluate(() => { const canvas = document.createElement('canvas'); canvas.width = canvas.height = 24; const ctx = canvas.getContext('2d'); ctx.fillStyle = '#76916b'; ctx.fillRect(0, 0, 24, 24); return canvas.toDataURL('image/png').split(',')[1]; });
  return { name, mimeType: 'image/png', buffer: Buffer.from(bytes, 'base64') };
}
for (const width of [375, 768, 1280]) test(`${width}px Catalog, Seller and Admin navigation has no page overflow or API calls`, async () => {
  const { page, context, errors, api } = await open(width);
  try {
    assert.equal(await page.locator('.demo-product-card').count(), 6);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await shot(page, `catalog-${width}.png`);
    await page.getByRole('button', { name: 'Sau', exact: true }).click(); assert.equal(await page.locator('.demo-product-card').count(), 1);
    await page.getByRole('textbox', { name: 'Tìm tên sản phẩm' }).fill('Không tồn tại'); await page.getByRole('heading', { name: 'Chưa tìm thấy điều bạn cần' }).waitFor();
    await page.getByRole('button', { name: 'Xem tất cả sản phẩm' }).click();
    await role(page, 'seller'); await page.locator('.demo-stats').waitFor();
    if (width < 768) { await page.getByRole('button', { name: 'Mở menu quản lý' }).click(); await page.getByRole('navigation', { name: 'Điều hướng quản lý' }).getByRole('link', { name: 'Sản phẩm', exact: true }).click(); }
    else await page.getByRole('navigation', { name: 'Điều hướng quản lý' }).getByRole('link', { name: 'Sản phẩm', exact: true }).click();
    await page.getByRole('heading', { name: 'Sản phẩm của bạn' }).waitFor();
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await shot(page, `seller-products-${width}.png`);
    await role(page, 'admin'); await page.getByRole('heading', { name: 'Xét duyệt người bán' }).waitFor();
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await shot(page, `admin-sellers-${width}.png`);
    assert.deepEqual(errors, []); assert.deepEqual(api, []);
  } finally { await context.close(); }
});
test('real Chrome local workflow: Seller creates/uploads preview/submits, Admin approves, Customer finds it, Reset revokes URLs', async () => {
  const { page, context, errors, api } = await open();
  try {
    await page.evaluate(() => { window.__demoURLs = { created: [], revoked: [] }; const create = URL.createObjectURL.bind(URL), revoke = URL.revokeObjectURL.bind(URL); URL.createObjectURL = blob => { const url = create(blob); window.__demoURLs.created.push(url); return url; }; URL.revokeObjectURL = url => { window.__demoURLs.revoked.push(url); revoke(url); }; });
    await role(page, 'seller'); await go(page, '/seller/products');
    await page.getByRole('button', { name: '+ Thêm sản phẩm', exact: true }).click();
    await page.getByRole('textbox', { name: /^Tên sản phẩm/ }).fill('Bình gốm demo mới');
    await page.getByRole('textbox', { name: /^SKU/ }).fill('DEMO-NEW-2026');
    await page.getByLabel(/^Danh mục/).selectOption('10000000-0000-4000-8000-000000000002');
    const price = page.getByRole('textbox', { name: /^Đơn giá/ }); await price.fill('1.5'); await price.press('Tab'); assert.equal(await price.getAttribute('aria-invalid'), 'true');
    await price.fill('250000'); await page.getByLabel(/^Tiền tệ/).selectOption('VND');
    await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
    await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).waitFor({ state: 'hidden' });
    await go(page, '/seller/images');
    await page.getByLabel('Chọn sản phẩm quản lý ảnh').selectOption({ label: 'Bình gốm demo mới · Nháp' });
    const input = page.locator('input[type=file]'), sample = await png(page);
    await input.setInputFiles(Array.from({ length: 6 }, (_, i) => ({ ...sample, name: `anh-${i}.png` })));
    await page.getByRole('alert').filter({ hasText: /5 ảnh/ }).waitFor(); assert.equal(await page.locator('.demo-image-thumb').count(), 0);
    await input.setInputFiles(Array.from({ length: 5 }, (_, i) => ({ ...sample, name: `anh-${i}.png` })));
    await page.locator('.demo-image-thumb').nth(4).waitFor(); assert.equal(await input.isDisabled(), true);
    await page.getByRole('button', { name: 'Xóa ảnh anh-0.png' }).click();
    await input.setInputFiles({ name: 'gia-mao.png', mimeType: 'image/png', buffer: Buffer.from('<svg/>') });
    await page.getByRole('alert').waitFor(); assert.equal(await page.locator('.demo-image-thumb').count(), 4);
    await input.setInputFiles(await png(page, 'anh-moi.png')); await page.locator('.demo-image-thumb').nth(4).waitFor();
    assert.equal(await page.locator('.demo-image-thumb img').evaluateAll(images => images.every(image => image.complete && image.naturalWidth === 24)), true);
    await shot(page, 'seller-local-images.png');
    await go(page, '/seller/products'); await page.locator('.tv2-product input').first().fill('DEMO-NEW-2026');
    const card = page.locator('.tv2-product__card').filter({ hasText: 'Bình gốm demo mới' }); await card.getByRole('button', { name: 'Gửi duyệt', exact: true }).click();
    await page.getByRole('button', { name: /^Xác nhận/ }).click(); await card.getByText('Chờ duyệt', { exact: true }).waitFor();
    await role(page, 'admin'); await go(page, '/admin/products');
    await page.getByRole('button', { name: 'Xem sản phẩm Bình gốm demo mới', exact: true }).click();
    await page.getByRole('button', { name: 'Duyệt sản phẩm', exact: true }).click();
    await role(page, 'customer'); await page.getByRole('textbox', { name: 'Tìm tên sản phẩm' }).fill('Bình gốm demo mới');
    await page.getByRole('link', { name: 'Bình gốm demo mới', exact: true }).click(); await page.getByRole('heading', { name: 'Bình gốm demo mới', exact: true }).waitFor();
    assert.equal(await page.locator('.demo-gallery-thumbs button').count(), 5); await shot(page, 'customer-product-detail.png');
    await role(page, 'seller'); await go(page, '/seller/products'); await page.locator('.tv2-product input').first().fill('DEMO-NEW-2026');
    await page.locator('.tv2-product__card').getByText('Đang bán', { exact: true }).waitFor();
    await page.getByRole('button', { name: 'Reset demo', exact: true }).click(); await page.getByRole('button', { name: 'Khôi phục demo', exact: true }).click();
    assert.equal(await page.evaluate(() => window.__demoURLs.created.every(url => window.__demoURLs.revoked.includes(url))), true);
    await go(page, '/seller/products'); await page.locator('.tv2-product input').first().fill('DEMO-NEW-2026'); await page.getByRole('heading', { name: 'Không tìm thấy sản phẩm' }).waitFor();
    assert.deepEqual(errors, []); assert.deepEqual(api, []);
  } finally { await context.close(); }
});
test('skip link focuses main without changing route and state examples conceal content', async () => {
  const { page, context, errors } = await open();
  try {
    const url = page.url(); await page.getByRole('link', { name: 'Đến nội dung chính' }).focus(); await page.keyboard.press('Enter'); assert.equal(page.url(), url);
    assert.equal(await page.evaluate(() => document.activeElement.id), 'demo-content');
    for (const mode of ['loading', 'empty', 'error', 'unauthorized', 'unavailable']) {
      await page.getByLabel('Trạng thái giao diện demo').selectOption(mode); assert.equal(await page.locator('.demo-product-card').count(), 0);
      await page.getByRole('button', { name: 'Trở về dữ liệu mẫu' }).click(); await page.locator('.demo-product-card').first().waitFor();
    }
    assert.deepEqual(errors, []);
  } finally { await context.close(); }
});
test('production entry never mounts demo fixtures or role switcher', async () => {
  const { page, context, api } = await open();
  try { await page.goto(base); assert.equal(await page.getByLabel('Vai trò demo', { exact: true }).count(), 0); assert.equal(await page.locator('.tv2-demo').count(), 0); assert.deepEqual(api, []); }
  finally { await context.close(); }
});
test('Catalog filters include descendants and sort exact VND; closing a Store removes its products', async () => {
  const { page, context, errors } = await open();
  try {
    await page.getByLabel('Sắp xếp', { exact: true }).selectOption('price-asc');
    const values = await page.locator('.demo-product-price').allTextContents();
    const prices = values.map(value => BigInt(value.replace(/\D/g, '')));
    assert.deepEqual(prices, [...prices].sort((a, b) => a < b ? -1 : a > b ? 1 : 0));
    await page.getByRole('treeitem', { name: 'Gốm & bàn ăn', exact: true }).click();
    assert.equal(await page.locator('.demo-product-card').count(), 3);
    await role(page, 'seller'); await go(page, '/seller/store'); await page.getByRole('button', { name: 'Đóng cửa hàng', exact: true }).click();
    await role(page, 'customer'); await page.getByRole('textbox', { name: 'Tìm tên sản phẩm' }).fill('Bình gốm men cát');
    await page.getByRole('heading', { name: 'Chưa tìm thấy điều bạn cần' }).waitFor();
    await role(page, 'seller'); await go(page, '/seller/store'); await page.getByRole('button', { name: 'Mở cửa hàng', exact: true }).click();
    await role(page, 'customer'); await page.getByRole('textbox', { name: 'Tìm tên sản phẩm' }).fill('Bình gốm men cát');
    await page.getByRole('heading', { name: 'Bình gốm men cát', exact: true }).waitFor(); assert.deepEqual(errors, []);
  } finally { await context.close(); }
});
test('new Seller registration, reject/resubmit, approval, Store creation and suspension are connected', async () => {
  const { page, context, errors } = await open();
  try {
    await role(page, 'seller'); await page.getByLabel('Tài khoản người bán demo').selectOption('seller-new'); await go(page, '/seller/profile');
    await page.getByRole('checkbox').check(); await page.getByRole('button', { name: 'Gửi đăng ký', exact: true }).click();
    await page.getByText('Hồ sơ đang chờ xét duyệt.', { exact: false }).last().waitFor();
    await role(page, 'admin'); await page.getByRole('button', { name: 'Xem hồ sơ Người bán mới', exact: true }).click();
    const sellerId = await page.locator('.demo-detail code').textContent();
    await page.getByRole('button', { name: 'Từ chối hồ sơ', exact: true }).click();
    await page.getByLabel('Lý do xét duyệt').fill('Bổ sung thông tin trước khi gửi lại.'); await page.getByRole('button', { name: 'Xác nhận', exact: true }).click();
    await role(page, 'seller'); await page.getByLabel('Tài khoản người bán demo').selectOption('seller-new'); await go(page, '/seller/profile');
    await page.getByRole('checkbox').check(); await page.getByRole('button', { name: 'Gửi lại để xét duyệt', exact: true }).click();
    await role(page, 'admin'); await page.getByRole('button', { name: 'Xem hồ sơ Người bán mới', exact: true }).click();
    assert.equal(await page.locator('.demo-detail code').textContent(), sellerId);
    await page.getByRole('button', { name: 'Duyệt người bán', exact: true }).click();
    await role(page, 'seller'); await page.getByLabel('Tài khoản người bán demo').selectOption('seller-new'); await go(page, '/seller/store');
    await page.getByLabel('Tên cửa hàng', { exact: true }).fill('Xưởng Demo'); await page.getByLabel('Định danh đường dẫn').fill('nep-nha');
    await page.getByRole('button', { name: 'Tạo cửa hàng', exact: true }).click(); await page.getByRole('alert').waitFor();
    await page.getByLabel('Định danh đường dẫn').fill('xuong-demo'); await page.getByRole('button', { name: 'Tạo cửa hàng', exact: true }).click();
    await page.getByText('Đang mở cửa', { exact: true }).waitFor();
    await role(page, 'admin'); await page.getByRole('button', { name: 'Xem hồ sơ Người bán mới', exact: true }).click();
    await page.getByRole('button', { name: 'Tạm ngừng người bán', exact: true }).click();
    await page.getByLabel('Lý do xét duyệt').fill('😀'.repeat(500)); assert.equal(await page.getByLabel('Lý do xét duyệt').inputValue(), '😀'.repeat(500));
    await page.getByRole('button', { name: 'Xác nhận', exact: true }).click(); await page.getByRole('button', { name: 'Khôi phục người bán', exact: true }).waitFor();
    await page.getByRole('button', { name: 'Khôi phục người bán', exact: true }).click();
    await page.getByRole('button', { name: 'Tạm ngừng người bán', exact: true }).waitFor(); assert.deepEqual(errors, []);
  } finally { await context.close(); }
});
test('Category CRUD protects Product references/cycles; Seller B sees only its Store', async () => {
  const { page, context, errors } = await open();
  try {
    await role(page, 'admin'); await go(page, '/admin/categories');
    await page.getByLabel('Tên danh mục', { exact: true }).fill('Danh mục demo mới'); await page.getByRole('button', { name: 'Thêm danh mục', exact: true }).click();
    await page.getByRole('treeitem', { name: 'Danh mục demo mới', exact: true }).click();
    const editor = page.locator('.demo-panel').filter({ has: page.getByRole('heading', { name: 'Chỉnh sửa: Danh mục demo mới', exact: true }) });
    assert.equal(await editor.getByLabel('Danh mục cha').locator('option', { hasText: 'Danh mục demo mới' }).evaluate(option => option.disabled), true);
    await editor.getByRole('button', { name: 'Xóa danh mục', exact: true }).click(); await page.getByRole('button', { name: 'Xác nhận xóa', exact: true }).click();
    assert.equal(await page.getByRole('treeitem', { name: 'Danh mục demo mới', exact: true }).count(), 0);
    await page.getByRole('treeitem', { name: 'Gốm & bàn ăn', exact: true }).click(); await page.getByRole('treeitem', { name: 'Bình & lọ', exact: true }).click();
    assert.equal(await page.getByRole('button', { name: 'Xóa danh mục', exact: true }).isDisabled(), true);
    await role(page, 'seller'); await page.getByLabel('Tài khoản người bán demo').selectOption('seller-b'); await go(page, '/seller/products');
    await page.locator('.tv2-product input').first().fill('NEP-DEMO-001'); await page.getByRole('heading', { name: 'Không tìm thấy sản phẩm' }).waitFor();
    await go(page, '/admin/products'); await page.getByRole('heading', { name: 'Bạn chưa có quyền truy cập' }).waitFor();
    assert.equal(await page.locator('.demo-table').count(), 0); assert.deepEqual(errors, []);
  } finally { await context.close(); }
});
