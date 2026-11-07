// TV2 #154: Admin Return oversight is read-only. Mock HTTP only, not Gateway/Order/PostgreSQL E2E.
import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createElement } from 'react';
import { Window } from 'happy-dom';
import { createServer } from 'vite';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

let environment, vite, createRoot, act, AdminReturnsPage;
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
  ({ AdminReturnsPage } = await vite.ssrLoadModule('/src/features/admin/AdminPages.tsx'));
});
after(async () => {
  await vite?.close();
  await environment?.happyDOM?.abort?.();
});

async function until(ok) {
  for (let i = 0; i < 100 && !ok(); i++) await act(async () => new Promise(resolve => setTimeout(resolve, 20)));
}

test('Admin sees Return rows with status filter and no action buttons', async () => {
  const originalFetch = globalThis.fetch;
  const paths = [];
  globalThis.fetch = async (path, options) => {
    paths.push(String(path));
    assert.equal(options?.method ?? 'GET', 'GET');
    return new Response(JSON.stringify([{
      requestId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', orderId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      storeOrderId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', storeId: 'store-1', status: 'REQUESTED',
      reason: 'Sai kích thước', requestedAt: new Date().toISOString(),
    }]), { status: 200, headers: { 'Content-Type': 'application/json' } });
  };
  const host = document.createElement('div');
  document.body.append(host);
  const root = createRoot(host);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  try {
    await act(async () => {
      root.render(createElement(QueryClientProvider, { client }, createElement(AdminReturnsPage)));
    });
    await until(() => /Sai kích thước/.test(host.textContent));
    assert.match(host.textContent, /Sai kích thước/);
    assert.match(host.textContent, /store-1/);
    assert.match(host.textContent, /Đã yêu cầu trả/);
    assert.equal(host.querySelector('button'), null);
    assert.equal(paths[0], '/api/admin/returns');

    await act(async () => {
      const select = host.querySelector('select');
      select.value = 'RECEIVED';
      select.dispatchEvent(new environment.Event('change', { bubbles: true }));
    });
    await until(() => paths.length > 1);
    assert.equal(paths.at(-1), '/api/admin/returns?status=RECEIVED');
  } finally {
    globalThis.fetch = originalFetch;
    await act(async () => root.unmount());
    client.clear();
    host.remove();
  }
});
