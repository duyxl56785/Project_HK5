import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';
import { ready, product } from './productTestFixtures.mjs';
let server, ProductManagementPage, ProductForm;
before(async () => {
  server = await createServer({ optimizeDeps: { noDiscovery: true, entries: [] }, server: { middlewareMode: true, hmr: { port: 0 }, watch: null } });
  ({ ProductManagementPage } = await server.ssrLoadModule('/src/features/product/ProductManagementPage.tsx'));
  ({ ProductForm } = await server.ssrLoadModule('/src/features/product/ProductForm.tsx'));
});
after(async () => { await server?.close(); });
const render = (state = ready, extra = {}) => renderToStaticMarkup(createElement(ProductManagementPage, { state, ...extra }));
for (const [status, label] of [['DRAFT', 'Nháp'], ['PENDING_REVIEW', 'Chờ duyệt'], ['ACTIVE', 'Đang bán'], ['REJECTED', 'Bị từ chối'], ['SUSPENDED', 'Tạm khóa'], ['INACTIVE', 'Ngừng bán']]) {
  test(`renders real ${status} state`, () => assert.ok(render({ ...ready, products: [{ ...product, status }] }).includes(label)));
}
for (const [status, label] of [['loading', 'Đang tải sản phẩm'], ['unauthorized', 'Không có quyền truy cập'], ['unavailable', 'Chức năng chưa sẵn sàng'], ['error', 'Không tải được sản phẩm']]) {
  test(`${status} conceals stale data and diagnostics`, () => {
    const html = render({ ...ready, status, message: 'SECRET_DIAGNOSTIC' });
    assert.ok(html.includes(label));
    assert.doesNotMatch(html, /Gốm Việt|GOM-MAY|SECRET_DIAGNOSTIC/);
  });
}
test('Store-scoped empty list is actionable without inventing products', () => {
  const html = render({ ...ready, products: [] });
  assert.match(html, /Chưa có sản phẩm|Thêm sản phẩm/);
  assert.doesNotMatch(html, /GOM-MAY/);
});
test('invalid or cross-Store source never discloses a partial list', () => {
  const html = render({ ...ready, products: [product, { ...product, id: ready.categories[0].id, storeId: ready.categories[0].id }] });
  assert.match(html, /Dữ liệu sản phẩm không nhất quán/);
  assert.doesNotMatch(html, /Gốm Việt|GOM-MAY/);
});
test('safe output escapes markup, omits private fields and has no approve control', () => {
  const html = render({ ...ready, products: [{ ...product, name: '<img src=x onerror=alert(1)>', ownerReference: 'PRIVATE_OWNER', reviewReason: 'PRIVATE_REASON' }] });
  assert.match(html, /&lt;img/);
  assert.doesNotMatch(html, /<img|PRIVATE_|>Duyệt sản phẩm</);
});
test('missing adapter disables state-changing submit and explains availability', () => {
  const html = render();
  assert.match(html, /<button[^>]*disabled[^>]*>Gửi duyệt<\/button>/);
  assert.match(html, /Chưa kết nối API/);
});
test('form uses only domain fields and keeps SKU immutable in edit mode', () => {
  const html = renderToStaticMarkup(createElement(ProductForm, { product, categories: ready.categories, busy: false, canSave: false, onSave() {}, onCancel() {} }));
  assert.match(html, /<input(?=[^>]*name="sku")(?=[^>]*readOnly)[^>]*>/);
  assert.doesNotMatch(html, /name="(?:ownerReference|reviewReason|status|storeId)"/);
  assert.match(html, /9007199254740993/);
});
test('adapter presence without session lifetime context does not enable submit', () => {
  const html = render(ready, { actions: { submitForReview: async () => ({ status: 'confirmed' }) } });
  assert.match(html, /<button[^>]*disabled[^>]*>Gửi duyệt<\/button>/);
});
