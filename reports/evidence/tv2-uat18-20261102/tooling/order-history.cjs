// One-off browser check of Order History against tv3-live-147. Never prints credentials.
const { readFileSync, writeFileSync } = require('node:fs');
const { chromium } = require('D:/monhoc/CNPM/project_hk5/.worktrees/uat18/frontend/node_modules/@playwright/test');
const assert = require('node:assert/strict');

const password = readFileSync('D:/monhoc/CNPM/project_hk5/.worktrees/uat18/.env.demo', 'utf8').match(/^SEED_PASSWORD=(.*)$/m)[1].trim();
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const problems = [];
  page.on('console', (m) => { if (m.type() === 'error') problems.push(`console: ${m.text()}`); });
  page.on('response', (r) => { if (r.status() >= 500) problems.push(`${r.status()} ${new URL(r.url()).pathname}`); });
  await page.goto('http://localhost:5173/login');
  await page.getByLabel('Email', { exact: true }).fill('customer@hk5.local');
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).waitFor();

  const listed = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/orders' && r.request().method() === 'GET');
  await page.goto('http://localhost:5173/orders');
  const list = await (await listed).json();
  const orders = list;
  assert.ok(Array.isArray(orders) && orders.length >= 4, 'expected the orders placed by this run');
  await page.getByRole('heading', { name: 'Đơn hàng của tôi', exact: true }).waitFor();
  const links = page.locator('a[href^="/orders/"]');
  await links.first().waitFor();
  const rows = await links.count();
  assert.equal(rows, orders.length);
  for (const order of orders) assert.ok(order.overallStatus, 'overallStatus must be present');

  const target = orders[0];
  await page.locator(`a[href="/orders/${target.id}"]`).first().click();
  await page.waitForURL(`**/orders/${target.id}`);
  await page.getByRole('heading', { name: new RegExp(`^Đơn #`) }).waitFor();
  await page.getByRole('link', { name: '← Đơn hàng của tôi' }).click();
  await page.waitForURL('**/orders');
  await page.getByRole('heading', { name: 'Đơn hàng của tôi', exact: true }).waitFor();
  await browser.close();

  assert.deepEqual(problems, []);
  const result = { rows, statuses: orders.map((o) => o.overallStatus), openedDetail: target.id, problems };
  writeFileSync(`${__dirname}/evidence/order-history.json`, JSON.stringify(result, null, 2));
  console.log('PASS', JSON.stringify(result));
})().catch((error) => { console.error('FAIL', error.message); process.exit(1); });
