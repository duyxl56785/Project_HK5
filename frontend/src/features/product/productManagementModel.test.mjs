import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createServer } from 'vite';
import { categories, product, store } from './productTestFixtures.mjs';

let server, model;
before(async () => {
  server = await createServer({ optimizeDeps: { noDiscovery: true, entries: [] }, server: { middlewareMode: true, hmr: { port: 0 }, watch: null } });
  model = await server.ssrLoadModule('/src/features/product/productManagementModel.ts');
});
after(async () => { await server?.close(); });
const fields = () => ({ name: '  Cốc gốm Việt  ', sku: 'GOM-é-01', categoryId: categories[0].id, unitPrice: '125000', currency: 'VND' });

test('validates real domain fields, canonicalizes name only and retains exact SKU/money', () => {
  assert.deepEqual(model.validateProductFields(fields(), categories).value, { ...fields(), name: 'Cốc gốm Việt' });
});
for (const price of ['', '0', '-1', '1.5', '1.0', '01', '1e3', 'NaN', 'Infinity', '1000000001', '1\n', ' 1']) {
  test(`rejects noncanonical/out-of-range price ${JSON.stringify(price)}`, () => assert.ok(model.validateProductFields({ ...fields(), unitPrice: price }, categories).errors.unitPrice));
}
for (const price of ['1', '1000000000']) {
  test(`accepts exact VND boundary ${price}`, () => assert.equal(model.validateProductFields({ ...fields(), unitPrice: price }, categories).value.unitPrice, price));
}
test('requires explicit currency and existing UUID category', () => {
  for (const currency of ['', 'USD', 'vnd', 'VND ']) assert.ok(model.validateProductFields({ ...fields(), currency }, categories).errors.currency);
  for (const categoryId of ['', 'missing', store.id]) assert.ok(model.validateProductFields({ ...fields(), categoryId }, categories).errors.categoryId);
});
test('SKU keeps case and normalization identity, enforces UTF-16 length', () => {
  for (const sku of ['abc', 'ABC', 'é', 'e\u0301', '🛍'.repeat(64), '\ufeffSKU']) assert.equal(model.validateProductFields({ ...fields(), sku }, categories).value.sku, sku);
  for (const sku of ['a'.repeat(129), '🛍'.repeat(65), '', ' \t', '\u00a0SKU', 'SKU\u2007', '\u202fSKU', '\u3000SKU', 'SKU\n', 'a\0b', '\ud800', '\udc00']) assert.ok(model.validateProductFields({ ...fields(), sku }, categories).errors.sku, JSON.stringify(sku));
});
test('name uses Java strip semantics without silently stripping NBSP or BOM', () => {
  assert.equal(model.validateProductFields({ ...fields(), name: '\u2003Cốc\u3000' }, categories).value.name, 'Cốc');
  for (const name of ['\u00a0Cốc\u00a0', '\ufeffCốc']) assert.equal(model.validateProductFields({ ...fields(), name }, categories).value.name, name);
  for (const name of ['', '\t\u2003', 'a'.repeat(256), '\ud800', 'a\0b']) assert.ok(model.validateProductFields({ ...fields(), name }, categories).errors.name);
  assert.ok(model.validateProductFields({ ...fields(), name: '🛍'.repeat(127) + 'a' }, categories).value);
});
test('projects one Store only and removes private extras', () => {
  const input = { ...product, ownerReference: 'PRIVATE_OWNER', reviewReason: 'PRIVATE_REASON', roles: ['ADMIN'] };
  const result = model.readProductManagement(store, [input], categories);
  assert.ok(result);
  assert.deepEqual(result.products[0], product);
  assert.ok(!JSON.stringify(result).includes('PRIVATE_'));
});
test('invalid data, duplicate IDs, wrong Store, missing Category fail as a whole', () => {
  for (const row of [{ ...product, storeId: categories[0].id }, { ...product, version: 1 }, { ...product, version: '9223372036854775808' }, { ...product, status: 'APPROVED' }, { ...product, unitPrice: '1.5' }, { ...product, currency: 'USD' }, { ...product, id: product.id + '\n' }, { ...product, categoryId: store.id }]) assert.equal(model.readProductManagement(store, [product, row], categories), null);
  assert.equal(model.readProductManagement(store, [product, product], categories), null);
  assert.equal(model.readProductManagement(store, [product], [{ ...categories[0], parentId: categories[0].id }]), null);
});
test('preserves exact BIGINT versions and only domain-editable states', () => {
  for (const version of ['0', '9007199254740993', '9223372036854775807']) assert.equal(model.readProductManagement(store, [{ ...product, version }], categories).products[0].version, version);
  for (const status of ['DRAFT', 'REJECTED', 'INACTIVE']) assert.equal(model.canEditProduct(status), true);
  for (const status of ['PENDING_REVIEW', 'ACTIVE', 'SUSPENDED', 'UNKNOWN']) assert.equal(model.canEditProduct(status), false);
});
