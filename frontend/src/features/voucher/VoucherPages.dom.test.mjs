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
      name: 'voucher-test-session',
      enforce: 'pre',
      resolveId(id) { if (id === '../../shared/auth') return '\0voucher-test-session'; },
      load(id) {
        if (id === '\0voucher-test-session') {
          return "export const useAuth = () => ({ account: { userId: 'owner' }, has: () => false });";
        }
      },
    }],
    optimizeDeps: { noDiscovery: true } });
  pages = await vite.ssrLoadModule('/src/features/voucher/VoucherPages.tsx');
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
    for (let i = 0; i < 100 && !host.querySelector('article'); i++) {
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

test('retraction view shows the owner voucher and offers only pause and cancel', async () => {
  await render(pages.SellerVouchersPage, () => [{
    id: 'voucher-1', code: 'TEST01', name: 'Ưu đãi', discountType: 'FIXED', discountValue: 100,
    maxDiscount: null, minOrderAmount: 200, usageLimit: 10, startsAt: '2026-11-14T01:00:00Z',
    endsAt: '2026-11-15T01:00:00Z', status: 'ACTIVE', version: '1',
  }], (host, paths) => {
    assert.match(host.textContent, /TEST01.*Đang hoạt động/);
    assert.deepEqual([...host.querySelectorAll('button')].map(button => button.textContent), ['Tạm dừng', 'Hủy']);
    assert.deepEqual(paths, [['/api/stores/me/vouchers?size=50', 'GET']]);
  });
});

test('Admin sees the pending request and needs a reason before rejecting', async () => {
  await render(pages.AdminVoucherLimitsPage, () => [{
    id: 'request-1', storeId: 'store-12345678', requestedLimit: 30, reason: 'Mở rộng chương trình',
    requestedFrom: '2026-11-14T01:00:00Z', requestedUntil: '2026-11-15T01:00:00Z', version: '0',
  }], (host, paths) => {
    assert.match(host.textContent, /Xin 30 slot/);
    const [approve, reject] = host.querySelectorAll('article button');
    assert.equal(approve.textContent, 'Duyệt');
    assert.equal(approve.disabled, false);
    assert.equal(reject.textContent, 'Từ chối');
    assert.equal(reject.disabled, true);
    assert.deepEqual(paths, [['/api/admin/voucher-limit-requests?status=PENDING&size=50', 'GET']]);
  });
});
