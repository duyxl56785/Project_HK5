import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';
let server, DemoApp, CatalogPage, ProductDetail, ProductManagementPage, createDemoState;
before(async () => {
  server = await createServer({ optimizeDeps: { noDiscovery: true, entries: [] }, server: { middlewareMode: true, hmr: { port: 0 }, watch: null } });
  ({ DemoApp } = await server.ssrLoadModule('/src/demo/DemoApp.tsx'));
  ({ CatalogPage, ProductDetail } = await server.ssrLoadModule('/src/demo/CatalogPages.tsx'));
  ({ ProductManagementPage } = await server.ssrLoadModule('/src/features/product/ProductManagementPage.tsx'));
  ({ createDemoState } = await server.ssrLoadModule('/src/demo/demoModel.ts'));
});
after(async () => server?.close());
test('independent demo identifies fixtures and offers role/reset controls without a JWT', () => {
  const html = renderToStaticMarkup(createElement(DemoApp));
  assert.match(html, /CHẾ ĐỘ DEMO — DỮ LIỆU MẪU/); assert.match(html, /Vai trò demo/); assert.match(html, /Reset demo/);
  assert.doesNotMatch(html, /Bearer |accessToken|refreshToken/);
});
test('Catalog refuses ACTIVE products when the current fixture seller is suspended', () => {
  const state = createDemoState(); state.sellers = state.sellers.map(seller => ({ ...seller, status: 'SUSPENDED' }));
  const html = renderToStaticMarkup(createElement(CatalogPage, { state, navigate() {} }));
  assert.match(html, /Chưa tìm thấy điều bạn cần/); assert.doesNotMatch(html, /class="demo-product-card"/);
});
test('detail does not disclose an unapproved/private product by guessed id', () => {
  const state = createDemoState(), product = state.products.find(item => item.status === 'DRAFT');
  const html = renderToStaticMarkup(createElement(ProductDetail, { state, id: product.id, navigate() {} }));
  assert.match(html, /Sản phẩm chưa sẵn sàng/); assert.ok(!html.includes(product.name)); assert.ok(!html.includes(product.sku));
});
test('optional presentation pagination retains the complete Store-scoped data boundary', () => {
  const state = createDemoState(), store = state.stores[0], products = state.products.filter(item => item.storeId === store.id);
  const html = renderToStaticMarkup(createElement(ProductManagementPage, { state: { status: 'ready', store, products, categories: state.categories }, pageSize: 2 }));
  assert.equal((html.match(/<li class="tv2-product__card"/g) ?? []).length, 2);
  assert.match(html, /Phân trang sản phẩm quản lý/); assert.match(html, /Trang sau/);
});
test('the default Product feature keeps its existing unpaged behavior', () => {
  const state = createDemoState(), store = state.stores[0], products = state.products.filter(item => item.storeId === store.id);
  const html = renderToStaticMarkup(createElement(ProductManagementPage, { state: { status: 'ready', store, products, categories: state.categories } }));
  assert.equal((html.match(/<li class="tv2-product__card"/g) ?? []).length, products.length);
  assert.doesNotMatch(html, /Phân trang sản phẩm quản lý/);
});
