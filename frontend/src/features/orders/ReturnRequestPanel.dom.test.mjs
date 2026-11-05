// TV3 #155: DOM-level regression for Return buyer UI. Mock HTTP only.
// Not authenticated Gateway/Order/PostgreSQL or live Playwright E2E.
import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createElement } from 'react';
import { Window } from 'happy-dom';
import { createServer } from 'vite';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

let environment, vite, createRoot, act, ReturnRequestPanel;
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
  vite = await createServer({ server: { middlewareMode: true, hmr: false, watch: null },
    optimizeDeps: { noDiscovery: true } });
  ({ ReturnRequestPanel } = await vite.ssrLoadModule('/src/features/orders/ReturnRequestPanel.tsx'));
});
after(async () => {
  await vite?.close();
  await environment?.happyDOM?.abort?.();
});

const ids = { orderId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  storeOrderId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb' };
const eligible = () => ({ ...ids, status: 'DELIVERED',
  deliveredAt: new Date(Date.now() - 3000).toISOString(),
  settledAt: null, existing: undefined, loading: false, loadError: null });

async function mounted(props) {
  const host = document.createElement('div');
  document.body.append(host);
  const root = createRoot(host);
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false }, queries: { retry: false } } });
  const render = async next => act(async () => {
    root.render(createElement(QueryClientProvider, { client }, createElement(ReturnRequestPanel, next)));
  });
  await render(props);
  const close = async () => {
    await act(async () => root.unmount());
    client.clear();
    host.remove();
  };
  const click = async () => act(async () => {
    host.querySelector('button')?.dispatchEvent(new environment.MouseEvent('click', { bubbles: true }));
    await new Promise(resolve => setTimeout(resolve, 30));
  });
  const type = async text => act(async () => {
    const input = host.querySelector('textarea');
    // Native setter avoids React's value tracker swallowing synthetic input events.
    const setter = Object.getOwnPropertyDescriptor(environment.HTMLTextAreaElement.prototype, 'value')?.set;
    assert.ok(setter);
    setter.call(input, text);
    input.dispatchEvent(new environment.Event('input', { bubbles: true }));
    input.dispatchEvent(new environment.Event('change', { bubbles: true }));
  });
  return { host, render, close, click, type };
}

test('only delivered, unsettled, within-window order offers Return request', async () => {
  const view = await mounted(eligible());
  try {
    assert.match(view.host.textContent, /Yêu cầu trả hàng/);
    assert.equal(view.host.querySelector('button')?.disabled, true);
    await view.render({ ...eligible(), status: 'PLACED' });
    assert.equal(view.host.querySelector('button'), null);
    await view.render({ ...eligible(), settledAt: new Date().toISOString() });
    assert.equal(view.host.querySelector('button'), null);
    await view.render({ ...eligible(), deliveredAt: new Date(Date.now()-9*86400000).toISOString() });
    assert.equal(view.host.querySelector('button'), null);
    await view.render({ ...eligible(), loadError: new Error('Status unavailable') });
    assert.equal(view.host.querySelector('button'), null);
    assert.match(view.host.textContent, /tải lại trang/);
  } finally { await view.close(); }
});

test('existing Return renders status, not a second submit button', async () => {
  const view = await mounted({ ...eligible(), existing: {
    requestId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', ...ids,
    status: 'APPROVED', reason: 'Sai kích thước', requestedAt: new Date().toISOString(),
  } });
  try {
    assert.match(view.host.textContent, /Đã duyệt/);
    assert.match(view.host.textContent, /Sai kích thước/);
    assert.equal(view.host.querySelector('button'), null);
  } finally { await view.close(); }
});

test('lost response never mints a different Idempotency-Key or changes original payload on retry', async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (path, options) => {
    calls.push({ path: String(path), key: options.headers['Idempotency-Key'], body: String(options.body) });
    if (calls.length === 1) throw new Error('simulated transport loss');
    return new Response(JSON.stringify({ requestId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
      ...ids, status: 'REQUESTED', reason: 'Hàng lỗi', requestedAt: new Date().toISOString() }), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      });
  };
  const view = await mounted(eligible());
  try {
    await view.type('Hàng lỗi');
    await view.click();
    assert.equal(calls.length, 1);
    assert.match(view.host.textContent, /Thử lại cùng mã yêu cầu/);
    assert.equal(view.host.querySelector('textarea').disabled, true);
    await view.click();
    assert.equal(calls.length, 2);
    assert.deepEqual(calls[0], calls[1]);
    assert.match(calls[0].path, /\/api\/orders\/.+\/store-orders\/.+\/returns/);
    assert.deepEqual(JSON.parse(calls[0].body), { reason: 'Hàng lỗi' });
    assert.ok(calls[0].key.length > 0);
  } finally {
    globalThis.fetch = originalFetch;
    await view.close();
  }
});
