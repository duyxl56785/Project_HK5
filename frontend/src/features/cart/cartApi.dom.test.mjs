import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Window } from 'happy-dom';
import { act, createElement } from 'react';
import { createServer } from 'vite';

let window, vite, createRoot, useCart, CartView;
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
    plugins: [{
      name: 'cart-test-customer',
      enforce: 'pre',
      resolveId(id) { if (id === '../../shared/auth') return '\0cart-test-customer'; },
      load(id) {
        if (id === '\0cart-test-customer') return "export const useAuth = () => ({ has: (role) => role === 'CUSTOMER' });";
      },
    }],
  });
  ({ useCart } = await vite.ssrLoadModule('/src/features/cart/cartApi.ts'));
  ({ CartView } = await vite.ssrLoadModule('/src/features/cart/CartView.tsx'));
});
after(async () => {
  await vite?.close();
  await window?.happyDOM.abort();
});

function CartWithData() {
  const { data } = useCart();
  return data && createElement(CartView, {
    state: { status: 'ready', snapshot: data.snapshot },
    storeNames: data.storeNames,
  });
}

test('Cart with over 100 products resolves names for both stores', async () => {
  const items = Array.from({ length: 101 }, (_, index) => ({
    productId: `00000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
    storeId: index < 100 ? 'store-1' : 'store-2',
    sellerId: index < 100 ? 'seller-1' : 'seller-2',
    quantity: 1, unitPriceSnapshot: 1000,
  }));
  const names = new Map(items.map(({ productId }, index) => [productId, `Sản phẩm demo ${index + 1}`]));
  const storeNames = [{ id: 'store-1', name: 'Tiệm demo A' }, { id: 'store-2', name: 'Tiệm demo B' }];
  const batches = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (path, options) => {
    assert.equal(options?.method, 'GET');
    const url = new URL(path, 'http://localhost');
    if (url.pathname === '/api/cart') return Response.json({ revision: 7, claimed: false, items });
    if (url.pathname === '/api/stores') return Response.json(storeNames);
    assert.equal(url.pathname, '/api/catalog/products/names');
    const ids = url.searchParams.get('ids').split(',');
    batches.push(ids);
    // Match the Catalog endpoint's batch limit.
    if (ids.length > 100) {
      return Response.json({ code: 'VALIDATION_FAILED', message: 'At most 100 ids' }, { status: 400 });
    }
    return Response.json(ids.map(id => ({ id, name: names.get(id) })));
  };
  const host = document.createElement('div');
  document.body.append(host);
  const root = createRoot(host);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  try {
    await act(async () => root.render(createElement(QueryClientProvider, { client }, createElement(CartWithData))));
    for (let i = 0; i < 100; i++) {
      if (client.getQueryState(['cart'])?.status === 'error' || host.querySelector('article.tv3-cart__store')) break;
      await act(async () => new Promise(resolve => setTimeout(resolve, 20)));
    }
    const state = client.getQueryState(['cart']);
    assert.equal(state?.status, 'success', state?.error?.message);
    assert.deepEqual(batches.map(ids => ids.length), [100, 1]);
    assert.deepEqual(batches.flat(), [...names.keys()]);
    assert.equal(host.querySelectorAll('article.tv3-cart__store').length, 2);
    assert.match(host.textContent, /Sản phẩm demo 101/);
    assert.match(host.textContent, /101\.000 ₫/);
  } finally {
    await act(async () => root.unmount());
    client.clear();
    globalThis.fetch = originalFetch;
    host.remove();
  }
});
