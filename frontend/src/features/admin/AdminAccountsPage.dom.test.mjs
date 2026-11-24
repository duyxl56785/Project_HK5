// Locking an account asks on the page, so it also works where the browser blocks dialogs. Mock HTTP only.
import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { Window } from 'happy-dom';
import { createServer } from 'vite';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

let environment, vite, createRoot, act, AdminAccountsPage;
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
  ({ AdminAccountsPage } = await vite.ssrLoadModule('/src/features/admin/AdminPages.tsx'));
});
after(async () => {
  await vite?.close();
  await environment?.happyDOM?.abort?.();
});

async function until(ok) {
  for (let i = 0; i < 100 && !ok(); i++) await act(async () => new Promise((resolve) => setTimeout(resolve, 20)));
}
const button = (host, name) => [...host.querySelectorAll('button')].find((b) => b.textContent === name);

test('Khóa only asks; the account is locked after "Xác nhận khóa", and "Không" cancels', async () => {
  const originalFetch = globalThis.fetch;
  const originalConfirm = window.confirm;
  window.confirm = () => { throw new Error('window.confirm must not be used'); };
  const posts = [];
  globalThis.fetch = async (path, options) => {
    if ((options?.method ?? 'GET') === 'POST') { posts.push(String(path)); return Response.json({}); }
    return Response.json([{ id: 'acc-1', email: 'khach@hk5.local', roles: ['CUSTOMER'], status: 'ACTIVE', createdAt: new Date().toISOString() }]);
  };
  const host = document.createElement('div');
  document.body.append(host);
  const root = createRoot(host);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  try {
    await act(async () => root.render(createElement(QueryClientProvider, { client }, createElement(AdminAccountsPage))));
    await until(() => button(host, 'Khóa'));

    await act(async () => button(host, 'Khóa').click());
    assert.deepEqual(posts, []);
    assert.match(host.textContent, /Khóa khach@hk5\.local\?/);
    await act(async () => button(host, 'Không').click());
    assert.equal(button(host, 'Xác nhận khóa'), undefined);

    await act(async () => button(host, 'Khóa').click());
    await act(async () => button(host, 'Xác nhận khóa').click());
    await until(() => posts.length > 0);
    assert.deepEqual(posts, ['/api/admin/accounts/acc-1/disable']);
  } finally {
    globalThis.fetch = originalFetch;
    window.confirm = originalConfirm;
    await act(async () => root.unmount());
    client.clear();
    host.remove();
  }
});
