import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let server, SellerStoreOverview;
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  ({ SellerStoreOverview } = await server.ssrLoadModule('/src/features/seller/SellerStoreOverview.tsx'));
});
after(async () => { await server?.close(); });
const seller = { id: '11111111-1111-4111-8111-111111111111', status: 'APPROVED', version: '9007199254740993' };
const store = { id: '22222222-2222-4222-8222-222222222222', sellerId: seller.id, name: 'Cửa hàng Việt', slug: 'cua-hang-viet', open: true, version: '9223372036854775807' };
const ready = { status: 'ready', seller, store };
const render = (state) => renderToStaticMarkup(createElement(SellerStoreOverview, { state }));

for (const [status, label] of [['PENDING_REVIEW', 'Chờ xét duyệt'], ['APPROVED', 'Đã duyệt'], ['REJECTED', 'Bị từ chối'], ['SUSPENDED', 'Tạm ngừng']]) {
  test(`renders actual Seller status ${status} as Vietnamese text`, () => assert.match(render({ ...ready, seller: { ...seller, status } }), new RegExp(label)));
}
for (const [open, label] of [[true, 'Đang mở cửa'], [false, 'Đang đóng cửa']]) {
  test(`renders persisted Store open=${open} without granting selling permission`, () => {
    const html = render({ ...ready, store: { ...store, open } });
    assert.match(html, new RegExp(label));
    assert.match(html, /Cửa hàng Việt/);
    assert.match(html, /cua-hang-viet/);
    assert.doesNotMatch(html, /Được phép bán|Có thể thanh toán/);
  });
}
for (const [status, label] of [['loading', 'Đang tải hồ sơ'], ['empty', 'Chưa có hồ sơ người bán'], ['unauthorized', 'Không có quyền xem hồ sơ'], ['unavailable', 'Chức năng chưa sẵn sàng']]) {
  test(`${status} has a distinct state and conceals any attached stale data`, () => {
    const html = render({ ...ready, status, reviewReason: 'PRIVATE_REASON' });
    assert.match(html, new RegExp(label));
    assert.doesNotMatch(html, /Cửa hàng Việt|cua-hang-viet|9007199254740993|PRIVATE_REASON/);
    if (status === 'loading') assert.match(html, /aria-busy="true"/);
  });
}
test('error exposes retry, not stale data or backend diagnostic text', () => {
  const html = render({ ...ready, status: 'error', onRetry() {}, message: 'PRIVATE_REASON' });
  assert.match(html, /role="alert"/);
  assert.match(html, /<button[^>]*>Thử lại<\/button>/);
  assert.doesNotMatch(html, /Cửa hàng Việt|PRIVATE_REASON/);
});
test('explicit no-Store state does not invent a Store or create action', () => {
  const html = render({ ...ready, store: null });
  assert.match(html, /Chưa có cửa hàng/);
  assert.doesNotMatch(html, /cua-hang-viet|Tạo cửa hàng thành công/);
});
test('invalid or cross-Seller source fails without partial private information', () => {
  for (const state of [{ ...ready, store: undefined }, { ...ready, seller: { ...seller, version: 9007199254740993 } },
    { ...ready, store: { ...store, sellerId: '33333333-3333-4333-8333-333333333333' } }]) {
    const html = render(state);
    assert.match(html, /Dữ liệu hồ sơ không nhất quán/);
    assert.doesNotMatch(html, /Cửa hàng Việt|cua-hang-viet|9007199254740993/);
  }
});
test('ready display never leaks private reason, owner, role or token extras', () => {
  const html = render({ ...ready, seller: { ...seller, reviewReason: 'PRIVATE_REASON', ownerReference: 'PRIVATE_OWNER', role: 'ADMIN' }, store: { ...store, token: 'PRIVATE_TOKEN' } });
  assert.doesNotMatch(html, /PRIVATE_REASON|PRIVATE_OWNER|PRIVATE_TOKEN|ADMIN/);
});
test('versions are displayed byte-for-byte beyond Number.MAX_SAFE_INTEGER', () => {
  const html = render(ready);
  assert.match(html, /9007199254740993/);
  assert.match(html, /9223372036854775807/);
  assert.doesNotMatch(html, /9007199254740992|9223372036854776000/);
});
test('display names are escaped instead of interpreted as markup', () => {
  const html = render({ ...ready, store: { ...store, name: '<img src=x onerror=alert(1)>' } });
  assert.match(html, /&lt;img/);
  assert.doesNotMatch(html, /<img/);
});
test('multiple instances have unique labelled sections', () => {
  const html = renderToStaticMarkup(createElement('div', null, createElement(SellerStoreOverview, { state: ready }), createElement(SellerStoreOverview, { state: ready })));
  const labels = [...html.matchAll(/aria-labelledby="([^"]+)"/g)].map((match) => match[1]);
  assert.ok(labels.length >= 6);
  assert.equal(new Set(labels).size, labels.length);
  for (const id of labels) assert.ok(html.includes(`id="${id}"`));
});
