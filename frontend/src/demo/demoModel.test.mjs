import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createServer } from 'vite';

let server, model;
before(async () => {
  server = await createServer({ optimizeDeps: { noDiscovery: true, entries: [] }, server: { middlewareMode: true, hmr: { port: 0 }, watch: null } });
  model = await server.ssrLoadModule('/src/demo/demoModel.ts');
});
after(async () => { await server?.close(); });
const seller = { role: 'seller', accountId: 'seller-a' };
const other = { role: 'seller', accountId: 'seller-b' };
const newcomer = { role: 'seller', accountId: 'seller-new' };
const admin = { role: 'admin', accountId: 'demo-admin' };
const customer = { role: 'customer', accountId: 'demo-customer' };
const fresh = () => model.createDemoState();
const apply = (state, session, action) => model.transition(state, session, action);
const product = (state, status = 'DRAFT') => state.products.find((item) => item.storeId === model.ownedStore(state, seller.accountId).id && item.status === status);
const fields = (state, overrides = {}) => ({ storeId: model.ownedStore(state, seller.accountId).id, name: '  Cốc gốm mới  ', sku: 'DEMO-NEW', categoryId: state.categories[1].id, unitPrice: '125000', currency: 'VND', ...overrides });
const update = (item, overrides = {}) => ({ type: 'product.update', fields: { productId: item.id, expectedVersion: item.version, name: item.name, categoryId: item.categoryId, unitPrice: item.unitPrice, currency: 'VND', ...overrides } });
const moderateSeller = (state, accountId, decision, reason) => { const row = model.ownedSeller(state, accountId); return apply(state, admin, { type: 'seller.moderate', sellerId: row.id, expectedVersion: row.version, decision, reason }); };

test('fixtures have six valid categories, twelve products across all states and seven public items', () => {
  const state = fresh();
  assert.equal(state.categories.length, 6);
  assert.equal(state.products.length, 12);
  assert.equal(new Set(state.products.map((row) => row.status)).size, 6);
  assert.equal(model.publicProducts(state).length, 7);
  for (const row of [...state.categories, ...state.sellers, ...state.stores, ...state.products]) assert.match(row.id, /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/);
  assert.equal(model.ownedSeller(state, newcomer.accountId), undefined);
  assert.equal(model.ownedStore(state, newcomer.accountId), undefined);
  state.products[0].images.push({ id: 'changed' });
  assert.equal(fresh().products[0].images.length, 0);
});

test('registration, rejection, resubmission and approval retain the same seller ID', () => {
  let state = apply(fresh(), newcomer, { type: 'seller.register' });
  const id = model.ownedSeller(state, newcomer.accountId).id;
  assert.equal(model.ownedSeller(state, newcomer.accountId).status, 'PENDING_REVIEW');
  assert.throws(() => apply(state, newcomer, { type: 'seller.register' }));
  state = moderateSeller(state, newcomer.accountId, 'reject', '  Thiếu thông tin  ');
  assert.equal(model.ownedSeller(state, newcomer.accountId).reviewReason, 'Thiếu thông tin');
  state = apply(state, newcomer, { type: 'seller.resubmit', sellerId: id, expectedVersion: '1' });
  state = moderateSeller(state, newcomer.accountId, 'approve');
  assert.equal(model.ownedSeller(state, newcomer.accountId).id, id);
  assert.equal(model.ownedSeller(state, newcomer.accountId).version, '3');
  state = apply(state, newcomer, { type: 'store.save', name: 'Tiệm mới', slug: 'tiem-moi' });
  assert.equal(model.ownedStore(state, newcomer.accountId).open, true);
});

test('roles, ownership, stale versions and self moderation cannot change state', () => {
  const state = fresh(), row = product(state);
  for (const session of [customer, other, admin]) assert.throws(() => apply(state, session, update(row)));
  assert.throws(() => apply(state, seller, update(row, { expectedVersion: '1' })));
  const owner = model.ownedSeller(state, seller.accountId);
  assert.throws(() => apply(state, { role: 'admin', accountId: seller.accountId }, { type: 'seller.moderate', sellerId: owner.id, expectedVersion: owner.version, decision: 'suspend', reason: 'Kiểm tra' }));
  assert.throws(() => apply(state, { role: 'admin', accountId: seller.accountId }, { type: 'product.moderate', productId: product(state, 'ACTIVE').id, expectedVersion: '0', decision: 'suspend', reason: 'Kiểm tra' }));
  assert.equal(state.revision, 0);
});

test('seller suspension hides public products, prevents management, and reinstatement restores visibility', () => {
  const initial = fresh();
  let state = moderateSeller(initial, seller.accountId, 'suspend', 'Cần xác minh');
  assert.ok(model.publicProducts(state).every((row) => row.storeId !== model.ownedStore(state, seller.accountId).id));
  assert.throws(() => apply(state, seller, update(product(state))));
  const store = model.ownedStore(state, seller.accountId);
  assert.throws(() => apply(state, seller, { type: 'store.toggle', storeId: store.id, expectedVersion: store.version, open: false }));
  state = moderateSeller(state, seller.accountId, 'reinstate');
  assert.equal(model.publicProducts(state).length, model.publicProducts(initial).length);
  assert.equal(model.ownedSeller(state, seller.accountId).reviewReason, null);
});

test('store uniqueness and slug validation use current owner and preserve closed-store management', () => {
  let state = fresh();
  const store = model.ownedStore(state, seller.accountId);
  assert.throws(() => apply(state, seller, { type: 'store.save', name: 'Trùng', slug: 'trung' }));
  assert.throws(() => apply(state, other, { type: 'store.save', storeId: store.id, expectedVersion: store.version, name: 'Khác', slug: 'khac' }));
  for (const slug of ['', 'Upper', '-bad', 'bad--slug', 'bad\n', 'bad\r', 'bad\u2028', 'a'.repeat(129), model.ownedStore(state, other.accountId).slug]) assert.throws(() => apply(state, seller, { type: 'store.save', storeId: store.id, expectedVersion: store.version, name: 'Nếp', slug }));
  state = apply(state, seller, { type: 'store.toggle', storeId: store.id, expectedVersion: '0', open: false });
  assert.throws(() => apply(state, seller, update(product(state))));
  state = apply(state, seller, { type: 'store.save', storeId: store.id, expectedVersion: '1', name: '\u2003Nếp mới\u3000', slug: 'nep-moi' });
  assert.equal(model.ownedStore(state, seller.accountId).name, 'Nếp mới');
  assert.equal(model.ownedStore(state, seller.accountId).open, false);
  assert.equal(model.publicProducts(state).filter((row) => row.storeId === store.id).length, 0);
});

test('create uses existing validation, exact global SKU uniqueness and immutable identity', () => {
  const state = fresh();
  for (const unitPrice of ['0', '01', '1.5', '1e3', '1000000001']) assert.throws(() => apply(state, seller, { type: 'product.create', fields: fields(state, { unitPrice }) }));
  for (const currency of ['', 'USD', 'VND ']) assert.throws(() => apply(state, seller, { type: 'product.create', fields: fields(state, { currency }) }));
  assert.throws(() => apply(state, seller, { type: 'product.create', fields: fields(state, { sku: state.products.at(-1).sku }) }));
  assert.throws(() => apply(state, seller, { type: 'product.create', fields: fields(state, { storeId: model.ownedStore(state, other.accountId).id }) }));
  for (const unitPrice of ['1', '1000000000']) assert.equal(apply(state, seller, { type: 'product.create', fields: fields(state, { unitPrice }) }).products.at(-1).unitPrice, unitPrice);
  const next = apply(state, seller, { type: 'product.create', fields: fields(state) });
  const created = next.products.at(-1);
  assert.equal(created.name, 'Cốc gốm mới');
  assert.equal(created.status, 'DRAFT');
  assert.equal(state.products.length, 12);
  assert.throws(() => apply(next, seller, update(created, { sku: 'FORGED' })));
  assert.throws(() => apply(next, seller, update(created, { storeId: model.ownedStore(next, other.accountId).id })));
});

test('product draft workflow retains supplemental fields and exact large versions', () => {
  let state = fresh();
  const item = product(state);
  state = { ...state, products: state.products.map((row) => row.id === item.id ? { ...row, version: '9007199254740993' } : row) };
  state = apply(state, seller, update({ ...item, version: '9007199254740993' }, { name: 'Bình mới' }));
  let row = state.products.find((entry) => entry.id === item.id);
  assert.equal(row.version, '9007199254740994');
  assert.equal(row.description, item.description);
  assert.equal(row.art, item.art);
  state = apply(state, seller, { type: 'product.submit', fields: { productId: row.id, expectedVersion: row.version } });
  row = state.products.find((entry) => entry.id === row.id);
  assert.equal(row.status, 'PENDING_REVIEW');
  assert.throws(() => apply(state, seller, update(row)));
  state = apply(state, admin, { type: 'product.moderate', productId: row.id, expectedVersion: row.version, decision: 'approve' });
  row = state.products.find((entry) => entry.id === row.id);
  assert.ok(model.publicProducts(state).some((entry) => entry.id === row.id));
  state = apply(state, seller, { type: 'product.deactivate', productId: row.id, expectedVersion: row.version });
  row = state.products.find((entry) => entry.id === row.id);
  assert.equal(row.status, 'INACTIVE');
  assert.ok(!model.publicProducts(state).some((entry) => entry.id === row.id));
  assert.doesNotThrow(() => apply(state, seller, update(row)));
});

test('moderation requires valid transitions and reasons with Java strip and codepoint limits', () => {
  const state = fresh(), owner = model.ownedSeller(state, seller.accountId);
  const item = product(state, 'ACTIVE');
  for (const reason of ['', '\u2003\u3000', 'a'.repeat(501), 'x\0y', '\ud800']) {
    assert.throws(() => apply(state, admin, { type: 'seller.moderate', sellerId: owner.id, expectedVersion: owner.version, decision: 'suspend', reason }));
    assert.throws(() => apply(state, admin, { type: 'product.moderate', productId: item.id, expectedVersion: item.version, decision: 'suspend', reason }));
  }
  assert.doesNotThrow(() => moderateSeller(state, seller.accountId, 'suspend', '\u2003' + '😀'.repeat(500) + '\u3000'));
  assert.equal(model.ownedSeller(moderateSeller(state, seller.accountId, 'suspend', '\u00a0'), seller.accountId).reviewReason, '\u00a0');
  assert.throws(() => moderateSeller(state, seller.accountId, 'approve'));
  assert.throws(() => apply(state, admin, { type: 'product.moderate', productId: item.id, expectedVersion: item.version, decision: 'approve' }));
});

test('category CRUD allows duplicate names but rejects cycles, missing parents and referenced deletion', () => {
  let state = fresh();
  const root = state.categories[0], child = state.categories.find((row) => row.parentId === root.id);
  assert.throws(() => apply(state, customer, { type: 'category.save', name: 'Mới', parentId: null }));
  for (const parentId of [root.id, child.id, 'missing']) assert.throws(() => apply(state, admin, { type: 'category.save', id: root.id, name: root.name, parentId }));
  assert.throws(() => apply(state, admin, { type: 'category.delete', id: root.id }));
  assert.throws(() => apply(state, admin, { type: 'category.delete', id: child.id }));
  assert.throws(() => apply(state, admin, { type: 'category.save', name: ' '.repeat(255) + 'A', parentId: null }));
  state = apply(state, admin, { type: 'category.save', name: root.name, parentId: null });
  const added = state.categories.at(-1);
  state = apply(state, admin, { type: 'category.save', id: added.id, name: '  Đồ nhỏ  ', parentId: root.id });
  assert.equal(state.categories.at(-1).name, 'Đồ nhỏ');
  state = apply(state, admin, { type: 'category.delete', id: added.id });
  assert.equal(state.categories.length, 6);
});

test('public visibility fails closed for missing and mismatched associations', () => {
  const state = fresh(), item = product(state, 'ACTIVE');
  for (const broken of [
    { ...state, stores: state.stores.filter((row) => row.id !== item.storeId) },
    { ...state, sellers: state.sellers.filter((row) => row.accountId !== seller.accountId) },
    { ...state, stores: state.stores.map((row) => row.id === item.storeId ? { ...row, sellerId: 'missing' } : row) },
    { ...state, categories: state.categories.filter((row) => row.id !== item.categoryId) },
  ]) assert.ok(!model.publicProducts(broken).some((row) => row.id === item.id));
});

test('image updates copy metadata and reject nonblob URLs, unsafe names and invalid media or limits', () => {
  const state = fresh(), row = product(state);
  const image = { id: 'preview-1', url: 'blob:https://demo.local/preview', name: 'binh.png', mediaType: 'image/png', sizeBytes: 2 * 1024 * 1024 };
  const action = (images) => ({ type: 'product.images', productId: row.id, expectedVersion: row.version, images });
  for (const patch of [{ url: 'https://example.com/img.png' }, { url: 'javascript:alert(1)' }, { name: '../binh.png' }, { name: 'bad\0.png' }, { mediaType: 'image/gif' }, { sizeBytes: 2097153 }, { sizeBytes: 0 }, { sizeBytes: 1.5 }]) assert.throws(() => apply(state, seller, action([{ ...image, ...patch }])));
  assert.throws(() => apply(state, seller, action(Array.from({ length: 6 }, (_, i) => ({ ...image, id: `i-${i}` })))));
  assert.throws(() => apply(state, seller, action([image, image])));
  assert.throws(() => apply(state, other, action([image])));
  const next = apply(state, seller, action([image]));
  image.name = 'changed.png';
  assert.equal(next.products.find((entry) => entry.id === row.id).images[0].name, 'binh.png');
  assert.equal(state.products.find((entry) => entry.id === row.id).images.length, 0);
  assert.throws(() => apply(state, seller, { ...action([]), productId: product(state, 'ACTIVE').id }));
});

test('rejected products can be resubmitted, while suspended products cannot be edited', () => {
  let state = fresh();
  let row = product(state);
  state = apply(state, seller, { type: 'product.submit', fields: { productId: row.id, expectedVersion: row.version } });
  row = state.products.find((entry) => entry.id === row.id);
  assert.throws(() => apply(state, seller, { type: 'product.moderate', productId: row.id, expectedVersion: row.version, decision: 'approve' }));
  state = apply(state, admin, { type: 'product.moderate', productId: row.id, expectedVersion: row.version, decision: 'reject', reason: '  Bổ sung thông tin  ' });
  row = state.products.find((entry) => entry.id === row.id);
  assert.equal(row.status, 'REJECTED');
  assert.equal(row.reviewReason, 'Bổ sung thông tin');
  state = apply(state, seller, update(row, { name: 'Thông tin đã bổ sung' }));
  row = state.products.find((entry) => entry.id === row.id);
  state = apply(state, seller, { type: 'product.submit', fields: { productId: row.id, expectedVersion: row.version } });
  row = state.products.find((entry) => entry.id === row.id);
  assert.equal(row.reviewReason, null);
  state = apply(state, admin, { type: 'product.moderate', productId: row.id, expectedVersion: row.version, decision: 'approve' });
  row = state.products.find((entry) => entry.id === row.id);
  state = apply(state, admin, { type: 'product.moderate', productId: row.id, expectedVersion: row.version, decision: 'suspend', reason: 'Cần xác minh' });
  row = state.products.find((entry) => entry.id === row.id);
  assert.equal(row.status, 'SUSPENDED');
  assert.throws(() => apply(state, seller, update(row)));
  assert.throws(() => apply(state, seller, { type: 'product.submit', fields: { productId: row.id, expectedVersion: row.version } }));
});

test('admin moderation preserves source rules when store is closed and seller suspended', () => {
  let state = fresh();
  const store = model.ownedStore(state, seller.accountId);
  const item = product(state, 'ACTIVE');
  state = apply(state, seller, { type: 'store.toggle', storeId: store.id, expectedVersion: store.version, open: false });
  state = moderateSeller(state, seller.accountId, 'suspend', 'Xác minh tài khoản');
  state = apply(state, admin, { type: 'product.moderate', productId: item.id, expectedVersion: item.version, decision: 'suspend', reason: 'Rà soát nội dung' });
  assert.equal(state.products.find((row) => row.id === item.id).status, 'SUSPENDED');
});

test('immutable snapshots and exact canonical versions prevent stale or overflowing writes', () => {
  const freeze = (value) => { if (value && typeof value === 'object') { for (const nested of Object.values(value)) freeze(nested); Object.freeze(value); } return value; };
  const state = freeze(fresh()), item = product(state);
  const next = apply(state, seller, update(item, { name: 'Tên mới' }));
  assert.equal(next.revision, 1);
  assert.equal(next.products.find((row) => row.id === item.id).name, 'Tên mới');
  assert.equal(item.name, 'Cốc men sương');
  for (const version of ['01', '-1', '1.0', '9223372036854775808']) assert.throws(() => apply(state, seller, update(item, { expectedVersion: version })));
  const max = { ...state, products: state.products.map((row) => row.id === item.id ? { ...row, version: '9223372036854775807' } : row) };
  assert.throws(() => apply(max, seller, update({ ...item, version: '9223372036854775807' })));
  const [root, child] = state.categories;
  const corrupt = { ...state, categories: state.categories.map((row) => row.id === root.id ? { ...row, parentId: child.id } : row) };
  assert.throws(() => apply(corrupt, admin, { type: 'category.save', name: 'Danh mục mới', parentId: child.id }));
});
