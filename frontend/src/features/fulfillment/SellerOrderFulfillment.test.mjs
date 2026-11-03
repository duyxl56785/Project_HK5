import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let server, SellerOrderFulfillment;
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  ({ SellerOrderFulfillment } = await server.ssrLoadModule('/src/features/fulfillment/SellerOrderFulfillment.tsx'));
});
after(async () => { await server?.close(); });

const order = { id: 'seller-order-1', storeId: 'store-a', status: 'PLACED', totalLabel: '450.000 đ', codStatus: 'UNPAID' };
const render = (state) => renderToStaticMarkup(createElement(SellerOrderFulfillment, { state }));

test('shows only Store-owned order and actions valid for current state', () => {
  const html = render({ status: 'ready', storeId: 'store-a', orders: [order] });
  assert.match(html, /seller-order-1/);
  assert.match(html, /Xác nhận/);
  assert.match(html, /Từ chối/);
  assert.doesNotMatch(html, /Bắt đầu chuẩn bị|Bàn giao/);
});
test('does not disclose cross-Store orders when the source is inconsistent', () => {
  const html = render({ status: 'ready', storeId: 'store-a', orders: [{ ...order, storeId: 'store-b' }] });
  assert.match(html, /Dữ liệu đơn không hợp lệ/);
  assert.doesNotMatch(html, /seller-order-1/);
});
test('terminal orders have no transition controls', () => {
  const html = render({ status: 'ready', storeId: 'store-a', orders: [{ ...order, status: 'DELIVERED' }] });
  assert.match(html, /Không còn thao tác fulfillment hợp lệ/);
  assert.doesNotMatch(html, /<button/);
});
test('pending action disables all buttons for the same order', () => {
  const html = render({ status: 'ready', storeId: 'store-a', orders: [order], pending: { orderId: order.id, action: 'ACCEPT' } });
  assert.match(html, /Đang xử lý…/);
  assert.match(html, /disabled/);
});
test('unauthorized state never renders stale order data', () => {
  const html = render({ status: 'unauthorized', storeId: 'store-a', orders: [order] });
  assert.match(html, /Không có quyền xem đơn/);
  assert.doesNotMatch(html, /seller-order-1|450\.000/);
});
