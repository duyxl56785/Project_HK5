// Real React DOM regression using a transient local happy-dom install.
// Run from frontend after: npm.cmd install --no-save --no-package-lock happy-dom
// node --test --test-concurrency=1 src/features/checkout/CheckoutCodView.dom.test.mjs
// Do not treat this fixture-only test as an authenticated backend or browser E2E.
import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createElement } from 'react';
import { Window } from 'happy-dom';
import { createServer } from 'vite';

let environment, vite, createRoot, act, CheckoutCodView;
before(async () => {
  environment = new Window({ url: 'http://localhost/' });
  globalThis.window = environment;
  globalThis.document = environment.document;
  globalThis.navigator = environment.navigator;
  globalThis.HTMLElement = environment.HTMLElement;
  globalThis.Node = environment.Node;
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  ({ act } = await import('react'));
  vite = await createServer({
    server: { middlewareMode: true, hmr: false, watch: null },
    optimizeDeps: { noDiscovery: true },
  });
  ({ CheckoutCodView } = await vite.ssrLoadModule('/src/features/checkout/CheckoutCodView.tsx'));
});
after(async () => {
  await vite?.close();
  await environment?.happyDOM?.abort?.();
});

const cartA = { revision: '1', claimed: false, items: [
  { productId: 'prod-a', storeId: 'store-a', sellerId: 'seller-a', title: 'Private A', quantity: 1, unitPriceSnapshot: '10000' },
] };
const cartB = { revision: '2', claimed: false, items: [
  { productId: 'prod-b', storeId: 'store-b', sellerId: 'seller-b', title: 'Public B', quantity: 1, unitPriceSnapshot: '20000' },
] };
const idA = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const idB = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const ready = (cart, key) => ({ status: 'ready', cart, idempotencyKey: key });

function deferred() {
  let resolve;
  const promise = new Promise(r => { resolve = r; });
  return { promise, resolve };
}
async function mounted() {
  const host = document.createElement('div');
  document.body.append(host);
  const root = createRoot(host);
  const render = async (state, onConfirm, trustedSessionScope) => {
    await act(async () => {
      root.render(createElement(CheckoutCodView, { state, onConfirm, trustedSessionScope }));
    });
  };
  const click = async () => {
    await act(async () => {
      host.querySelector('button')?.dispatchEvent(new environment.MouseEvent('click', { bubbles: true }));
    });
  };
  const close = async () => {
    await act(async () => root.unmount());
    host.remove();
  };
  return { host, render, click, close };
}

test('obsolete A confirmation never appears after unauthorized then ready with Cart B/key B', async () => {
  const ui = await mounted();
  const a = deferred();
  let callsB = 0;
  try {
    await ui.render(ready(cartA, 'key-a'), () => a.promise, 'session-a');
    await ui.click();
    assert.match(ui.host.textContent, /Đang xác minh yêu cầu/);
    await ui.render({ status: 'unauthorized' }, undefined, 'logged-out');
    assert.doesNotMatch(ui.host.textContent, /Private A|store-a|Backend đã xác nhận/);
    await ui.render(ready(cartB, 'key-b'), async () => {
      callsB++;
      return { status: 'confirmed', orderId: idB };
    }, 'session-b');
    assert.match(ui.host.textContent, /Public B/);
    assert.doesNotMatch(ui.host.textContent, /Private A|store-a|Backend đã xác nhận/);
    await act(async () => {
      a.resolve({ status: 'confirmed', orderId: idA });
      await new Promise(resolve => setImmediate(resolve));
    });
    assert.doesNotMatch(ui.host.textContent, /Backend đã xác nhận|aaaaaaaa/);
    assert.equal(ui.host.querySelector('button').disabled, false);
    await ui.click();
    assert.equal(callsB, 1);
    assert.match(ui.host.textContent, /Backend đã xác nhận đơn hàng/);
    assert.match(ui.host.textContent, /bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/);
    assert.doesNotMatch(ui.host.textContent, /aaaaaaaa/);
  } finally { await ui.close(); }
});

test('same Idempotency-Key cannot be replayed after access or Cart scope changed', async () => {
  const ui = await mounted();
  const a = deferred();
  let callsB = 0;
  try {
    await ui.render(ready(cartA, 'reused-key'), () => a.promise, 'session-a');
    await ui.click();
    await ui.render({ status: 'unauthorized' }, undefined, 'logged-out');
    await ui.render(ready(cartB, 'reused-key'), async () => {
      callsB++;
      return { status: 'confirmed', orderId: idB };
    }, 'session-b');
    assert.equal(ui.host.querySelector('button').disabled, true);
    assert.match(ui.host.textContent, /Mã yêu cầu đã được dùng/);
    await ui.click();
    assert.equal(callsB, 0);
    await act(async () => {
      a.resolve({ status: 'unknown' });
      await new Promise(resolve => setImmediate(resolve));
    });
    await ui.render(ready(cartB, 'reused-key'), async () => {
      callsB++;
      return { status: 'confirmed', orderId: idB };
    }, 'session-b');
    assert.equal(ui.host.querySelector('button').disabled, true);
    assert.equal(callsB, 0);
    assert.doesNotMatch(ui.host.textContent, /Backend đã xác nhận/);
  } finally { await ui.close(); }
});

test('direct trusted session switch hides obsolete A result even if cart and key match', async () => {
  const ui = await mounted();
  const a = deferred();
  try {
    await ui.render(ready(cartA, 'shared-key'), () => a.promise, 'principal-a-session');
    await ui.click();
    await ui.render(ready(cartA, 'shared-key'), () => a.promise, 'principal-b-session');
    assert.equal(ui.host.querySelector('button').disabled, true);
    await act(async () => {
      a.resolve({ status: 'confirmed', orderId: idA });
      await new Promise(resolve => setImmediate(resolve));
    });
    assert.doesNotMatch(ui.host.textContent, /Backend đã xác nhận|aaaaaaaa/);
  } finally { await ui.close(); }
});
