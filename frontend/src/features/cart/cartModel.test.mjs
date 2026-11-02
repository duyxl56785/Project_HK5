import assert from 'node:assert/strict';
import test from 'node:test';
import { canonicalRevision, minorUnits, projectCart, renderMinorUnits } from './cartModel.ts';

const base = {
  revision: '9007199254740993',
  claimed: false,
  items: [
    { productId: 'p1', storeId: 'store-A', sellerId: 'seller-A', title: 'Áo thun', quantity: 2, unitPriceSnapshot: '10000.00' },
    { productId: 'p2', storeId: 'store-B', sellerId: 'seller-B', title: 'Sách', quantity: 1, unitPriceSnapshot: '15000.00' },
  ],
};

test('two Store groups preserve exact revision and amount without JS floating point', () => {
  const view = projectCart(base);
  assert.ok(view);
  assert.equal(view.revision, '9007199254740993');
  assert.equal(view.itemCount, 2);
  assert.equal(view.groups.length, 2);
  assert.equal(view.groups[0].storeSubtotal, '20.000,00');
  assert.equal(view.groups[1].storeSubtotal, '15.000,00');
  assert.equal(view.total, '35.000,00');
  assert.equal(view.groups[0].lines[0].lineSubtotal, '20.000,00');
});
test('multiple products in same Store and Seller render one Store group', () => {
  const items = [{ ...base.items[0] }, { ...base.items[1], storeId: 'store-A', sellerId: 'seller-A' }];
  assert.equal(projectCart({ ...base, items }).groups.length, 1);
});
test('conflicting Store to Seller or Seller to Store mapping fails closed', () => {
  assert.equal(projectCart({ ...base, items: [{ ...base.items[0] }, { ...base.items[1], sellerId: 'seller-A' }] }), null);
  assert.equal(projectCart({ ...base, items: [{ ...base.items[0] }, { ...base.items[1], storeId: 'store-A' }] }), null);
});
test('duplicates, malformed or padded identifiers fail without partial group', () => {
  for (const change of [{ productId: 'p1' }, { productId: ' p2' }, { storeId: '\u2003store-B' }, { sellerId: '' }]) {
    assert.equal(projectCart({ ...base, items: [base.items[0], { ...base.items[1], ...change }] }), null);
  }
});
test('money uses exact decimal minor units and forbids silent rounding', () => {
  assert.equal(minorUnits('1.2300'), 123n);
  assert.equal(minorUnits('0'), 0n);
  assert.equal(minorUnits('0.01'), 1n);
  for (const invalid of ['1.001', '1e3', '1,00', '-1', '01', '999999999999999999.99', 1.23, null]) {
    assert.equal(minorUnits(invalid), null, String(invalid));
  }
  assert.equal(renderMinorUnits(9999999999999999999n), '99.999.999.999.999.999,99');
});
test('line and aggregate overflow rejected before displaying calculated totals', () => {
  assert.equal(projectCart({ ...base, items: [{ ...base.items[0], unitPriceSnapshot: '90000000000000000.00' }] }), null);
  assert.equal(projectCart({ ...base, items: [
    { ...base.items[0], quantity: 1, unitPriceSnapshot: '60000000000000000.00' },
    { ...base.items[1], quantity: 1, unitPriceSnapshot: '50000000000000000.00' },
  ] }), null);
});
test('quantity, claims and revisions are strictly validated', () => {
  for (const quantity of [0, -1, 1001, 1.2, NaN, null]) {
    assert.equal(projectCart({ ...base, items: [{ ...base.items[0], quantity }] }), null);
  }
  for (const revision of [9007199254740993, '01', '-1', '9223372036854775808']) {
    assert.equal(canonicalRevision(revision), false);
    assert.equal(projectCart({ ...base, revision }), null);
  }
  assert.equal(projectCart({ ...base, claimed: 'false' }), null);
  assert.equal(projectCart({ ...base, items: [] }).total, '0,00');
});
test('source input is neither mutated nor trusted for extra private fields', () => {
  const source = Object.freeze({ ...base, token: 'PRIVATE_TOKEN', items: Object.freeze(base.items.map((x) => Object.freeze({ ...x, email: 'PRIVATE_EMAIL' }))) });
  const view = projectCart(source);
  assert.ok(view);
  assert.doesNotMatch(JSON.stringify(view), /PRIVATE_TOKEN|PRIVATE_EMAIL/);
});
