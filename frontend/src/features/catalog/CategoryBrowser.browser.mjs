import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { createServer } from 'vite';

let server, browser, context, page, baseUrl;
let errors;
const evidence = resolve(process.env.TV2_BROWSER_EVIDENCE ?? '../backend/product-service/build/category-browser-browser-evidence');
before(async () => {
  const modulePath = process.env.TV2_BROWSER_MODULE;
  const { chromium } = await import(modulePath ? pathToFileURL(modulePath).href : 'playwright');
  browser = await chromium.launch({ headless: true,
    ...(process.env.TV2_BROWSER_EXECUTABLE ? { executablePath: process.env.TV2_BROWSER_EXECUTABLE } : { channel: 'chrome' }) });
  server = await createServer({ server: { host: '127.0.0.1', port: 0, strictPort: false } });
  await server.listen();
  baseUrl = `http://127.0.0.1:${server.httpServer.address().port}/src/features/catalog/preview/index.html`;
  await mkdir(evidence, { recursive: true });
  await writeFile(resolve(evidence, 'browser.json'), JSON.stringify({ browser: browser.version(), source: 'feature preview fixtures; not API/E2E' }, null, 2));
});
beforeEach(async () => {
  errors = [];
  context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'vi-VN' });
  page = await context.newPage();
  page.on('pageerror', (error) => errors.push(error.message));
});
afterEach(async () => { await context?.close(); assert.deepEqual(errors, []); });
after(async () => { await browser?.close(); await server?.close(); });
const item = (name) => page.getByRole('treeitem', { name, exact: true });
async function open(mode) {
  await page.goto(baseUrl + (mode ? `?state=${mode}` : ''));
  await page.getByRole('heading', { name: 'Bạn đang tìm gì?' }).waitFor();
}
async function focused(locator) {
  assert.equal(await locator.evaluate((element) => element === document.activeElement), true);
}

test('click expands/collapses multiple levels and controls selection', async () => {
  await open();
  await item('Điện tử').click();
  assert.equal(await item('Điện tử').getAttribute('aria-expanded'), 'true');
  await item('Điện thoại').click();
  await item('Ốp lưng').click();
  assert.equal(await item('Ốp lưng').getAttribute('aria-selected'), 'true');
  assert.match(await page.locator('.tv2-category-browser__selection').innerText(), /Ốp lưng/);
  await item('Điện tử').click();
  assert.equal(await item('Ốp lưng').count(), 0);
});
test('Vietnamese search keeps ancestors and clear restores focus', async () => {
  await open();
  const input = page.getByRole('searchbox', { name: 'Tìm danh mục' });
  await input.fill('ỐP LƯNG');
  assert.equal(await page.getByRole('treeitem').count(), 3);
  assert.equal(await item('Điện tử').getAttribute('aria-expanded'), 'true');
  await input.fill('không tìm thấy');
  await page.getByRole('heading', { name: 'Không tìm thấy danh mục' }).waitFor();
  assert.equal(await page.getByRole('tree').count(), 0);
  await page.getByRole('button', { name: 'Xóa tìm kiếm' }).click();
  await focused(input);
  assert.equal(await input.inputValue(), '');
  assert.equal(await page.getByRole('treeitem').count(), 3);
});
test('Arrow/Home/End keyboard navigation preserves selection', async () => {
  await open();
  await item('Điện tử').focus();
  await page.keyboard.press('ArrowRight');
  await focused(item('Điện tử'));
  await page.keyboard.press('ArrowRight');
  await focused(item('Điện thoại'));
  await page.keyboard.press('ArrowLeft');
  await focused(item('Điện tử'));
  await page.keyboard.press('ArrowDown');
  await focused(item('Điện thoại'));
  await page.keyboard.press('ArrowUp');
  await focused(item('Điện tử'));
  await page.keyboard.press('End');
  await focused(item('Danh mục cấp 1'));
  await page.keyboard.press('Home');
  await focused(item('Điện tử'));
  assert.equal(await page.getByRole('treeitem', { selected: true }).count(), 0);
});
test('native Enter/Space select and toggle; Tab/Shift+Tab enter and leave the tree', async () => {
  await open();
  const input = page.getByRole('searchbox');
  await input.focus();
  await page.keyboard.press('Tab');
  await focused(item('Điện tử'));
  await page.keyboard.press('Enter');
  assert.equal(await item('Điện tử').getAttribute('aria-selected'), 'true');
  assert.equal(await item('Điện tử').getAttribute('aria-expanded'), 'true');
  await page.keyboard.press('Space');
  assert.equal(await item('Điện tử').getAttribute('aria-expanded'), 'false');
  await page.keyboard.press('Shift+Tab');
  await focused(input);
});
for (const [mode, message] of [['loading', 'Đang tải danh mục'], ['empty', 'Chưa có danh mục'], ['invalid', 'Dữ liệu danh mục không nhất quán']]) {
  test(`${mode} is visible without a usable tree`, async () => {
    await open(mode);
    assert.match(await page.locator('.tv2-category-browser').innerText(), new RegExp(message));
    assert.equal(await page.getByRole('tree').count(), 0);
  });
}
test('error preview retry invokes its callback and recovers to ready fixtures', async () => {
  await open('error');
  await page.getByRole('alert').waitFor();
  await page.getByRole('button', { name: 'Thử lại' }).click();
  await page.getByRole('tree').waitFor();
  assert.equal(await page.getByRole('alert').count(), 0);
});
test('expanded accessibility hierarchy owns direct children and exposes state', async () => {
  await open();
  await page.getByRole('searchbox').fill('ốp');
  for (const [parent, child, level] of [['Điện tử', 'Điện thoại', '2'], ['Điện thoại', 'Ốp lưng', '3']]) {
    const groupId = await item(parent).getAttribute('aria-owns');
    assert.equal(await page.evaluate((id) => document.getElementById(id)?.getAttribute('aria-owns'), groupId), await item(child).getAttribute('id'));
    assert.equal(await item(child).getAttribute('aria-level'), level);
    assert.equal(await item(child).getAttribute('aria-posinset'), '1');
    assert.equal(await item(child).getAttribute('aria-setsize'), '1');
  }
  assert.equal(await item('Ốp lưng').getAttribute('aria-expanded'), null);
  const snapshot = await page.getByRole('tree').ariaSnapshot();
  assert.match(snapshot, /group/);
  assert.match(snapshot, /Ốp lưng/);
  await writeFile(resolve(evidence, 'expanded-accessibility.yml'), snapshot);
  const cdp = await context.newCDPSession(page);
  const { nodes } = await cdp.send('Accessibility.getFullAXTree');
  const byId = new Map(nodes.map((node) => [node.nodeId, node]));
  const axItem = (name) => nodes.find((node) => node.role?.value === 'treeitem' && node.name?.value === name);
  for (const [parent, child] of [['Điện tử', 'Điện thoại'], ['Điện thoại', 'Ốp lưng']]) {
    const parentNode = axItem(parent);
    const childNode = axItem(child);
    assert.ok(parentNode && childNode);
    const group = parentNode.childIds?.map((id) => byId.get(id)).find((node) => node.role?.value === 'group');
    assert.ok(group?.childIds?.includes(childNode.nodeId));
    assert.equal(childNode.parentId, group.nodeId);
  }
  await writeFile(resolve(evidence, 'chrome-accessibility.json'), JSON.stringify({ nodes }, null, 2));
  await cdp.detach();
});
for (const width of [375, 768, 1280]) {
  test(`responsive ${width}px: deep long names, hit areas and visible focus`, async () => {
    await page.setViewportSize({ width, height: 900 });
    await open();
    await page.getByRole('searchbox').fill('Phụ kiện thiết kế');
    assert.equal(await page.getByRole('treeitem').count(), 10);
    const leaf = page.getByRole('treeitem').last();
    await leaf.click();
    await page.getByRole('searchbox').focus();
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
    for (const button of await page.locator('.tv2-category-browser button').all()) {
      const box = await button.boundingBox();
      assert.ok(box.width >= 44 && box.height >= 44);
    }
    const focus = await page.evaluate(() => {
      const style = getComputedStyle(document.activeElement);
      return { width: parseFloat(style.outlineWidth), style: style.outlineStyle };
    });
    assert.ok(focus.width >= 2 && focus.style !== 'none');
    await page.screenshot({ path: resolve(evidence, `category-${width}.png`), fullPage: true });
  });
}
