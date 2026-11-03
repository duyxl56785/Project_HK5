import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let server, SellerOperationsDashboard;
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  ({ SellerOperationsDashboard } = await server.ssrLoadModule('/src/features/seller-operations/SellerOperationsDashboard.tsx'));
});
after(async () => { await server?.close(); });

const state = {
  inventory: { status: 'empty' },
  fulfillment: { status: 'empty' },
};
const render = (props) => renderToStaticMarkup(createElement(SellerOperationsDashboard, props));

test('defaults to the Inventory view with accessible tab semantics', () => {
  const html = render({ state });
  assert.match(html, /role="tablist"/);
  assert.match(html, /Tồn kho/);
  assert.match(html, /aria-selected="true"/);
  assert.match(html, /Chưa có mặt hàng/);
  assert.doesNotMatch(html, /SellerOrder cần xử lý/);
});
test('can render the SellerOrder view without inventing order data', () => {
  const html = render({ state, initialView: 'orders' });
  assert.match(html, /Đơn hàng/);
  assert.match(html, /SellerOrder cần xử lý/);
  assert.doesNotMatch(html, /Chưa có mặt hàng/);
});
