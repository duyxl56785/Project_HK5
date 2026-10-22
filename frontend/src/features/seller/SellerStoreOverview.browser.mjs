import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { createServer } from 'vite';

let server, browser, context, page, baseUrl, errors;
const evidence = resolve(process.env.TV2_BROWSER_EVIDENCE ?? '../backend/seller-service/build/overview-browser-evidence');
before(async () => {
  const modulePath = process.env.TV2_BROWSER_MODULE;
  const { chromium } = await import(modulePath ? pathToFileURL(modulePath).href : 'playwright');
  browser = await chromium.launch({ headless: true,
    ...(process.env.TV2_BROWSER_EXECUTABLE ? { executablePath: process.env.TV2_BROWSER_EXECUTABLE } : { channel: 'chrome' }) });
  server = await createServer({ server: { host: '127.0.0.1', port: 0, strictPort: false } });
  await server.listen();
  baseUrl = `http://127.0.0.1:${server.httpServer.address().port}/src/features/seller/preview/index.html`;
  await mkdir(evidence, { recursive: true });
  await writeFile(resolve(evidence, 'browser.json'), JSON.stringify({ browser: browser.version(), scope: 'fixture presentation only; no authorization/API/E2E' }, null, 2));
});
beforeEach(async () => {
  errors = [];
  context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'vi-VN' });
  page = await context.newPage();
  page.on('pageerror', (error) => errors.push(error.message));
});
afterEach(async () => { await context?.close(); assert.deepEqual(errors, []); });
after(async () => { await browser?.close(); await server?.close(); });
async function open(query = '') {
  await page.goto(baseUrl + query);
  await page.getByRole('heading', { name: 'Hồ sơ & cửa hàng' }).waitFor();
}
const content = () => page.locator('.tv2-seller-overview').innerText();

test('ready cards preserve full versions and never expose private fields', async () => {
  await open();
  assert.match(await content(), /Đã duyệt/);
  assert.match(await content(), /Cửa hàng Việt/);
  assert.match(await content(), /9007199254740993/);
  assert.match(await content(), /9223372036854775807/);
  assert.doesNotMatch(await content(), /PRIVATE_REASON|PRIVATE_OWNER|9007199254740992/);
  assert.equal(await page.locator('.tv2-seller-overview button').count(), 0);
});
test('Store closed and proven no-Store remain distinct', async () => {
  await open('?state=closed');
  assert.match(await content(), /Đang đóng cửa/);
  await open('?state=no-store');
  assert.match(await content(), /Chưa có cửa hàng/);
  assert.doesNotMatch(await content(), /cua-hang-viet/);
});
for (const [state, label] of [['loading', 'Đang tải hồ sơ'], ['empty', 'Chưa có hồ sơ người bán'], ['unauthorized', 'Không có quyền xem hồ sơ'], ['unavailable', 'Chức năng chưa sẵn sàng']]) {
  test(`${state} conceals stale attached private data`, async () => {
    await open(`?state=${state}`);
    assert.match(await content(), new RegExp(label));
    assert.doesNotMatch(await content(), /Cửa hàng Việt|9007199254740993|PRIVATE_REASON|PRIVATE_OWNER/);
  });
}
test('cross-Seller source exposes only a consistency error', async () => {
  await open('?state=inconsistent');
  await page.getByRole('alert').waitFor();
  assert.match(await content(), /Dữ liệu hồ sơ không nhất quán/);
  assert.doesNotMatch(await content(), /Cửa hàng Việt|9007199254740993/);
});
test('native Tab/Enter activate retry callback without making a network request', async () => {
  const requests = [];
  page.on('request', (request) => { if (['fetch', 'xhr'].includes(request.resourceType())) requests.push(request.url()); });
  await open('?state=error');
  await page.keyboard.press('Tab');
  const retry = page.getByRole('button', { name: 'Thử lại' });
  assert.equal(await retry.evaluate((element) => element === document.activeElement), true);
  await page.keyboard.press('Enter');
  await page.getByRole('heading', { name: 'Cửa hàng Việt' }).waitFor();
  assert.equal(await page.getByRole('alert').count(), 0);
  assert.deepEqual(requests, []);
});
for (const width of [375, 768, 1280]) {
  test(`responsive ${width}px keeps long names/slugs/versions readable`, async () => {
    await page.setViewportSize({ width, height: 900 });
    await open('?long=1');
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
    assert.match(await content(), /9223372036854775807/);
    const cards = await page.locator('.tv2-seller-overview__card').all();
    assert.equal(cards.length, 2);
    for (const card of cards) assert.equal(await card.evaluate((element) => element.scrollWidth <= element.clientWidth), true);
    await page.screenshot({ path: resolve(evidence, `overview-${width}.png`), fullPage: true });
  });
}
