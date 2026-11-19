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
      const row = host.querySelector('article li');
      const status = row.querySelector('span.badge--pending');
      const withdraw = row.querySelector('button.btn--danger');
      assert.equal(status?.textContent, 'Chờ duyệt');
      assert.equal(withdraw?.textContent.trim(), 'Rút đăng ký');
      assert.equal(withdraw?.type, 'button');
      assert.notEqual(status?.parentElement, withdraw?.parentElement);
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
      return path.endsWith('/products') ? [registration] : [
        { ...campaign, kind: 'SELLER_CLEARANCE', status: 'DRAFT', submittedAt: null },
      ];
    }, host => {
      const option = host.querySelector('select[name="productId"] option[value="product-1"]');
      assert.match(option?.textContent ?? '', /Áo demo/);
      assert.equal(host.querySelector('input[name="productId"]'), null);
      const create = host.querySelector('form[aria-label="Tạo chương trình khuyến mãi"] button.btn--primary');
      const register = option?.closest('form').querySelector('button.btn--primary');
      const submit = [...host.querySelectorAll('article button.btn--primary')].find(
        button => button.textContent === 'Gửi duyệt xả kho');
      assert.equal(create?.textContent, 'Tạo chương trình');
      assert.equal(create?.type, 'submit');
      assert.equal(register?.textContent, 'Đăng ký sản phẩm');
      assert.equal(register?.type, 'submit');
      assert.equal(submit?.type, 'button');
    });
  } finally {
    delete globalThis.promotionTestSeller;
  }
});

test('Admin can pause and approve an offer and needs a rejection reason', async () => {
  await render(pages.AdminPromotionsPage, path => path.endsWith('/products') ? [registration] : [campaign],
    host => {
      assert.match(host.textContent, /Tạm dừng/);
      const row = host.querySelector('article li');
      assert.equal(row.querySelector('strong').textContent, 'Áo demo');
      assert.equal(row.querySelector('.badge--pending').textContent, 'Chờ duyệt');
      assert.match(row.querySelector('p').textContent, /Tiệm demo.*Giá gốc.*Giá sale/);
      assert.match(host.textContent, /Sắp diễn ra/);
      const buttons = [...host.querySelectorAll('button')];
      assert.equal(buttons.find(button => button.textContent === 'Duyệt sản phẩm')?.disabled, false);
      assert.equal(buttons.find(button => button.textContent === 'Từ chối sản phẩm')?.disabled, true);
    });
});

test('Admin and Seller see the same approval deadline without changing decided registrations', async () => {
  const expired = {
    ...campaign, kind: 'SELLER_CLEARANCE', startsAt: '2020-01-01T15:00:00Z',
    status: 'DRAFT', submittedAt: '2019-12-31T15:00:00Z',
  };
  const withdraw = { ...registration, productId: 'product-2', productName: 'Đèn demo', status: 'WITHDRAWN' };
  for (const Page of [pages.AdminPromotionsPage, pages.PromotionsPage]) {
    await render(Page, path => path.endsWith('/products') ? [registration, withdraw] : [expired], host => {
      const card = host.querySelector('article');
      assert.match(card.querySelector('p').textContent, /Xả kho · Đã quá hạn duyệt/);
      assert.match(card.querySelector('.note--error').textContent, /qua giờ bắt đầu khi còn chờ duyệt/);
      const badges = [...card.querySelectorAll('li .badge')].map(node => node.textContent);
      assert.deepEqual(badges, ['Đã quá hạn duyệt', 'Đã rút']);
      assert.doesNotMatch(card.textContent, /Duyệt chương trình|Duyệt sản phẩm|Lý do từ chối/);
    });
  }
});

test('Admin sees why a clearance draft and its products cannot be approved before Seller submits', async () => {
  const unsubmitted = { ...campaign, kind: 'SELLER_CLEARANCE', status: 'DRAFT', submittedAt: null };
  await render(pages.AdminPromotionsPage, path => path.endsWith('/products') ? [registration] : [unsubmitted], host => {
    const card = host.querySelector('article');
    assert.match(card.querySelector('.note').textContent, /Seller cần bấm Gửi duyệt xả kho/);
    assert.doesNotMatch(card.textContent, /Đã quá hạn duyệt|Duyệt chương trình|Duyệt sản phẩm/);
    assert.match(card.textContent, /Từ chối sản phẩm/);
  });
  await render(pages.AdminPromotionsPage, path => path.endsWith('/products') ? [registration] : [
    { ...unsubmitted, submittedAt: '2020-01-01T00:00:00Z' },
  ], host => {
    const card = host.querySelector('article');
    assert.match(card.textContent, /Chờ duyệt chương trình/);
    assert.match(card.textContent, /Duyệt chương trình/);
    assert.doesNotMatch(card.textContent, /Duyệt sản phẩm/);
  });
});
