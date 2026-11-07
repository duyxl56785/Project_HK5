// TV2 #155/#156: Seller Return UI. Mock HTTP only, not Gateway/Order/PostgreSQL E2E.
import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createElement } from 'react';
import { Window } from 'happy-dom';
import { createServer } from 'vite';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

let environment, vite, createRoot, act, SellerReturnsPage;
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
  ({ SellerReturnsPage } = await vite.ssrLoadModule('/src/features/seller/SellerReturnsPage.tsx'));
});
after(async () => {
  await vite?.close();
  await environment?.happyDOM?.abort?.();
});

const row = (requestId, status) => ({ requestId, orderId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  storeOrderId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', status, reason: 'Sai kích thước',
  requestedAt: new Date().toISOString(), reviewedAt: null, receivedAt: null, receiptReference: null });
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

async function until(ok) {
  for (let i = 0; i < 100 && !ok(); i++) await act(async () => new Promise(resolve => setTimeout(resolve, 20)));
}

async function mounted(respond) {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path: String(path), method: options.method ?? 'GET', body: options.body ? JSON.parse(String(options.body)) : undefined });
    return respond(calls.at(-1), calls);
  };
  const host = document.createElement('div');
  document.body.append(host);
  const root = createRoot(host);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  await act(async () => root.render(createElement(QueryClientProvider, { client }, createElement(SellerReturnsPage))));
  const button = text => [...host.querySelectorAll('button')].find(b => b.textContent === text);
  const click = async text => act(async () => button(text).dispatchEvent(new environment.MouseEvent('click', { bubbles: true })));
  const close = async () => {
    globalThis.fetch = originalFetch;
    await act(async () => root.unmount());
    client.clear();
    host.remove();
  };
  return { host, calls, button, click, close };
}

test('REQUESTED offers approve/reject; APPROVED needs a receipt reference to receive', async () => {
  const view = await mounted(call => call.method === 'GET'
    ? json([row('11111111-1111-4111-8111-111111111111', 'REQUESTED'), row('22222222-2222-4222-8222-222222222222', 'APPROVED')])
    : json(row('x', 'APPROVED')));
  try {
    await until(() => view.button('Từ chối'));
    assert.ok(view.button('Đồng ý trả hàng'));
    assert.equal(view.button('Xác nhận đã nhận hàng').disabled, true);

    await view.click('Đồng ý trả hàng');
    await until(() => view.calls.some(c => c.method === 'POST'));
    assert.equal(view.calls.find(c => c.method === 'POST').path,
      '/api/seller/returns/11111111-1111-4111-8111-111111111111/approve');

    await act(async () => {
      const input = view.host.querySelector('input[aria-label="Mã nhận hàng"]');
      Object.getOwnPropertyDescriptor(environment.HTMLInputElement.prototype, 'value').set.call(input, '  VD-123 ');
      input.dispatchEvent(new environment.Event('input', { bubbles: true }));
    });
    assert.equal(view.button('Xác nhận đã nhận hàng').disabled, false);
    await view.click('Xác nhận đã nhận hàng');
    await until(() => view.calls.some(c => c.path.endsWith('/receive')));
    const receive = view.calls.find(c => c.path.endsWith('/receive'));
    assert.equal(receive.path, '/api/seller/returns/22222222-2222-4222-8222-222222222222/receive');
    assert.deepEqual(receive.body, { receiptReference: 'VD-123' });
  } finally { await view.close(); }
});

test('reject needs a second explicit click and can be cancelled', async () => {
  const view = await mounted(call => call.method === 'GET'
    ? json([row('11111111-1111-4111-8111-111111111111', 'REQUESTED')])
    : json(row('11111111-1111-4111-8111-111111111111', 'REJECTED')));
  try {
    await until(() => view.button('Từ chối'));
    await view.click('Từ chối');
    assert.equal(view.calls.filter(c => c.method === 'POST').length, 0);
    assert.match(view.host.textContent, /không đổi lại được/);
    await view.click('Hủy');
    assert.ok(view.button('Từ chối'));
    assert.equal(view.button('Xác nhận từ chối'), undefined);

    await view.click('Từ chối');
    await view.click('Xác nhận từ chối');
    await until(() => view.calls.some(c => c.method === 'POST'));
    assert.equal(view.calls.find(c => c.method === 'POST').path,
      '/api/seller/returns/11111111-1111-4111-8111-111111111111/reject');
  } finally { await view.close(); }
});

test('409 after a lost response re-reads the list instead of leaving stale buttons', async () => {
  const view = await mounted((call, calls) => {
    if (call.method === 'POST') return json({ code: 'RETURN_INVALID_TRANSITION', message: 'Action approve is not allowed from APPROVED' }, 409);
    const gets = calls.filter(c => c.method === 'GET').length;
    return json([row('11111111-1111-4111-8111-111111111111', gets === 1 ? 'REQUESTED' : 'APPROVED')]);
  });
  try {
    await until(() => view.button('Đồng ý trả hàng'));
    await view.click('Đồng ý trả hàng');
    await until(() => !view.button('Đồng ý trả hàng'));
    assert.equal(view.calls.filter(c => c.method === 'GET').length, 2);
    assert.ok(view.button('Xác nhận đã nhận hàng'));
  } finally { await view.close(); }
});
