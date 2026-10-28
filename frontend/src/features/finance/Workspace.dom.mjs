import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { pathToFileURL } from 'node:url';
import { act, createElement } from 'react';
import { createServer } from 'vite';
let window, server, FinanceWorkspace, createRoot, root, container;
before(async () => {
  const { Window } = await import(process.env.TV5_DOM_MODULE ? pathToFileURL(process.env.TV5_DOM_MODULE).href : 'happy-dom');
  window = new Window({ url: 'http://localhost/' });
  for (const name of ['window', 'document', 'navigator', 'HTMLElement', 'HTMLInputElement', 'Event', 'MouseEvent', 'KeyboardEvent']) Object.defineProperty(globalThis, name, { value: name === 'window' ? window : window[name], configurable: true });
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  server = await createServer({ optimizeDeps: { noDiscovery: true, include: [] }, server: { middlewareMode: true, hmr: false, watch: null } });
  ({ FinanceWorkspace } = await server.ssrLoadModule('/src/features/finance/FinanceWorkspace.tsx'));
});
beforeEach(() => { container = window.document.createElement('div'); window.document.body.append(container); root = createRoot(container); });
afterEach(async () => { await act(() => root.unmount()); container.remove(); });
after(async () => { await server?.close(); await window?.happyDOM.abort(); });
const tx = store => ({ id: `PRIVATE-${store}`, orderId: 'DEMO-ORDER', storeId: store, store, seller: `Seller ${store}`, amount: '100', currency: 'VND', delivery: 'DELIVERED', cod: 'UNPAID', verification: 'UNKNOWN', paymentEvidence: null, commission: null, sellerNet: null, settlement: null, createdAt: '2026-10-27T00:00:00Z', updatedAt: '2026-10-27T00:00:00Z' });
const ready = store => ({ status: 'ready', data: { source: 'DEMO', asOf: '2026-10-27T00:00:00Z', transactions: [tx(store)], qaRuns: [], audit: { status: 'blocked', rows: [], asOf: null } } });
const mount = (adapter, scope) => act(() => root.render(createElement(FinanceWorkspace, { adapter, scope })));

test('default component denies access and never invents a demo adapter', async () => {
  await act(() => root.render(createElement(FinanceWorkspace)));
  assert.match(container.textContent, /Không có quyền truy cập/); assert.doesNotMatch(container.textContent, /PRIVATE-|DEMO DATA/);
});
test('late result from old Store is discarded, with no synchronous stale frame', async () => {
  const calls = []; const adapter = { id: 'race', read(scope, signal) { return new Promise(resolve => calls.push({ scope, signal, resolve })); } };
  await mount(adapter, { kind: 'store', storeId: 'A' });
  await mount(adapter, { kind: 'store', storeId: 'B' });
  assert.equal(calls[0].signal.aborted, true); assert.match(container.textContent, /Đang tải dữ liệu/);
  await act(() => calls[1].resolve(ready('B'))); assert.match(container.textContent, /PRIVATE-B/);
  await act(() => calls[0].resolve(ready('A'))); assert.doesNotMatch(container.textContent, /PRIVATE-A/);
});
test('a new adapter object with the same ID cannot reuse previous data', async () => {
  const original = { id: 'same', async read() { return ready('A'); } };
  await mount(original, { kind: 'admin' }); assert.match(container.textContent, /PRIVATE-A/);
  let resolve; const replacement = { id: 'same', read() { return new Promise(r => { resolve = r; }); } };
  await mount(replacement, { kind: 'admin' }); assert.doesNotMatch(container.textContent, /PRIVATE-A/);
  await act(() => resolve(ready('B'))); assert.match(container.textContent, /PRIVATE-B/);
});
test('denied scope never invokes the adapter and removes existing data', async () => {
  let calls = 0; const adapter = { id: 'authority', async read() { calls++; return ready('A'); } };
  await mount(adapter, { kind: 'admin' }); assert.equal(calls, 1);
  await mount(adapter, { kind: 'denied' }); assert.equal(calls, 1);
  assert.match(container.textContent, /Không có quyền truy cập/); assert.doesNotMatch(container.textContent, /PRIVATE-A/);
});
test('dependency errors are sanitized and retry re-reads without stale data', async () => {
  let calls = 0; const adapter = { id: 'error', async read() { calls++; if (calls === 1) throw new Error('PRIVATE_DB_TRACE'); return ready('A'); } };
  await mount(adapter, { kind: 'admin' }); assert.match(container.textContent, /Không thể tải dữ liệu/); assert.doesNotMatch(container.textContent, /PRIVATE_DB_TRACE|PRIVATE-A/);
  const retry = [...container.querySelectorAll('button')].find(b => b.textContent === 'Thử lại');
  await act(() => retry.click()); assert.equal(calls, 2); assert.match(container.textContent, /PRIVATE-A/);
});
