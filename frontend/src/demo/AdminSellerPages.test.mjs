import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let server, sellerPages, adminPages;
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  sellerPages = await server.ssrLoadModule('/src/demo/SellerPages.tsx');
  adminPages = await server.ssrLoadModule('/src/demo/AdminPages.tsx');
});
after(async () => { await server?.close(); });

const sellerA = { id: '11111111-1111-4111-8111-111111111111', accountId: 'seller-a', version: '0', status: 'APPROVED', reviewReason: null };
const sellerB = { id: '11111111-1111-4111-8111-111111111112', accountId: 'seller-b', version: '0', status: 'APPROVED', reviewReason: 'PRIVATE_B_REASON' };
const storeA = { id: '22222222-2222-4222-8222-222222222221', sellerId: sellerA.id, name: 'Cửa hàng riêng A', slug: 'cua-hang-a', open: true, version: '0' };
const storeB = { id: '22222222-2222-4222-8222-222222222222', sellerId: sellerB.id, name: 'PRIVATE_B_STORE', slug: 'private-b', open: true, version: '0' };
const product = { id: 'product-a', storeId: storeA.id, categoryId: 'cat-a', name: 'Bình gốm', description: 'Mô tả bình', sku: 'BINH-A', status: 'ACTIVE', unitPrice: '200000', currency: 'VND', version: '0', reviewReason: null, images: [], art: 'vase' };
const state = { revision: 0, sellers: [sellerA, sellerB], stores: [storeA, storeB], products: [product, ...Array.from({ length: 7 }, (_, index) => ({ ...product, id: `private-product-${index}`, name: 'PRIVATE_B_PRODUCT', storeId: storeB.id }))], categories: [{ id: 'cat-a', name: 'Đồ gốm', parentId: null }] };
const props = { state, session: { role: 'seller', accountId: 'seller-a' }, dispatch() { throw new Error('render must not mutate'); }, navigate() {} };
const render = (Component, overrides = {}) => renderToStaticMarkup(createElement(Component, { ...props, ...overrides }));

test('Seller pages conceal private information when role is not Seller', () => {
  for (const Component of Object.values(sellerPages)) {
    const html = render(Component, { session: { role: 'customer', accountId: 'seller-a' } });
    assert.doesNotMatch(html, /Cửa hàng riêng A|PRIVATE_B|cua-hang-a|Bình gốm/);
    assert.match(html, /Chuyển sang vai trò Người bán/);
  }
});
test('Admin pages conceal every private list from a Seller session', () => {
  for (const Component of Object.values(adminPages)) {
    const html = render(Component);
    assert.doesNotMatch(html, /Cửa hàng riêng A|PRIVATE_B|Bình gốm|Đồ gốm/);
    assert.match(html, /Chuyển sang vai trò Quản trị/);
  }
});
test('Seller dashboard statistics include current Store products only', () => {
  const html = render(sellerPages.SellerDashboard);
  assert.match(html, /Tổng sản phẩm<\/span><strong>1<\/strong>/);
  assert.match(html, /Đang bán<\/span><strong>1<\/strong>/);
  assert.doesNotMatch(html, /PRIVATE_B|<strong>8<\/strong>/);
  assert.match(html, /Cửa hàng riêng A/);
});
test('Seller without a Store gets empty statistics without another Store leaking', () => {
  const html = render(sellerPages.SellerDashboard, { state: { ...state, stores: [storeB] } });
  assert.match(html, /Tổng sản phẩm<\/span><strong>0<\/strong>/);
  assert.doesNotMatch(html, /PRIVATE_B/);
});
test('Rejected Seller sees own reason and resubmission while waiting Seller cannot resubmit', () => {
  const rejected = render(sellerPages.SellerProfilePage, { state: { ...state, sellers: [{ ...sellerA, status: 'REJECTED', reviewReason: 'Cần kiểm tra hồ sơ cửa hàng.' }, sellerB] } });
  assert.match(rejected, /Cần kiểm tra hồ sơ cửa hàng/);
  assert.match(rejected, /Gửi lại để xét duyệt/);
  assert.doesNotMatch(rejected, /PRIVATE_B_REASON/);
  const pending = render(sellerPages.SellerProfilePage, { state: { ...state, sellers: [{ ...sellerA, status: 'PENDING_REVIEW' }, sellerB] } });
  assert.match(pending, /đang chờ xét duyệt/);
  assert.doesNotMatch(pending, /Gửi lại để xét duyệt|Gửi đăng ký/);
});
test('Suspended Seller cannot edit or toggle Store even when an open Store exists', () => {
  const html = render(sellerPages.StorePage, { state: { ...state, sellers: [{ ...sellerA, status: 'SUSPENDED' }, sellerB] } });
  assert.match(html, /Hồ sơ cần được phê duyệt/);
  assert.doesNotMatch(html, /Lưu thay đổi|Đóng cửa hàng|cua-hang-a/);
});
test('Admin Product queue initially contains pending Products and excludes active Products', () => {
  const html = render(adminPages.AdminProductsPage, { session: { role: 'admin', accountId: 'admin-demo' }, state: { ...state, products: [product, { ...product, id: 'pending', name: 'Sản phẩm đang chờ', status: 'PENDING_REVIEW' }] } });
  assert.match(html, /Sản phẩm đang chờ/);
  assert.doesNotMatch(html, /Bình gốm|PRIVATE_B_PRODUCT/);
});
