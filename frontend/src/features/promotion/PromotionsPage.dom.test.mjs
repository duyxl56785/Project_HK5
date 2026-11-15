import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { act, createElement } from 'react';
import { Window } from 'happy-dom';
import { createServer } from 'vite';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

let window, vite, createRoot, pages;
before(async () => {
  window = new Window({ url: 'http://localhost/' });
  globalThis.window = window;
  globalThis.document = window.document;
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  vite = await createServer({ server: { middlewareMode: true, hmr: false, watch: null },
    plugins: [{
      name: 'promotion-test-session',
      enforce: 'pre',
      resolveId(id) { if (id === '../../shared/auth') return '\0promotion-test-session'; },
      load(id) {
        if (id === '\0promotion-test-session') {
          return "export const useAuth = () => ({ account: { userId: 'owner' }, has: () => globalThis.promotionTestSeller === true });";
        }
      },
    }],
    optimizeDeps: { noDiscovery: true } });
  pages = await vite.ssrLoadModule('/src/features/promotion/PromotionsPage.tsx');
});
after(async () => {
  await vite?.close();
  await window?.happyDOM.abort();
});

async function render(Page, respond, verify) {
  const originalFetch = globalThis.fetch;
  const paths = [];
  globalThis.fetch = async (path, options) => {
    paths.push([String(path), options?.method ?? 'GET']);
    return Response.json(respond(String(path)));
  };
  const host = document.createElement('div');
  document.body.append(host);
  const root = createRoot(host);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  try {
    await act(async () => root.render(createElement(QueryClientProvider, { client },
      createElement(Page))));
    for (let i = 0; i < 100 && (!host.querySelector('article li') || (globalThis.promotionTestSeller && !host.querySelector('option[value="product-1"]'))); i++) {
      await act(async () => new Promise(resolve => setTimeout(resolve, 20)));
    }
    verify(host, paths);
  } finally {
    await act(async () => root.unmount());
    client.clear();
    globalThis.fetch = originalFetch;
    host.remove();
  }
}

const campaign = {
  id: 'campaign-1', name: 'Demo Sale', kind: 'FLASH_SALE', status: 'APPROVED',
  startsAt: '2090-01-01T15:00:00Z', endsAt: '2090-01-01T17:00:00Z', version: 0,
};
const registration = {
  productId: 'product-1', productName: 'Áo demo', storeName: 'Tiệm demo',
  status: 'PENDING', baseUnitPrice: 2000, promotionalUnitPrice: 1000, version: 0,
};

test('Seller without role may withdraw but cannot create or register', async () => {
  await render(pages.PromotionsPage, path => path.endsWith('/products') ? [registration] : [campaign],
    host => {
      assert.match(host.textContent, /Rút đăng ký/);
      assert.doesNotMatch(host.textContent, /Tạo chương trình|Đăng ký sản phẩm/);
    });
});

test('Seller selects a Product by name from their active Product list', async () => {
  globalThis.promotionTestSeller = true;
  try {
    await render(pages.PromotionsPage, path => {
      if (path.startsWith('/api/seller/products?')) {
        const query = new URL(path, 'http://localhost').searchParams;
        assert.equal(query.get('status'), 'ACTIVE');
        return { products: [{ id:'product-1', name:'Áo demo', unitPrice:'2000' }] };
      }
      return path.endsWith('/products') ? [registration] : [campaign];
    }, host => {
      const option = host.querySelector('select[name="productId"] option[value="product-1"]');
      assert.match(option?.textContent ?? '', /Áo demo/);
      assert.equal(host.querySelector('input[name="productId"]'), null);
    });
  } finally {
    delete globalThis.promotionTestSeller;
  }
});

test('Admin can pause and approve an offer and needs a rejection reason', async () => {
  await render(pages.AdminPromotionsPage, path => path.endsWith('/products') ? [registration] : [campaign],
    host => {
      assert.match(host.textContent, /Tạm dừng/);
      assert.match(host.textContent, /Áo demo.*Tiệm demo.*Giá gốc.*Giá sale.*Chờ duyệt/);
      assert.match(host.textContent, /Sắp diễn ra/);
      const buttons = [...host.querySelectorAll('button')];
      assert.equal(buttons.find(button => button.textContent === 'Duyệt sản phẩm')?.disabled, false);
      assert.equal(buttons.find(button => button.textContent === 'Từ chối sản phẩm')?.disabled, true);
    });
});