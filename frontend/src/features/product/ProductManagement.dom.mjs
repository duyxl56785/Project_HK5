import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { pathToFileURL } from 'node:url';
import { act, createElement } from 'react';
import { createServer } from 'vite';
import { categories, product, ready, store } from './productTestFixtures.mjs';

let window, server, ProductManagementPage, createRoot, container, root;
before(async () => {
  const { Window } = await import(process.env.TV2_DOM_MODULE ? pathToFileURL(process.env.TV2_DOM_MODULE).href : 'happy-dom');
  window = new Window({ url: 'http://localhost/' });
  for (const name of ['window', 'document', 'navigator', 'HTMLElement', 'HTMLInputElement', 'HTMLSelectElement', 'Event', 'MouseEvent', 'KeyboardEvent']) Object.defineProperty(globalThis, name, { value: name === 'window' ? window : window[name], configurable: true });
  globalThis.requestAnimationFrame = window.requestAnimationFrame.bind(window);
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  server = await createServer({ optimizeDeps: { noDiscovery: true, entries: [] }, server: { middlewareMode: true, hmr: { port: 0 }, watch: null } });
  ({ ProductManagementPage } = await server.ssrLoadModule('/src/features/product/ProductManagementPage.tsx'));
});
beforeEach(() => { container = window.document.createElement('div'); window.document.body.append(container); root = createRoot(container); });
afterEach(async () => { await act(() => root.unmount()); container.remove(); });
after(async () => { await server?.close(); await window?.happyDOM.abort(); });
const mount = (props = {}) => act(() => root.render(createElement(ProductManagementPage, { state: ready, ...props })));
const button = (text) => [...container.querySelectorAll('button')].find((element) => element.textContent === text);
const click = (element) => act(() => element.click());
async function input(name, value) {
  const element = container.querySelector(`[name="${name}"]`);
  await act(() => {
    const proto = element.tagName === 'SELECT' ? window.HTMLSelectElement.prototype : window.HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(element, value);
    element.dispatchEvent(new window.Event(element.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true }));
  });
}
const submit = () => act(() => container.querySelector('form').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true })));
async function fill() { await input('name', '  Cốc mới  '); await input('sku', 'Moi-é'); await input('categoryId', categories[0].id); await input('unitPrice', '1000000000'); await input('currency', 'VND'); }
const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done; }); return { promise, resolve }; };

test('no adapter: create/edit forms usable, save/submit unavailable, no invented success', async () => {
  await mount(); await click(button('+ Thêm sản phẩm')); await fill();
  assert.equal(button('Lưu bản nháp').disabled, true);
  await submit();
  assert.match(container.textContent, /dữ liệu chưa được lưu/);
  assert.doesNotMatch(container.textContent, /Máy chủ đã xác nhận/);
  assert.equal(container.querySelectorAll('.tv2-product__card').length, 1);
  await click(button('Hủy')); await click(button('Chỉnh sửa'));
  assert.equal(container.querySelector('[name="sku"]').readOnly, true);
  assert.equal(button('Gửi duyệt').disabled, true);
});
test('required/invalid form blocks callback and exposes linked errors', async () => {
  let calls = 0; await mount({ actions: { create: async () => { calls++; return { status: 'confirmed' }; } } });
  await click(button('+ Thêm sản phẩm')); await submit();
  assert.equal(calls, 0);
  assert.equal(container.querySelectorAll('[aria-invalid="true"]').length, 5);
  for (const link of container.querySelectorAll('[role="alert"] a')) assert.ok(window.document.getElementById(link.hash.slice(1)));
  await fill(); await input('unitPrice', '1.5'); await submit(); assert.equal(calls, 0);
});
test('create passes canonical fields/explicit currency with Store, never role/owner/status', async () => {
  const calls = []; let reload;
  await mount({ actions: { create: async (command) => { calls.push(command); return { status: 'confirmed' }; } }, onReload: (id) => { reload = id; } });
  await click(button('+ Thêm sản phẩm'));
  assert.equal(container.querySelector('[name="currency"]').value, '');
  await fill(); await submit();
  assert.deepEqual(calls, [{ storeId: store.id, name: 'Cốc mới', sku: 'Moi-é', categoryId: categories[0].id, unitPrice: '1000000000', currency: 'VND' }]);
  assert.equal(reload, store.id);
  assert.equal(container.querySelectorAll('.tv2-product__card').length, 1);
  assert.match(container.textContent, /Máy chủ đã xác nhận/);
  assert.equal(button('+ Thêm sản phẩm').disabled, true);
});
test('update preserves exact expectedVersion and excludes SKU/Store from command', async () => {
  let command; await mount({ actions: { update: async (value) => { command = value; return { status: 'confirmed' }; } } });
  await click(button('Chỉnh sửa')); await input('name', 'Tên mới'); await submit();
  assert.deepEqual(command, { productId: product.id, expectedVersion: '9007199254740993', categoryId: product.categoryId, name: 'Tên mới', unitPrice: '125000', currency: 'VND' });
  assert.match(container.querySelector('.tv2-product__card').textContent, /Cốc gốm men mây/);
});
test('submit requires confirmation and never optimistically marks PENDING_REVIEW', async () => {
  const calls = []; await mount({ actions: { submitForReview: async (value) => { calls.push(value); return { status: 'confirmed' }; } } });
  await click(button('Gửi duyệt')); assert.equal(calls.length, 0);
  assert.match(container.textContent, /Gửi sản phẩm để xét duyệt/);
  await click(button('Xác nhận gửi duyệt'));
  assert.deepEqual(calls, [{ productId: product.id, expectedVersion: product.version }]);
  assert.match(container.querySelector('.tv2-product__card').textContent, /Nháp/);
});
test('pending operation locks duplicate submit and retains entered data', async () => {
  const pending = deferred(); let calls = 0;
  await mount({ actions: { create: () => { calls++; return pending.promise; } } });
  await click(button('+ Thêm sản phẩm')); await fill(); await submit(); await submit();
  assert.equal(calls, 1); assert.equal(container.querySelector('fieldset').disabled, true);
  assert.equal(container.querySelector('[name="name"]').value, '  Cốc mới  ');
  await act(() => pending.resolve({ status: 'unavailable' }));
  assert.match(container.textContent, /Kết nối chưa sẵn sàng/);
});
for (const outcome of ['error', 'unavailable', 'conflict', 'throw']) {
  test(`safe ${outcome} state retains form, no raw diagnostic or success`, async () => {
    await mount({ actions: { update: async () => { if (outcome === 'throw') throw new Error('PRIVATE_TOKEN'); return { status: outcome, message: 'PRIVATE_TOKEN' }; } } });
    await click(button('Chỉnh sửa')); await input('name', 'Giữ thông tin này'); await submit();
    assert.equal(container.querySelector('[name="name"]').value, 'Giữ thông tin này');
    assert.ok(container.querySelector('[role="alert"]'));
    assert.doesNotMatch(container.textContent, /PRIVATE_TOKEN|Máy chủ đã xác nhận/);
    assert.equal(button('Lưu thay đổi').disabled, true, 'uncertain/conflicting writes require a fresh read before retry');
  });
}
test('authorization denial conceals Product/Store and editor immediately', async () => {
  await mount({ actions: { update: async () => ({ status: 'unauthorized' }) } });
  await click(button('Chỉnh sửa')); await submit();
  assert.match(container.textContent, /Không có quyền truy cập/);
  assert.doesNotMatch(container.textContent, /Gốm Việt|GOM-MAY/);
  assert.equal(container.querySelector('form'), null);
});
test('external authorization change ignores late mutation response', async () => {
  const pending = deferred(); let reloads = 0;
  await mount({ actions: { update: () => pending.promise }, onReload: () => reloads++ });
  await click(button('Chỉnh sửa')); await submit();
  await mount({ state: { status: 'unauthorized' } });
  await act(() => pending.resolve({ status: 'confirmed' }));
  assert.match(container.textContent, /Không có quyền truy cập/); assert.equal(reloads, 0);
  assert.doesNotMatch(container.textContent, /GOM-MAY|Máy chủ đã xác nhận/);
});
test('Store switch resets form and ignores old-store response', async () => {
  const pending = deferred(); let reloads = 0;
  await mount({ actions: { update: () => pending.promise }, onReload: () => reloads++ });
  await click(button('Chỉnh sửa')); await submit();
  await mount({ state: { ...ready, store: { ...store, id: categories[0].id, name: 'Cửa hàng thứ hai' }, products: [] } });
  await act(() => pending.resolve({ status: 'confirmed' }));
  assert.match(container.textContent, /Cửa hàng thứ hai/); assert.equal(container.querySelector('form'), null); assert.equal(reloads, 0);
});
test('changed source version blocks stale edit/confirmation instead of overwriting', async () => {
  let calls = 0; const actions = { update: async () => { calls++; return { status: 'confirmed' }; } };
  await mount({ actions }); await click(button('Chỉnh sửa'));
  await mount({ state: { ...ready, products: [{ ...product, version: '9007199254740994' }] }, actions });
  await submit(); assert.equal(calls, 0); assert.match(container.textContent, /Dữ liệu đang sửa đã cũ/);
});
test('changed source version also blocks a pending submit confirmation', async () => {
  let calls = 0; const actions = { submitForReview: async () => { calls++; return { status: 'confirmed' }; } };
  await mount({ actions }); await click(button('Gửi duyệt'));
  await mount({ state: { ...ready, products: [{ ...product, version: '9007199254740994' }] }, actions });
  assert.equal(button('Xác nhận gửi duyệt').disabled, true);
  await click(button('Xác nhận gửi duyệt')); assert.equal(calls, 0);
  assert.match(container.textContent, /Sản phẩm đã thay đổi/);
});
test('new authoritative list replaces confirmed view; no local optimistic Product remains', async () => {
  const actions = { submitForReview: async () => ({ status: 'confirmed' }) };
  await mount({ actions }); await click(button('Gửi duyệt')); await click(button('Xác nhận gửi duyệt'));
  assert.match(container.querySelector('.tv2-product__badge').textContent, /Nháp/);
  await mount({ state: { ...ready, products: [{ ...product, status: 'PENDING_REVIEW', version: '9007199254740994' }] }, actions });
  assert.match(container.querySelector('.tv2-product__badge').textContent, /Chờ duyệt/);
  assert.equal(button('Chỉnh sửa'), undefined);
});
test('all editable domain states expose edit; other states never offer seller approval', async () => {
  for (const status of ['DRAFT', 'REJECTED', 'INACTIVE', 'PENDING_REVIEW', 'ACTIVE', 'SUSPENDED']) {
    await mount({ state: { ...ready, products: [{ ...product, status }] } });
    assert.equal(!!button('Chỉnh sửa'), ['DRAFT', 'REJECTED', 'INACTIVE'].includes(status));
    assert.equal(button('Duyệt sản phẩm'), undefined);
  }
});
test('local filters search names/SKU and status without claiming server pagination', async () => {
  await mount();
  const search = container.querySelector('input[type="search"]');
  await act(() => { Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(search, 'không thấy'); search.dispatchEvent(new window.Event('input', { bubbles: true })); });
  assert.match(container.textContent, /Không tìm thấy sản phẩm/);
  await click(button('Xóa bộ lọc')); assert.equal(container.querySelectorAll('.tv2-product__card').length, 1);
  const filter = container.querySelector('.tv2-product__toolbar select');
  await act(() => { filter.value = 'ACTIVE'; filter.dispatchEvent(new window.Event('change', { bubbles: true })); });
  assert.equal(container.querySelectorAll('.tv2-product__card').length, 0);
});
test('error retry invokes host callback', async () => { let calls = 0; await mount({ state: { status: 'error' }, onRetry: () => calls++ }); await click(button('Thử lại')); assert.equal(calls, 1); });
