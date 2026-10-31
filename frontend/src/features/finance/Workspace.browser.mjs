import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { createServer } from 'vite';

let server, browser, context, page, baseUrl, errors;
const evidence = resolve(process.env.TV5_BROWSER_EVIDENCE ?? '../.verification/tv5-ui-browser');
before(async () => {
  const { chromium } = await import(process.env.TV5_BROWSER_MODULE ? pathToFileURL(process.env.TV5_BROWSER_MODULE).href : 'playwright');
  browser = await chromium.launch({ headless: true, channel: 'chrome' });
  server = await createServer({ server: { host: '127.0.0.1', port: 0, strictPort: false } });
  await server.listen();
  baseUrl = `http://127.0.0.1:${server.httpServer.address().port}/src/features/finance/preview/index.html`;
  await mkdir(evidence, { recursive: true });
});
beforeEach(async () => {
  errors = []; context = await browser.newContext({ viewport: { width: 1440, height: 1080 }, locale: 'vi-VN' }); page = await context.newPage();
  page.on('pageerror', e => errors.push(e.message));
});
afterEach(async () => { await context?.close(); assert.deepEqual(errors, []); });
after(async () => { await browser?.close(); await server?.close(); });
async function open(query = '') { await page.goto(baseUrl + query); await page.getByRole('heading', { level: 1 }).waitFor(); }
async function ready(query = '') { await open(query); await page.getByText('DEMO DATA', { exact: true }).waitFor(); }
const nav = label => page.getByRole('navigation', { name: 'Điều hướng TV5' }).getByRole('button', { name: label, exact: true });
const rows = () => page.locator('tbody tr');
const stat = label => page.locator('.tv5-stat').filter({ has: page.getByRole('heading', { name: label, exact: true }) }).locator('strong');

test('Finance dashboard renders four metrics, exact VND, charts and paged data', async () => {
  await ready(); assert.equal(await stat('SellerOrder').innerText(), '12'); assert.equal(await rows().count(), 5);
  assert.match(await page.locator('main').innerText(), /1\.250\.000 ₫/);
  assert.equal(await page.getByRole('img', { name: /Giá trị snapshot/ }).count(), 1);
  await page.getByText('Xem số liệu biểu đồ', { exact: true }).click();
  assert.match(await page.locator('details').innerText(), /2026-10-22/);
  await page.screenshot({ path: resolve(evidence, 'finance-desktop.png'), fullPage: true });
});
test('literal search and clearing reset the page and restore twelve rows', async () => {
  await ready(); await page.getByRole('button', { name: 'Sau →' }).click();
  await page.getByRole('searchbox', { name: 'Tìm giao dịch' }).fill('demo-so-012');
  assert.equal(await rows().count(), 1); assert.match(await rows().innerText(), /DEMO-SO-012/);
  await page.getByRole('button', { name: 'Xóa bộ lọc' }).click();
  assert.equal(await rows().count(), 5); assert.match(await page.getByRole('navigation', { name: 'Phân trang' }).innerText(), /Trang 1\/3/);
});
test('Store/status/date intersection and reversed date validation work', async () => {
  await ready(); await page.getByLabel('Store', { exact: true }).selectOption('store-b');
  assert.doesNotMatch(await page.locator('tbody').innerText(), /Mộc Studio/);
  await page.getByLabel('Trạng thái', { exact: true }).selectOption('COLLECTED');
  assert.equal(await stat('SellerOrder').innerText(), '3');
  await page.getByLabel('Từ ngày UTC').fill('2026-10-27'); await page.getByLabel('Đến ngày UTC').fill('2026-10-22');
  await page.getByRole('alert').waitFor(); assert.equal(await rows().count(), 0);
});
test('COD keeps delivery, payment and verification visibly separate', async () => {
  await ready(); await nav('Theo dõi COD').click(); await page.getByRole('heading', { name: 'COD Management', exact: true }).waitFor();
  const table = page.locator('table'); assert.match(await table.innerText(), /Delivery[\s\S]*COD[\s\S]*Evidence/);
  assert.match(await table.innerText(), /DISPATCHED|DELIVERED/); assert.match(await table.innerText(), /UNVERIFIED|VERIFIED/);
  assert.equal(await page.getByRole('button', { name: /Xác nhận COD|Thanh toán/ }).count(), 0);
  await page.screenshot({ path: resolve(evidence, 'cod-desktop.png'), fullPage: true });
});
test('Settlement is read-only with explicit absent money snapshots and blocker reason', async () => {
  await ready(); await nav('Settlement').click(); assert.equal(await rows().count(), 4);
  assert.match(await page.locator('tbody').innerText(), /BLOCKED/); assert.match(await page.locator('tbody').innerText(), /Chưa có snapshot/);
  await page.getByRole('button', { name: 'Xem chi tiết DEMO-SO-001', exact: true }).click();
  const dialog = page.getByRole('dialog'); assert.match(await dialog.innerText(), /Chờ policy/);
  assert.equal(await dialog.getByRole('button').count(), 1);
  assert.equal(await page.getByRole('button', { name: /Create Settlement|Release Payment|Refund|Approve Settlement/ }).count(), 0);
});
test('Detail panel traps keyboard focus, Escape closes and restores the trigger', async () => {
  await ready(); const trigger = page.getByRole('button', { name: 'Xem chi tiết DEMO-SO-001', exact: true }); await trigger.click();
  const close = page.getByRole('button', { name: 'Đóng chi tiết', exact: true });
  assert.equal(await close.evaluate(el => el === document.activeElement), true);
  await page.keyboard.press('Tab'); assert.equal(await close.evaluate(el => el === document.activeElement), true);
  await page.keyboard.press('Escape'); assert.equal(await page.getByRole('dialog').count(), 0);
  assert.equal(await trigger.evaluate(el => el === document.activeElement), true);
});
test('scope switch clears open foreign details, rows, options and privileged tabs', async () => {
  await ready(); await page.getByRole('button', { name: 'Xem chi tiết DEMO-SO-002', exact: true }).click();
  await page.getByLabel('Preview scope').selectOption('store-a');
  await page.getByText('DEMO DATA', { exact: true }).waitFor();
  assert.equal(await page.getByRole('dialog').count(), 0); assert.doesNotMatch(await page.locator('main').innerText(), /Sách & Chuyện|Demo Seller B|DEMO-SO-002/);
  assert.equal(await nav('Kiểm thử QA').count(), 0); assert.equal(await nav('Pull Requests').count(), 0);
  assert.equal(await page.getByLabel('Store', { exact: true }).locator('option').count(), 2);
});
for (const [mode, heading] of [['loading', 'Đang tải dữ liệu'], ['empty', 'Chưa có dữ liệu'], ['error', 'Không thể tải dữ liệu'], ['denied', 'Không có quyền truy cập'], ['blocked', 'Chưa kết nối API']]) {
  test(`preview ${mode} state displays no stale transaction`, async () => {
    await open(`?state=${mode}`); await page.getByRole('heading', { name: heading, exact: true }).waitFor();
    assert.equal(await rows().count(), 0); assert.doesNotMatch(await page.locator('main').innerText(), /DEMO-SO|Mộc Studio|PRIVATE/);
    if (mode === 'error') { await page.getByRole('button', { name: 'Thử lại', exact: true }).click(); await page.getByRole('heading', { name: heading, exact: true }).waitFor(); }
  });
}
test('QA switches receipt heads without adding their totals, shows unexecuted gates separately', async () => {
  await ready(); await nav('Kiểm thử QA').click(); assert.equal(await stat('Đã thực thi').innerText(), '749');
  await page.getByLabel('Lượt chạy', { exact: true }).selectOption('cli-445deec'); assert.equal(await stat('Đã thực thi').innerText(), '32');
  assert.equal(await stat('PASS').innerText(), '32');
  await page.getByLabel('Lượt chạy', { exact: true }).selectOption('acceptance-not-executed');
  assert.equal(await stat('Đã thực thi').innerText(), '0'); assert.equal(await stat('BLOCKED').innerText(), '2'); assert.equal(await stat('NOT_RUN').innerText(), '1');
  await page.getByRole('button', { name: 'Chi tiết test FIN-PROD', exact: true }).click(); assert.match(await page.getByRole('dialog').innerText(), /signatures pending/);
  assert.match(await page.getByRole('dialog').getByRole('link').getAttribute('href'), /^https:\/\/github\.com\/duyxl56785\/Project_HK5\//);
});
test('QA module/status/type/search/commit filters never relabel another head', async () => {
  await ready(); await nav('Kiểm thử QA').click(); await page.getByLabel('Module', { exact: true }).selectOption('product-service');
  await page.getByLabel('Kết quả', { exact: true }).selectOption('PASS'); await page.getByLabel('Test type', { exact: true }).selectOption('Unit');
  await page.getByRole('searchbox', { name: 'Tìm test' }).fill('CatalogBatchValidatorTest'); assert.ok(await rows().count() > 0);
  await page.getByLabel('Commit SHA').fill('ffffffffffff'); await page.getByRole('heading', { name: 'Chưa có dữ liệu', exact: true }).waitFor();
  assert.equal(await stat('PASS').count(), 0);
});
test('PR table preserves NO_CHECKS, old review heads and filters real request metadata', async () => {
  await ready(); await nav('Pull Requests').click(); await page.getByRole('searchbox', { name: 'Tìm Pull Request' }).fill('103');
  assert.equal(await rows().count(), 1); assert.match(await rows().innerText(), /NO_CHECKS/); assert.match(await rows().innerText(), /HEAD cũ/);
  await page.getByRole('button', { name: 'Chi tiết PR 103', exact: true }).click(); assert.match(await page.getByRole('dialog').innerText(), /unresolved|Unresolved/);
  await page.keyboard.press('Escape'); await page.getByRole('searchbox', { name: 'Tìm Pull Request' }).fill('');
  await page.getByLabel('Phạm vi PR', { exact: true }).selectOption('changes'); assert.ok(await rows().count() >= 1);
  await page.getByLabel('Phạm vi PR', { exact: true }).selectOption('failed'); assert.equal(await rows().count(), 0);
  assert.equal(await page.getByRole('button', { name: /^(?:Approve|Merge|Resolve thread)$/ }).count(), 0);
});
test('labels, heading hierarchy and external evidence link protections are present', async () => {
  await ready(); assert.equal(await page.getByRole('heading', { level: 1 }).count(), 1);
  const unlabeled = await page.locator('input,select').evaluateAll(elements => elements.filter(el => !el.labels?.length && !el.getAttribute('aria-label')).length); assert.equal(unlabeled, 0);
  const ids = await page.locator('[id]').evaluateAll(elements => elements.map(el => el.id)); assert.equal(ids.length, new Set(ids).size);
  await nav('Kiểm thử QA').click(); const links = await page.locator('a[target="_blank"]').evaluateAll(elements => elements.map(el => el.rel)); assert.ok(links.every(rel => rel.includes('noopener') && rel.includes('noreferrer')));
  await page.screenshot({ path: resolve(evidence, 'qa-desktop.png'), fullPage: true });
});
for (const width of [375, 768]) {
  test(`responsive ${width}px has bounded page width and reachable table navigation`, async () => {
    await page.setViewportSize({ width, height: 900 }); await ready();
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
    await page.getByRole('button', { name: 'Sau →' }).click(); assert.match(await page.getByRole('navigation', { name: 'Phân trang' }).innerText(), /Trang 2\/3/);
    await page.screenshot({ path: resolve(evidence, `finance-${width}.png`), fullPage: true });
  });
}
