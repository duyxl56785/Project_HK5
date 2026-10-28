import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';
let server, WorkspaceView;
before(async () => {
  server = await createServer({ optimizeDeps: { noDiscovery: true, include: [] }, server: { middlewareMode: true, hmr: false, watch: null } });
  ({ WorkspaceView } = await server.ssrLoadModule('/src/features/finance/WorkspaceView.tsx'));
});
after(async () => { await server?.close(); });
const row = (id, storeId) => ({ id, orderId: `order-${id}`, storeId, store: `Store ${storeId}`, seller: `Seller ${storeId}`, amount: '9007199254740993', currency: 'VND',
  delivery: 'DELIVERED', cod: 'PAID', verification: 'UNVERIFIED', paymentEvidence: null, commission: null, sellerNet: null,
  settlement: null, createdAt: '2026-10-27T00:00:00Z', updatedAt: '2026-10-27T01:00:00Z' });
const data = { source: 'DEMO', asOf: '2026-10-28T00:00:00Z', transactions: [row('public-A', 'A'), row('FOREIGN_SECRET', 'B')], qaRuns: [], audit: { status: 'blocked', rows: [], asOf: null } };
const render = (state, props = {}) => renderToStaticMarkup(createElement(WorkspaceView, { state, scope: { kind: 'admin' }, ...props }));
test('Finance renders exact amounts, transaction columns and explicit DEMO boundary', () => {
  const html = render({ status: 'ready', data });
  assert.match(html, /Tổng quan tài chính/); assert.match(html, /DEMO DATA/);
  assert.match(html, /9\.007\.199\.254\.740\.993/); assert.match(html, /SellerOrder/);
  assert.match(html, /Chưa có snapshot/);
});
for (const [status, label] of [['loading', 'Đang tải dữ liệu'], ['empty', 'Chưa có dữ liệu'], ['error', 'Không thể tải dữ liệu'], ['denied', 'Không có quyền truy cập'], ['blocked', 'Chưa kết nối API']]) {
  test(`${status} conceals any stale/attached private rows`, () => {
    const html = render({ status, data, message: 'PRIVATE_DB_TRACE' });
    assert.match(html, new RegExp(label));
    assert.doesNotMatch(html, /public-A|FOREIGN_SECRET|PRIVATE_DB_TRACE|9\.007/);
    if (status === 'loading') assert.match(html, /aria-busy="true"/);
  });
}
test('Store scope excludes foreign amounts, options, details and internal QA/PR tabs', () => {
  const html = render({ status: 'ready', data }, { scope: { kind: 'store', storeId: 'A' } });
  assert.match(html, /public-A/); assert.doesNotMatch(html, /FOREIGN_SECRET|Store B|Kiểm thử QA|Pull Requests/);
});
test('denied scope takes precedence over ready data', () => {
  const html = render({ status: 'ready', data }, { scope: { kind: 'denied' } });
  assert.match(html, /Không có quyền truy cập/); assert.doesNotMatch(html, /public-A|FOREIGN_SECRET/);
});
test('all panels omit financial mutation controls', () => {
  for (const initialView of ['finance', 'cod', 'settlement', 'qa', 'prs']) {
    const html = render({ status: 'ready', data }, { initialView });
    assert.doesNotMatch(html, /<button[^>]*>(?:Create Settlement|Approve Settlement|Release Payment|Refund|Xác nhận COD|Quyết toán)/);
  }
});
test('unknown COD is a display observation, never a new persisted workflow state', () => {
  const html = render({ status: 'ready', data: { ...data, transactions: [{ ...row('unknown', 'A'), cod: null, verification: 'UNKNOWN' }] } }, { initialView: 'cod' });
  assert.match(html, /UNKNOWN/); assert.match(html, /Chưa có thông tin/);
});
test('React escapes source labels and never serializes extra private fields', () => {
  const html = render({ status: 'ready', data: { ...data, transactions: [{ ...row('<img onerror=alert(1)>', 'A'), password: 'PRIVATE_PASSWORD', token: 'PRIVATE_TOKEN' }] } });
  assert.match(html, /&lt;img/); assert.doesNotMatch(html, /<img|PRIVATE_PASSWORD|PRIVATE_TOKEN/);
});
