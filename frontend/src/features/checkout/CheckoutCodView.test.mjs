import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let server, CheckoutCodView;
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  ({ CheckoutCodView } = await server.ssrLoadModule('/src/features/checkout/CheckoutCodView.tsx'));
});
after(async () => { await server?.close(); });

const cart = { revision: '9007199254740993', claimed: false, items: [
  { productId: 'p1', storeId: 's1', sellerId: 'seller1', title: 'Áo', quantity: 2, unitPriceSnapshot: '10000.00' },
  { productId: 'p2', storeId: 's2', sellerId: 'seller2', title: 'Sách', quantity: 1, unitPriceSnapshot: '15000.00' },
] };
const render = (state, onConfirm) => renderToStaticMarkup(createElement(CheckoutCodView, { state, onConfirm }));

test('COD preview shows two Store groups, exact total and no assumed monetary policy', () => {
  const html = render({ status: 'ready', cart, idempotencyKey: 'fixed-key' });
  assert.match(html, /Thanh toán khi nhận hàng/);
  assert.match(html, /Cửa hàng: s1/); assert.match(html, /Cửa hàng: s2/);
  assert.match(html, /35\.000,00/);
  assert.match(html, /disabled=""/);
  assert.doesNotMatch(html, /Đã xác nhận đơn hàng|COD_PAID|VNPay/);
});
test('only an explicitly connected handler enables submit button; SSR makes no network call', () => {
  const html = render({ status: 'ready', cart, idempotencyKey: 'fixed-key' }, async () => ({ status: 'unknown' }));
  assert.match(html, /Xác nhận đặt hàng COD/);
  assert.doesNotMatch(html, /<button[^>]*disabled=/);
});
test('Claimed cart or malformed key cannot trigger checkout', () => {
  for (const state of [
    { status: 'ready', cart: { ...cart, claimed: true }, idempotencyKey: 'fixed-key' },
    { status: 'ready', cart, idempotencyKey: ' fixed-key' },
    { status: 'ready', cart: { ...cart, items: [] }, idempotencyKey: 'fixed-key' },
  ]) {
    assert.match(render(state, async () => ({ status: 'confirmed' })), /disabled=""/);
  }
});
test('UNKNOWN/reconciliation is prominent and cannot display a confirmed order', () => {
  const html = render({ status: 'unknown', cart, idempotencyKey: 'fixed-key' });
  assert.match(html, /Chưa xác định kết quả đặt hàng/);
  assert.doesNotMatch(html, /Backend đã xác nhận|s1|s2|fixed-key/);
});
