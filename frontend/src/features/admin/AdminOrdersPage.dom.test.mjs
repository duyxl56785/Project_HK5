// A Store order with an open Return cannot be settled, so the COD page does not offer it. Mock HTTP only.
import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { Window } from 'happy-dom';
import { createServer } from 'vite';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

let environment, vite, createRoot, act, AdminOrdersPage;
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
  ({ AdminOrdersPage } = await vite.ssrLoadModule('/src/features/admin/AdminPages.tsx'));
});
after(async () => {
  await vite?.close();
  await environment?.happyDOM?.abort?.();
});

async function until(ok) {
  for (let i = 0; i < 100 && !ok(); i++) await act(async () => new Promise((resolve) => setTimeout(resolve, 20)));
}

const collected = (id, orderId) => ({
  id, orderId, storeId: 'store-1', storeName: 'Tiệm demo', status: 'DELIVERED', codStatus: 'COLLECTED',
  gross: '95000', sellerNet: '90250', settledAt: null,
});
const returned = (storeOrderId, status) => ({
  requestId: 'r-' + storeOrderId, orderId: 'o-' + storeOrderId, storeOrderId, storeId: 'store-1',
  status, reason: 'Sai màu', requestedAt: new Date().toISOString(),
});

async function mount(returnsFor) {
  const paths = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (path) => {
    path = String(path);
    paths.push(path);
    if (path.startsWith('/api/admin/store-orders')) {
      return Response.json([
        collected('so-requested', 'aaaaaaa1-0000-4000-8000-000000000000'),
        collected('so-approved', 'aaaaaaa2-0000-4000-8000-000000000000'),
        collected('so-received', 'aaaaaaa3-0000-4000-8000-000000000000'),
        collected('so-free', 'aaaaaaa4-0000-4000-8000-000000000000'),
      ]);
    }
    if (path.startsWith('/api/admin/returns')) return returnsFor(new URL(path, 'http://localhost').searchParams.get('status'));
    return Response.json([]);
  };
  const host = document.createElement('div');
  document.body.append(host);
  const root = createRoot(host);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => root.render(createElement(QueryClientProvider, { client }, createElement(AdminOrdersPage))));
  const row = (shortId) => [...host.querySelectorAll('tbody tr')].find((tr) => tr.textContent.includes(shortId));
  const cleanup = async () => {
    globalThis.fetch = originalFetch;
    await act(async () => root.unmount());
    client.clear();
    host.remove();
  };
  return { host, row, paths, cleanup };
}

test('an order with a requested, approved or received Return shows why instead of a payout button', async () => {
  const view = await mount((status) => Response.json({
    REQUESTED: [returned('so-requested', 'REQUESTED')],
    APPROVED: [returned('so-approved', 'APPROVED')],
    RECEIVED: [returned('so-received', 'RECEIVED')],
  }[status] ?? []));
  try {
    await until(() => view.row('#AAAAAAA4') && !/Thanh toán/.test(view.row('#AAAAAAA3')?.textContent ?? 'Thanh toán'));
    const rows = [...view.host.querySelectorAll('tbody tr')];
    assert.equal(rows.length, 4);
    for (const held of rows.slice(0, 3)) {
      assert.equal(held.querySelector('button'), null, held.textContent);
      assert.match(held.textContent, /trả hàng/);
    }
    assert.match(rows[3].querySelector('button')?.textContent ?? '', /^Thanh toán/);
    assert.ok(!view.paths.includes('/api/admin/returns?status=REJECTED'), 'a rejected Return does not hold payment');
  } finally {
    await view.cleanup();
  }
});

test('when the Returns list cannot be read the button stays, since the server still refuses a held payout', async () => {
  const view = await mount(() => new Response('{}', { status: 503 }));
  try {
    await until(() => view.host.querySelectorAll('tbody tr button').length === 4);
    assert.equal(view.host.querySelectorAll('tbody tr button').length, 4);
  } finally {
    await view.cleanup();
  }
});
