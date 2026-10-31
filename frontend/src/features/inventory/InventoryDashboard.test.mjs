import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let server, InventoryDashboard;
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  ({ InventoryDashboard } = await server.ssrLoadModule('/src/features/inventory/InventoryDashboard.tsx'));
});
after(async () => { await server?.close(); });

const ready = { status: 'ready', items: [
  { sku: 'SKU-001', name: 'Áo sơ mi', availableQuantity: '9223372036854775807', reservedQuantity: '2' },
  { sku: 'SKU-002', name: 'Túi vải', availableQuantity: '0', reservedQuantity: '3' },
] };
const render = (state) => renderToStaticMarkup(createElement(InventoryDashboard, { state }));

test('renders 64-bit quantities byte-for-byte and marks out-of-stock rows', () => {
  const html = render(ready);
  assert.match(html, /9223372036854775807/);
  assert.match(html, /Hết hàng/);
  assert.match(html, /Còn hàng/);
  assert.doesNotMatch(html, /9223372036854776000/);
});
for (const [status, label] of [['loading', 'Đang tải tồn kho'], ['empty', 'Chưa có mặt hàng']]) {
  test(`renders ${status} without stale rows`, () => {
    const html = render({ status, items: ready.items });
    assert.match(html, new RegExp(label));
    assert.doesNotMatch(html, /SKU-001|Áo sơ mi/);
  });
}
test('error state exposes retry and does not render stale inventory', () => {
  const html = render({ status: 'error', items: ready.items, onRetry() {} });
  assert.match(html, /role="alert"/);
  assert.match(html, /<button[^>]*>Thử lại<\/button>/);
  assert.doesNotMatch(html, /SKU-001|Áo sơ mi/);
});
test('invalid or duplicated API data fails closed', () => {
  for (const items of [[{ ...ready.items[0], availableQuantity: '-1' }], [{ ...ready.items[0] }, { ...ready.items[0] }]]) {
    const html = render({ status: 'ready', items });
    assert.match(html, /Dữ liệu tồn kho không hợp lệ/);
    assert.doesNotMatch(html, /SKU-001.*SKU-001/);
  }
});
