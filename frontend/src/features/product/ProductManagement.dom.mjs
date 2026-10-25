import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { pathToFileURL } from 'node:url';
import { act as reactAct, createElement } from 'react';
import { createServer } from 'vite';
import { categories, product, ready, store } from './productTestFixtures.mjs';

let window, server, ProductManagementPage, createRoot, container, root;
const act = (callback) => reactAct(async () => { await callback(); });
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
const sessionKey = 'test-session-A';
const mount = (props = {}) => act(() => root.render(createElement(ProductManagementPage, { state: ready, sessionKey, ...props })));
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
const completedReload = (request, snapshot = ready) => ({ status: 'confirmed', receipt: { ...request }, snapshot });
async function assertLocked(calls, count = 1) {
  assert.equal(button('Lưu thay đổi').disabled, true);
  await submit();
  assert.equal(calls(), count);
}

for (const outcome of ['unavailable', 'conflict', 'error', 'throw']) {
  test(`clone old products must preserve write lock after ${outcome}`, async () => {
    let calls = 0;
    const actions = { update: async () => { calls++; if (outcome === 'throw') throw new Error('offline'); return { status: outcome }; } };
    await mount({ actions }); await click(button('Chỉnh sửa')); await submit();
    assert.ok(container.querySelector('form'));
    assert.equal(button('Lưu thay đổi').disabled, true);
    for (let i = 0; i < 3; i++) await mount({ state: { ...ready, products: [...ready.products] }, actions });
    const locked = button('Lưu thay đổi').disabled;
    await submit();
    assert.deepEqual({ locked, calls }, { locked: true, calls: 1 }, 'array clones are not an authoritative reload receipt');
    assert.equal(container.querySelector('[name="name"]').value, product.name);
  });
}

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
  await mount({ actions: { create: async (command) => { calls.push(command); return { status: 'confirmed' }; } }, onReload: async (request) => { reload = request; return { status: 'unavailable' }; } });
  await click(button('+ Thêm sản phẩm'));
  assert.equal(container.querySelector('[name="currency"]').value, '');
  await fill(); await submit();
  assert.deepEqual(calls, [{ storeId: store.id, name: 'Cốc mới', sku: 'Moi-é', categoryId: categories[0].id, unitPrice: '1000000000', currency: 'VND' }]);
  assert.equal(reload.storeId, store.id);
  assert.equal(reload.sessionKey, sessionKey);
  assert.ok(reload.requestId);
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

test('calling onReload without a completed response cannot unlock writes', async () => {
  let calls = 0; const pending = deferred();
  const actions = { update: async () => { calls++; return { status: 'unavailable' }; } };
  await mount({ actions, onReload: () => pending.promise }); await click(button('Chỉnh sửa')); await submit();
  await click(button('Tải lại danh sách')); await assertLocked(() => calls);
  for (let i = 0; i < 3; i++) await mount({ actions, onReload: () => pending.promise, state: { ...ready, products: [...ready.products] } });
  await assertLocked(() => calls);
  await act(() => pending.resolve({ status: 'unavailable' })); await assertLocked(() => calls);
});
for (const failure of ['unavailable', 'conflict', 'error', 'throw', 'timeout']) {
  test(`reload ${failure} keeps the write barrier`, async () => {
    let calls = 0;
    const actions = { update: async () => { calls++; return { status: 'unavailable' }; } };
    const onReload = async () => { if (failure === 'throw' || failure === 'timeout') throw new Error('PRIVATE_TIMEOUT'); return { status: failure }; };
    await mount({ actions, onReload }); await click(button('Chỉnh sửa')); await submit();
    await click(button('Tải lại danh sách'));
    await mount({ actions, onReload, state: { ...ready, products: [...ready.products] } });
    await assertLocked(() => calls); assert.doesNotMatch(container.textContent, /PRIVATE_TIMEOUT/);
  });
}
for (const mismatch of ['store', 'session', 'request', 'missing-receipt']) {
  test(`reload receipt with wrong ${mismatch} cannot unlock`, async () => {
    let calls = 0;
    const actions = { update: async () => { calls++; return { status: 'conflict' }; } };
    const onReload = async (request) => {
      const result = completedReload(request);
      if (mismatch === 'store') result.receipt.storeId = categories[0].id;
      if (mismatch === 'session') result.receipt.sessionKey = 'different-session';
      if (mismatch === 'request') result.receipt.requestId = 'old-request';
      if (mismatch === 'missing-receipt') delete result.receipt;
      return result;
    };
    await mount({ actions, onReload }); await click(button('Chỉnh sửa')); await submit(); await click(button('Tải lại danh sách'));
    await assertLocked(() => calls);
  });
}
for (const invalid of ['different-store', 'bad-price', 'cross-store-row', 'duplicate', 'missing']) {
  test(`reload with invalid ${invalid} snapshot cannot unlock`, async () => {
    let calls = 0;
    const actions = { update: async () => { calls++; return { status: 'unavailable' }; } };
    const onReload = async (request) => {
      const snapshot = { store, categories, products: [{ ...product }] };
      if (invalid === 'different-store') snapshot.store = { ...store, id: categories[0].id };
      if (invalid === 'bad-price') snapshot.products[0].unitPrice = '1.5';
      if (invalid === 'cross-store-row') snapshot.products[0].storeId = categories[0].id;
      if (invalid === 'duplicate') snapshot.products.push({ ...product });
      return completedReload(request, invalid === 'missing' ? null : snapshot);
    };
    await mount({ actions, onReload }); await click(button('Chỉnh sửa')); await submit(); await click(button('Tải lại danh sách'));
    await assertLocked(() => calls);
  });
}
test('valid current-scope receipt unlocks only after its validated snapshot is displayed', async () => {
  let calls = 0, request;
  const pending = deferred(), refreshed = { ...ready, products: [{ ...product, version: '9007199254740994' }] };
  const actions = { update: async () => { calls++; return { status: 'unavailable' }; } };
  const onReload = (value) => { request = value; return pending.promise; };
  await mount({ actions, onReload }); await click(button('Chỉnh sửa')); await submit(); await click(button('Tải lại danh sách'));
  await act(() => pending.resolve(completedReload(request, refreshed)));
  await assertLocked(() => calls);
  await mount({ actions, onReload, state: refreshed });
  assert.equal(button('+ Thêm sản phẩm').disabled, false, 'valid reload clears the barrier');
  assert.equal(button('Lưu thay đổi').disabled, true, 'old editor must still fail its version check');
  await submit(); assert.equal(calls, 1);
  await click(button('Hủy')); await click(button('Chỉnh sửa')); await submit(); assert.equal(calls, 2);
});
test('successful same-version authoritative reload permits a retry with the exact version', async () => {
  const commands = [];
  const actions = { update: async (command) => { commands.push(command); return { status: 'unavailable' }; } };
  await mount({ actions, onReload: async (request) => completedReload(request) });
  await click(button('Chỉnh sửa')); await submit(); await click(button('Tải lại danh sách'));
  assert.equal(button('Lưu thay đổi').disabled, false);
  await submit(); assert.equal(commands.length, 2);
  assert.equal(commands[1].expectedVersion, product.version);
  assert.equal('sku' in commands[1], false); assert.equal('storeId' in commands[1], false);
});
test('a consumed receipt cannot satisfy a later reload request', async () => {
  let calls = 0, oldResult;
  const actions = { update: async () => { calls++; return { status: 'unavailable' }; } };
  const onReload = async (request) => { if (!oldResult) oldResult = completedReload(request); return oldResult; };
  await mount({ actions, onReload }); await click(button('Chỉnh sửa')); await submit(); await click(button('Tải lại danh sách'));
  assert.equal(button('Lưu thay đổi').disabled, false);
  await submit(); await click(button('Tải lại danh sách')); await assertLocked(() => calls, 2);
});
test('late completion of a superseded reload cannot unlock', async () => {
  let calls = 0; const requests = [], first = deferred(), second = deferred();
  const actions = { update: async () => { calls++; return { status: 'unavailable' }; } };
  const onReload = (request) => { requests.push(request); return requests.length === 1 ? first.promise : second.promise; };
  await mount({ actions, onReload }); await click(button('Chỉnh sửa')); await submit();
  await click(button('Tải lại danh sách')); await click(button('Tải lại danh sách'));
  assert.notEqual(requests[0].requestId, requests[1].requestId);
  await act(() => first.resolve(completedReload(requests[0]))); await assertLocked(() => calls);
  await act(() => second.resolve({ status: 'unavailable' })); await assertLocked(() => calls);
});
for (const change of ['session', 'store', 'unauthorized']) {
  test(`late reload completion after ${change} change cannot affect new scope`, async () => {
    let request, calls = 0; const pending = deferred();
    const actions = { update: async () => { calls++; return { status: 'unavailable' }; } };
    const onReload = (value) => { request = value; return pending.promise; };
    await mount({ actions, onReload }); await click(button('Chỉnh sửa')); await submit(); await click(button('Tải lại danh sách'));
    if (change === 'unauthorized') await mount({ state: { status: 'unauthorized' } });
    else if (change === 'store') await mount({ state: { ...ready, store: { ...store, id: categories[0].id, name: 'Store khác' }, products: [] } });
    else {
      await mount({ actions, sessionKey: 'test-session-B' });
      await click(button('Chỉnh sửa')); await submit();
    }
    await act(() => pending.resolve(completedReload(request)));
    if (change === 'session') await assertLocked(() => calls, 2);
    else { assert.equal(container.querySelector('form'), null); assert.doesNotMatch(container.textContent, /GOM-MAY/); }
  });
}
test('session change during mutation discards the old private form and response', async () => {
  const pending = deferred(); let calls = 0, reloads = 0;
  await mount({ actions: { update: () => pending.promise }, onReload: async () => { reloads++; return { status: 'unavailable' }; } });
  await click(button('Chỉnh sửa')); await input('name', 'PRIVATE_FORM'); await submit();
  const actions = { update: async () => { calls++; return { status: 'unavailable' }; } };
  await mount({ actions, sessionKey: 'test-session-B' });
  assert.equal(container.querySelector('form'), null); assert.doesNotMatch(container.textContent, /PRIVATE_FORM/);
  await click(button('Chỉnh sửa')); await submit();
  await act(() => pending.resolve({ status: 'confirmed' })); await assertLocked(() => calls);
  assert.equal(reloads, 0);
});
test('reload authorization denial immediately hides private data', async () => {
  await mount({ actions: { update: async () => ({ status: 'unavailable' }) }, onReload: async () => ({ status: 'unauthorized' }) });
  await click(button('Chỉnh sửa')); await submit(); await click(button('Tải lại danh sách'));
  assert.match(container.textContent, /Không có quyền truy cập/);
  assert.doesNotMatch(container.textContent, /Gốm Việt|GOM-MAY/); assert.equal(container.querySelector('form'), null);
});
test('invalid rerender cannot clear the barrier after a valid source is restored', async () => {
  let calls = 0;
  const actions = { update: async () => { calls++; return { status: 'unavailable' }; } };
  await mount({ actions }); await click(button('Chỉnh sửa')); await submit();
  await mount({ actions, state: { ...ready, products: [{ ...product, unitPrice: 'invalid' }] } });
  assert.doesNotMatch(container.textContent, /GOM-MAY/);
  await mount({ actions, state: { ...ready, products: [...ready.products] } });
  if (!container.querySelector('form')) await click(button('Chỉnh sửa'));
  await assertLocked(() => calls);
});
test('missing session lifetime key never enables a write adapter', async () => {
  let calls = 0;
  await mount({ sessionKey: undefined, actions: { update: async () => { calls++; return { status: 'confirmed' }; } } });
  await click(button('Chỉnh sửa')); await assertLocked(() => calls, 0);
});
test('publishing a validated snapshot before resolving its receipt also unlocks safely', async () => {
  let request, calls = 0; const pending = deferred();
  const refreshed = { ...ready, products: [{ ...product, name: 'Tên từ server', version: '9007199254740994' }] };
  const actions = { update: async () => { calls++; return { status: 'conflict' }; } };
  const onReload = (value) => { request = value; return pending.promise; };
  await mount({ actions, onReload }); await click(button('Chỉnh sửa')); await submit(); await click(button('Tải lại danh sách'));
  await mount({ actions, onReload, state: refreshed });
  assert.equal(button('+ Thêm sản phẩm').disabled, true, 'data alone cannot unlock');
  await act(() => pending.resolve(completedReload(request, refreshed)));
  assert.equal(button('+ Thêm sản phẩm').disabled, false);
  await submit(); assert.equal(calls, 1, 'old edit version stays blocked');
});
test('a correct receipt cannot authorize cross-Store data after reload', async () => {
  let calls = 0;
  const actions = { update: async () => { calls++; return { status: 'unavailable' }; } };
  const onReload = async (request) => completedReload(request);
  await mount({ actions, onReload }); await click(button('Chỉnh sửa')); await submit(); await click(button('Tải lại danh sách'));
  assert.equal(button('Lưu thay đổi').disabled, false);
  await mount({ actions, onReload, state: { ...ready, products: [{ ...product, storeId: categories[0].id }] } });
  assert.doesNotMatch(container.textContent, /GOM-MAY/);
  await mount({ actions, onReload }); await assertLocked(() => calls);
});
test('invalid data cancels a pending receipt even if the old data is later restored', async () => {
  let calls = 0, request; const pending = deferred();
  const actions = { update: async () => { calls++; return { status: 'unavailable' }; } };
  const onReload = (value) => { request = value; return pending.promise; };
  await mount({ actions, onReload }); await click(button('Chỉnh sửa')); await submit(); await click(button('Tải lại danh sách'));
  await mount({ actions, onReload, state: { ...ready, products: [{ ...product, version: -1 }] } });
  await act(() => pending.resolve(completedReload(request)));
  await mount({ actions, onReload }); await assertLocked(() => calls);
});
test('unmounted workspace ignores reload completion without updating another instance', async () => {
  let request; const pending = deferred();
  await mount({ actions: { update: async () => ({ status: 'unavailable' }) }, onReload: (value) => { request = value; return pending.promise; } });
  await click(button('Chỉnh sửa')); await submit(); await click(button('Tải lại danh sách'));
  await act(() => root.render(createElement('p', null, 'Đã rời trang')));
  await act(() => pending.resolve(completedReload(request)));
  assert.equal(container.textContent, 'Đã rời trang');
});
test('loading/error round trip without a receipt preserves the same-scope write barrier', async () => {
  let calls = 0;
  const actions = { update: async () => { calls++; return { status: 'unavailable' }; } };
  await mount({ actions }); await click(button('Chỉnh sửa')); await submit();
  await mount({ state: { status: 'loading' } }); await mount({ state: { status: 'error' } });
  await mount({ actions });
  assert.equal(button('+ Thêm sản phẩm').disabled, true);
  assert.equal(calls, 1);
});
test('401/403-style denial cannot be bypassed by restoring ready props in the same session', async () => {
  let calls = 0;
  const actions = { update: async () => { calls++; return { status: 'unauthorized' }; } };
  await mount({ actions }); await click(button('Chỉnh sửa')); await submit();
  assert.doesNotMatch(container.textContent, /GOM-MAY/);
  await mount({ state: { status: 'unauthorized' } }); await mount({ actions });
  assert.equal(button('+ Thêm sản phẩm').disabled, true);
  assert.equal(calls, 1);
});
test('pending mutation retains the write barrier across loading and ignores its late response', async () => {
  const pending = deferred(); let reloads = 0;
  const actions = { update: () => pending.promise };
  await mount({ actions }); await click(button('Chỉnh sửa')); await submit();
  await mount({ state: { status: 'loading' } });
  await mount({ actions, onReload: async () => { reloads++; return { status: 'unavailable' }; } });
  assert.equal(button('+ Thêm sản phẩm').disabled, true);
  await act(() => pending.resolve({ status: 'confirmed' }));
  assert.equal(button('+ Thêm sản phẩm').disabled, true); assert.equal(reloads, 0);
});
