import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let server; let FulfillmentBoard;
before(async () => { server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } }); ({ FulfillmentBoard } = await server.ssrLoadModule('/src/features/fulfillment/FulfillmentBoard.tsx')); });
after(async () => { await server?.close(); });

const storeId = '11111111-1111-4111-8111-111111111111';
const order = { id: '22222222-2222-4222-8222-222222222222', parentOrderId: '33333333-3333-4333-8333-333333333333', storeId, status: 'READY_FOR_HANDOFF', codStatus: 'UNRECORDED', createdAt: '2026-10-28T10:00:00Z', actions: [{ id: 'DISPATCH', label: 'Xác nhận bàn giao' }] };
const render = (state) => renderToStaticMarkup(createElement(FulfillmentBoard, { state }));

test('renders host-provided order state and command without claiming COD is paid', () => {
  const html = render({ status: 'ready', storeId, orders: [order], onAction() {} });
  assert.match(html, /Sẵn sàng bàn giao/);
  assert.match(html, /COD chưa được ghi nhận/);
  assert.match(html, /Xác nhận bàn giao/);
  assert.doesNotMatch(html, /COD đã được ghi nhận/);
});

test('does not render invalid or cross-Store order information', () => {
  const html = render({ status: 'ready', storeId, orders: [{ ...order, storeId: '44444444-4444-4444-8444-444444444444' }] });
  assert.match(html, /Dữ liệu đơn hàng không nhất quán/);
  assert.doesNotMatch(html, /22222222-2222-4222-8222-222222222222|Xác nhận bàn giao/);
});

test('keeps stale ready data hidden in unauthorized and error states', () => {
  assert.doesNotMatch(render({ status: 'unauthorized', storeId, orders: [order] }), /22222222-2222-4222-8222-222222222222/);
  const html = render({ status: 'error', onRetry() {}, storeId, orders: [order] });
  assert.match(html, /Thử lại/);
  assert.doesNotMatch(html, /22222222-2222-4222-8222-222222222222/);
});

test('disables every command while the host is processing that SellerOrder', () => {
  const html = render({
    status: 'ready', storeId,
    pendingOrderId: order.id,
    orders: [{ ...order, actions: [
      { id: 'DISPATCH', label: 'Xác nhận bàn giao' },
      { id: 'RELEASE', label: 'Trả tồn kho', tone: 'secondary' },
    ] }],
    onAction() {},
  });
  assert.equal((html.match(/disabled=""/g) ?? []).length, 2);
  assert.equal((html.match(/Đang gửi yêu cầu…/g) ?? []).length, 2);
  assert.match(html, /aria-busy="true"/);
});
