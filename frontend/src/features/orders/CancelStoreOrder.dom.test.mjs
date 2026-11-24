// Real React DOM regression on happy-dom: a customer confirms before a Store order is cancelled.
// Run from frontend with: npm test
import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { Window } from 'happy-dom';
import { createServer } from 'vite';

let environment, vite, createRoot, act, CancelStoreOrder;
before(async () => {
  environment = new Window({ url: 'http://localhost/' });
  globalThis.window = environment;
  globalThis.document = environment.document;
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: environment.navigator });
  globalThis.HTMLElement = environment.HTMLElement;
  globalThis.Node = environment.Node;
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  ({ act } = await import('react'));
  vite = await createServer({ server: { middlewareMode: true, hmr: false, watch: null }, optimizeDeps: { noDiscovery: true }, logLevel: 'silent' });
  ({ CancelStoreOrder } = await vite.ssrLoadModule('/src/features/orders/CancelStoreOrder.tsx'));
});
after(async () => {
  await vite?.close();
  await environment?.happyDOM?.abort?.();
});

const button = (host, name) => [...host.querySelectorAll('button')].find((b) => b.textContent === name);
const type = async (input, value) => act(async () => {
  Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(input, value);
  input.dispatchEvent(new window.Event('input', { bubbles: true }));
});

test('one click only asks; the order is cancelled after the customer confirms, with the typed reason', async () => {
  const cancelled = [];
  const host = document.createElement('div');
  document.body.appendChild(host);
  const root = createRoot(host);
  await act(async () => root.render(createElement(CancelStoreOrder, { pending: false, onCancel: (reason) => cancelled.push(reason) })));

  await act(async () => button(host, 'Hủy đơn của cửa hàng này').click());
  assert.deepEqual(cancelled, []);
  await act(async () => button(host, 'Không hủy').click());
  assert.equal(button(host, 'Xác nhận hủy'), undefined);

  await act(async () => button(host, 'Hủy đơn của cửa hàng này').click());
  await type(host.querySelector('input'), '  Đặt nhầm  ');
  await act(async () => button(host, 'Xác nhận hủy').click());
  assert.deepEqual(cancelled, ['Đặt nhầm']);
  await act(async () => root.unmount());
});
