// Cart page against a fake API: a refused quantity change can be retried with the same number.
// Run from frontend with: npm test
import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Window } from 'happy-dom';
import { act, createElement } from 'react';
import { createServer } from 'vite';

let window, vite, createRoot, CartPage;
before(async () => {
  window = new Window({ url: 'http://localhost/' });
  globalThis.window = window;
  globalThis.document = window.document;
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  vite = await createServer({
    server: { middlewareMode: true, hmr: false, watch: null },
    optimizeDeps: { noDiscovery: true },
    logLevel: 'silent',
    // Run the router import through the plugins below instead of loading the CommonJS build.
    ssr: { noExternal: ['react-router-dom'] },
    plugins: [{
      name: 'cart-page-test-customer',
      enforce: 'pre',
      resolveId(id) {
        if (id === '../../shared/auth') return '\0cart-page-test-customer';
        if (id === 'react-router-dom') return '\0cart-page-test-router';
      },
      load(id) {
        if (id === '\0cart-page-test-customer') return "export const useAuth = () => ({ has: (role) => role === 'CUSTOMER' });";
        // The page only renders links; a plain anchor avoids loading the router in Vite SSR.
        if (id === '\0cart-page-test-router') {
          return "import { createElement } from 'react'; export const Link = ({ to, ...props }) => createElement('a', { href: to, ...props });";
        }
      },
    }],
  });
  ({ CartPage } = await vite.ssrLoadModule('/src/features/cart/CartPage.tsx'));
});
after(async () => {
  await vite?.close();
  await window?.happyDOM.abort();
});

const PRODUCT = '00000000-0000-4000-8000-000000000001';
const settle = async (done) => {
  for (let i = 0; i < 100 && !done(); i++) await act(async () => new Promise((resolve) => setTimeout(resolve, 20)));
};

test('after the server refuses a quantity, entering the same number again sends it again', async () => {
  const patches = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (path, options) => {
    const url = new URL(path, 'http://localhost');
    if (options?.method === 'PATCH') {
      patches.push(JSON.parse(options.body).quantity);
      return patches.length === 1
        ? Response.json({ code: 'SERVICE_UNAVAILABLE', message: 'Cart unavailable' }, { status: 503 })
        : Response.json({});
    }
    if (url.pathname === '/api/cart') {
      return Response.json({ revision: 7, claimed: false,
        items: [{ productId: PRODUCT, storeId: 's1', sellerId: 'u1', quantity: 2, unitPriceSnapshot: 1000 }] });
    }
    if (url.pathname === '/api/stores') return Response.json([{ id: 's1', name: 'Tiệm demo' }]);
    if (url.pathname === '/api/catalog/products/names') return Response.json([{ id: PRODUCT, name: 'Áo demo' }]);
    if (url.pathname === `/api/catalog/products/${PRODUCT}`) return Response.json({ available: 10 });
    throw new Error(`Unexpected ${options?.method} ${url.pathname}`);
  };
  const host = document.createElement('div');
  document.body.append(host);
  const root = createRoot(host);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  try {
    await act(async () => root.render(createElement(QueryClientProvider, { client }, createElement(CartPage))));
    await settle(() => host.querySelector(`#qty-${PRODUCT}`));
    const input = host.querySelector(`#qty-${PRODUCT}`);
    assert.ok(input, 'quantity field');
    await act(async () => {
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(input, '5');
      input.dispatchEvent(new window.Event('input', { bubbles: true }));
    });
    await act(async () => { input.focus(); input.blur(); });
    await settle(() => host.querySelector('[role="alert"]'));
    assert.deepEqual(patches, [5]);
    assert.equal(input.value, '5');
    await act(async () => input.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true })));
    await settle(() => patches.length > 1);
    assert.deepEqual(patches, [5, 5]);
  } finally {
    await act(async () => root.unmount());
    client.clear();
    globalThis.fetch = originalFetch;
    host.remove();
  }
});
