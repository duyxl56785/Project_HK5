import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let server, CartView;
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  ({ CartView } = await server.ssrLoadModule('/src/features/cart/CartView.tsx'));
});
after(async () => { await server?.close(); });

const snapshot = {
  revision: '9007199254740993', claimed: false, items: [
    { productId: 'p1', storeId: 'store-A', sellerId: 'seller-A', title: 'Áo thun', quantity: 2, unitPriceSnapshot: '10000.00' },
    { productId: 'p2', storeId: 'store-B', sellerId: 'seller-B', title: 'Sách', quantity: 1, unitPriceSnapshot: '15000.00' },
  ],
};
const view = (state, actions = {}) => renderToStaticMarkup(createElement(CartView, { state, ...actions }));

test('two Store groups show exact total, revision and accessible quantity labels', () => {
  const html = view({ status: 'ready', snapshot }, { onQuantityChange() {}, onRemove() {} });
  assert.match(html, /store-A/); assert.match(html, /store-B/);
  assert.match(html, /35\.000,00/); assert.match(html, /9007199254740993/);
  assert.match(html, /Số lượng/); assert.match(html, /Xóa/);
  assert.doesNotMatch(html, /Checkout thành công|Thanh toán thành công/);
});
test('Cart claim prevents edit and remove even when callbacks were passed', () => {
  const html = view({ status: 'ready', snapshot: { ...snapshot, claimed: true } },
    { onQuantityChange() {}, onRemove() {} });
  assert.match(html, /Giỏ hàng đang được giữ/);
  assert.match(html, /disabled=""/);
});
test('unconfigured actions are disabled rather than pretending to call backend', () => {
  const html = view({ status: 'ready', snapshot });
  assert.match(html, /disabled=""/);
});
test('invalid snapshot renders no stale partial store items', () => {
  const invalid = { ...snapshot, items: [{ ...snapshot.items[0] }, { ...snapshot.items[1], sellerId: 'seller-A' }] };
  const html = view({ status: 'ready', snapshot: invalid });
  assert.match(html, /Dữ liệu giỏ hàng không nhất quán/);
  assert.doesNotMatch(html, /Áo thun|Sách|35\.000,00/);
});
test('non-ready states never render stale Cart data or private source fields', () => {
  for (const status of ['loading', 'empty', 'unauthorized', 'unavailable', 'error']) {
    const html = view({ status, snapshot: { ...snapshot, token: 'PRIVATE_TOKEN' } });
    assert.doesNotMatch(html, /Áo thun|Sách|PRIVATE_TOKEN|35\.000,00/);
  }
});
