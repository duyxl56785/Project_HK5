import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { pathToFileURL } from 'node:url';
import { mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { createServer } from 'vite';

let server, browser, url;
before(async () => {
  const { chromium } = await import(process.env.TV2_BROWSER_MODULE ? pathToFileURL(process.env.TV2_BROWSER_MODULE).href : 'playwright');
  server = await createServer({ server: { host: '127.0.0.1', port: 0, hmr: { port: 0 }, watch: null } });
  await server.listen();
  url = `http://127.0.0.1:${server.httpServer.address().port}/src/features/product/preview/index.html`;
  browser = await chromium.launch({ headless: true, ...(process.env.TV2_CHROME_PATH ? { executablePath: process.env.TV2_CHROME_PATH } : { channel: 'chrome' }) });
});
after(async () => { await browser?.close(); await server?.close(); });
async function pageFor(width) {
  const context = await browser.newContext({ viewport: { width, height: 1000 }, reducedMotion: 'reduce' });
  const page = await context.newPage(), errors = [], api = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('request', (request) => { if (/\/api(?:\/|\?|$)/.test(request.url()) || request.method() !== 'GET') api.push(request.url()); });
  await page.goto(url);
  await page.getByRole('heading', { name: 'Sản phẩm của bạn' }).waitFor();
  return { page, context, errors, api };
}
async function screenshot(page, name) {
  if (!process.env.TV2_BROWSER_EVIDENCE) return;
  await mkdir(process.env.TV2_BROWSER_EVIDENCE, { recursive: true });
  await page.screenshot({ path: join(process.env.TV2_BROWSER_EVIDENCE, name), fullPage: true });
}
for (const width of [1280, 375]) {
  test(`${width}px list and form render without overflow/runtime errors or backend requests`, async () => {
    const { page, context, errors, api } = await pageFor(width);
    try {
      assert.equal(await page.locator('.tv2-product__card').count(), 6);
      for (const label of ['Nháp', 'Chờ duyệt', 'Đang bán', 'Bị từ chối', 'Tạm khóa', 'Ngừng bán']) assert.ok(await page.locator('.tv2-product__badge').filter({ hasText: label }).count());
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      await screenshot(page, `product-list-${width}.png`);
      await page.getByRole('button', { name: '+ Thêm sản phẩm', exact: true }).click();
      await page.getByRole('textbox', { name: /^Tên sản phẩm/ }).fill('Cốc mới');
      await page.getByRole('textbox', { name: /^SKU/ }).fill('NEW-SKU');
      await page.getByLabel(/^Danh mục/).selectOption('33333333-3333-4333-8333-333333333333');
      await page.getByRole('textbox', { name: /^Đơn giá/ }).fill('125000');
      assert.equal(await page.getByLabel(/^Tiền tệ/).inputValue(), '');
      await page.getByLabel(/^Tiền tệ/).selectOption('VND');
      assert.equal(await page.getByRole('button', { name: 'Lưu bản nháp' }).isDisabled(), true);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      const targets = await page.locator('.tv2-product button, .tv2-product input, .tv2-product select').evaluateAll((elements) => elements.map((el) => el.getBoundingClientRect().height));
      assert.ok(targets.every((height) => height >= 44));
      await screenshot(page, `product-form-${width}.png`);
      assert.deepEqual(errors, []); assert.deepEqual(api, []);
    } finally { await context.close(); }
  });
}
test('real browser: blur validates fractional price; keyboard reaches named controls', async () => {
  const { page, context } = await pageFor(1280);
  try {
    await page.getByRole('button', { name: '+ Thêm sản phẩm', exact: true }).click();
    const name = page.getByRole('textbox', { name: /^Tên sản phẩm/ });
    assert.equal(await name.evaluate((el) => el === document.activeElement), true);
    const price = page.getByRole('textbox', { name: /^Đơn giá/ });
    await price.fill('1.5'); await price.press('Tab');
    assert.equal(await price.getAttribute('aria-invalid'), 'true');
    assert.ok(await page.getByRole('alert').count());
    await price.fill('1000000000'); await price.press('Tab');
    assert.equal(await price.getAttribute('aria-invalid'), 'false');
    await page.getByRole('button', { name: 'Hủy', exact: true }).click();
    await page.waitForFunction(() => document.activeElement?.textContent === '+ Thêm sản phẩm');
  } finally { await context.close(); }
});
test('real browser: editing keeps immutable SKU; protected writes stay disabled', async () => {
  const { page, context } = await pageFor(1280);
  try {
    await page.getByRole('button', { name: 'Sửa Cốc gốm men mây', exact: true }).click();
    assert.equal(await page.getByRole('textbox', { name: /^SKU/ }).getAttribute('readonly'), '');
    assert.equal(await page.getByRole('button', { name: 'Lưu thay đổi' }).isDisabled(), true);
    assert.equal(await page.getByRole('button', { name: 'Gửi duyệt', exact: true }).first().isDisabled(), true);
    assert.equal(await page.locator('button').filter({ hasText: /^Duyệt sản phẩm$/ }).count(), 0);
  } finally { await context.close(); }
});
test('real browser: local search/status filtering and reset', async () => {
  const { page, context } = await pageFor(375);
  try {
    await page.getByLabel('Tìm trong danh sách đã tải').fill('GOM-VIET-03');
    assert.equal(await page.locator('.tv2-product__card').count(), 1);
    await page.getByLabel('Trạng thái', { exact: true }).selectOption('DRAFT');
    await page.getByRole('heading', { name: 'Không tìm thấy sản phẩm' }).waitFor();
    await page.getByRole('button', { name: 'Xóa bộ lọc' }).click();
    assert.equal(await page.locator('.tv2-product__card').count(), 6);
  } finally { await context.close(); }
});
for (const [mode, heading] of [['loading', 'Đang tải sản phẩm'], ['empty', 'Chưa có sản phẩm'], ['error', 'Không tải được sản phẩm'], ['unauthorized', 'Không có quyền truy cập'], ['unavailable', 'Chức năng chưa sẵn sàng']]) {
  test(`real browser: ${mode} state`, async () => {
    const { page, context } = await pageFor(375);
    try {
      await page.getByLabel('Trạng thái kiểm thử').selectOption(mode);
      await page.getByRole('heading', { name: heading }).waitFor();
      assert.equal(await page.locator('.tv2-product__card').count(), 0);
      if (mode !== 'empty') assert.equal(await page.locator('.tv2-product').getByText('Gốm Việt Studio', { exact: true }).count(), 0);
    } finally { await context.close(); }
  });
}
test('real browser: Store switch discards old editor', async () => {
  const { page, context } = await pageFor(375);
  try {
    await page.getByRole('button', { name: '+ Thêm sản phẩm', exact: true }).click();
    await page.getByRole('textbox', { name: /^Tên sản phẩm/ }).fill('Bản chưa lưu');
    await page.getByRole('button', { name: 'Đổi Store kiểm thử' }).click();
    assert.equal(await page.locator('form').count(), 0);
    await page.getByText('Gốm Việt — Cửa hàng thứ hai', { exact: true }).waitFor();
  } finally { await context.close(); }
});

// Browser-only host/callback fixture, never imported by the application or feature bundle.
async function mountWriteSafetyHarness(page) {
  await page.evaluate(async () => {
    const { default: React } = await import('/node_modules/.vite/deps/react.js');
    const { default: ReactDOMClient } = await import('/node_modules/.vite/deps/react-dom_client.js');
    const { ProductManagementPage } = await import('/src/features/product/ProductManagementPage.tsx');
    const { ready } = await import('/src/features/product/productTestFixtures.mjs');
    const target = document.createElement('div'); document.body.append(target);
    let setSource, calls = 0, confirmReload = false;
    const actions = { update: async () => { calls++; return { status: 'unavailable' }; } };
    const onReload = async (request) => confirmReload
      ? { status: 'confirmed', receipt: { ...request }, snapshot: ready }
      : { status: 'unavailable' };
    function Harness() {
      const [state, setState] = React.useState(ready); setSource = setState;
      return React.createElement(ProductManagementPage, { state, sessionKey: 'browser-test-session', actions, onReload });
    }
    ReactDOMClient.createRoot(target).render(React.createElement(Harness));
    window.TV2ProductWriteSafetyTest = { clone() { setSource((current) => ({ ...current, products: [...current.products] })); }, confirm() { confirmReload = true; }, count() { return calls; } };
  });
  const harness = page.locator('body > div').last();
  await harness.getByRole('heading', { name: 'Sản phẩm của bạn' }).waitFor();
  return harness;
}
for (const allowReload of [false, true]) {
  test(`Chrome write-safety: stale clone stays locked; explicit reload confirmed=${allowReload}`, async () => {
    const { page, context, errors, api } = await pageFor(1280);
    try {
      const harness = await mountWriteSafetyHarness(page);
      await harness.getByRole('button', { name: 'Sửa Cốc gốm men mây', exact: true }).click();
      await harness.getByRole('button', { name: 'Lưu thay đổi' }).click();
      await page.evaluate(() => { for (let i = 0; i < 3; i++) window.TV2ProductWriteSafetyTest.clone(); });
      assert.equal(await harness.getByRole('button', { name: 'Lưu thay đổi' }).isDisabled(), true);
      await harness.locator('form').evaluate((form) => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
      assert.equal(await page.evaluate(() => window.TV2ProductWriteSafetyTest.count()), 1);
      if (allowReload) await page.evaluate(() => window.TV2ProductWriteSafetyTest.confirm());
      await harness.getByRole('button', { name: 'Tải lại danh sách' }).click();
      if (allowReload) {
        await harness.getByText('Danh sách đã được tải lại. Kiểm tra thông tin trước khi tiếp tục.', { exact: true }).waitFor();
        assert.equal(await harness.getByRole('button', { name: 'Lưu thay đổi' }).isEnabled(), true);
        await harness.getByRole('button', { name: 'Lưu thay đổi' }).click();
        assert.equal(await page.evaluate(() => window.TV2ProductWriteSafetyTest.count()), 2);
      } else assert.equal(await harness.getByRole('button', { name: 'Lưu thay đổi' }).isDisabled(), true);
      assert.deepEqual(errors, []); assert.deepEqual(api, []);
    } finally { await context.close(); }
  });
}
