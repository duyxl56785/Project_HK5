// Real React DOM regression on happy-dom for typing a quantity and clearing the Cart.
// Run from frontend with: npm test
import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { Window } from 'happy-dom';
import { createServer } from 'vite';

let environment, vite, createRoot, act, CartView;
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
  ({ CartView } = await vite.ssrLoadModule('/src/features/cart/CartView.tsx'));
});
after(async () => {
  await vite?.close();
  await environment?.happyDOM?.abort?.();
});

const snapshot = {
  revision: '7', claimed: false,
  items: [{ productId: 'p1', storeId: 's1', sellerId: 'u1', title: 'Áo thun', quantity: 2, unitPriceSnapshot: '10000.00' }],
};

async function mount(props) {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const root = createRoot(host);
  await act(async () => root.render(createElement(CartView, { state: { status: 'ready', snapshot }, ...props })));
  return { host, root };
}

// React tracks the value it last rendered, so typing must go through the native setter.
const type = async (input, value) => act(async () => {
  Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(input, value);
  input.dispatchEvent(new window.Event('input', { bubbles: true }));
});
const button = (host, name) => [...host.querySelectorAll('button')].find((b) => b.textContent === name);

test('typing 12 sends one change for 12 when the field is left, not one per keystroke', async () => {
  const changes = [];
  const { host, root } = await mount({ onQuantityChange: (id, quantity) => changes.push([id, quantity]) });
  const input = host.querySelector('#qty-p1');
  await type(input, '');
  await type(input, '1');
  await type(input, '12');
  assert.deepEqual(changes, []);
  assert.equal(input.value, '12');
  await act(async () => { input.focus(); input.blur(); });
  assert.deepEqual(changes, [['p1', 12]]);
  await act(async () => root.unmount());
});

test('Enter applies the typed quantity', async () => {
  const changes = [];
  const { host, root } = await mount({ onQuantityChange: (id, quantity) => changes.push(quantity) });
  const input = host.querySelector('#qty-p1');
  input.focus();
  await type(input, '5');
  await act(async () => input.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true })));
  assert.deepEqual(changes, [5]);
  await act(async () => root.unmount());
});

test('an invalid quantity is not sent and the field shows the Cart quantity again', async () => {
  const changes = [];
  const { host, root } = await mount({ onQuantityChange: (id, quantity) => changes.push(quantity) });
  const input = host.querySelector('#qty-p1');
  for (const bad of ['0', '', '2.5', '1001']) {
    await type(input, bad);
    await act(async () => { input.focus(); input.blur(); });
    assert.equal(input.value, '2', bad);
  }
  assert.deepEqual(changes, []);
  await act(async () => root.unmount());
});

test('a quantity above the stock is not sent and says how many are left', async () => {
  const changes = [];
  const { host, root } = await mount({ stock: { p1: 3 }, onQuantityChange: (id, quantity) => changes.push(quantity) });
  const input = host.querySelector('#qty-p1');
  assert.equal(input.max, '3');
  await type(input, '5');
  await act(async () => { input.focus(); input.blur(); });
  assert.deepEqual(changes, []);
  assert.equal(input.value, '2');
  assert.match(host.textContent, /Chỉ còn 3 sản phẩm/);
  await type(input, '3');
  await act(async () => { input.focus(); input.blur(); });
  assert.deepEqual(changes, [3]);
  await act(async () => root.unmount());
});

test('a quantity the server refused can be sent again without typing another number', async () => {
  const changes = [];
  let fail = true;
  const { host, root } = await mount({ onQuantityChange: (id, quantity) => {
    changes.push(quantity);
    return fail ? Promise.reject(new Error('503')) : Promise.resolve();
  } });
  const input = host.querySelector('#qty-p1');
  await type(input, '5');
  await act(async () => { input.focus(); input.blur(); });
  assert.deepEqual(changes, [5]);
  // The Cart reload after the failure still says 2, so nothing resets the field from outside.
  fail = false;
  await act(async () => { input.focus(); input.blur(); });
  assert.deepEqual(changes, [5, 5]);
  // Once a send is accepted, Enter then leaving the field still sends it only once.
  await act(async () => { input.focus(); input.blur(); });
  assert.deepEqual(changes, [5, 5]);
  await act(async () => root.unmount());
});

test('a line already above the stock is flagged so the customer lowers it before ordering', async () => {
  const { host, root } = await mount({ stock: { p1: 1 }, onQuantityChange: () => {} });
  assert.match(host.textContent, /Chỉ còn 1 sản phẩm/);
  await act(async () => root.unmount());
});

test('clearing the Cart asks on the page, so it also works where browser dialogs are blocked', async () => {
  let cleared = 0;
  const { host, root } = await mount({ onClear: () => { cleared += 1; } });
  await act(async () => button(host, 'Xóa giỏ hàng').click());
  assert.equal(cleared, 0);
  await act(async () => button(host, 'Không').click());
  assert.equal(button(host, 'Xóa hết'), undefined);
  await act(async () => button(host, 'Xóa giỏ hàng').click());
  await act(async () => button(host, 'Xóa hết').click());
  assert.equal(cleared, 1);
  await act(async () => root.unmount());
});
