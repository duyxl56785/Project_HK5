import assert from 'node:assert/strict';
import test from 'node:test';
import { baseName, initialSelection, optionValues, pickVariant, variantLabel } from './variantModel.ts';

const offer = (id, size, color, available = 3) => ({ id, size, color, sku: id, unitPrice: '150000', baseUnitPrice: '150000', promotion: null, available });
const offers = [offer('m-red', 'M', 'Đỏ', 0), offer('l-red', 'L', 'Đỏ'), offer('m-blue', 'M', 'Xanh')];

test('option values keep the seller order and appear once', () => {
  assert.deepEqual(optionValues(offers), { sizes: ['M', 'L'], colors: ['Đỏ', 'Xanh'] });
  assert.deepEqual(optionValues([offer('a', 'S', null), offer('b', 'M', null)]), { sizes: ['S', 'M'], colors: [] });
});

test('a pick needs every option the product uses', () => {
  assert.equal(pickVariant(offers, { size: 'L', color: 'Đỏ' })?.id, 'l-red');
  assert.equal(pickVariant(offers, { size: 'L', color: 'Xanh' }), undefined);
  assert.equal(pickVariant(offers, { size: 'L', color: null }), undefined);
  assert.equal(pickVariant([offer('s', 'S', null)], { size: 'S', color: null })?.id, 's');
});

test('the opened variant is preselected, otherwise the first one in stock', () => {
  assert.deepEqual(initialSelection(offers, 'm-blue'), { size: 'M', color: 'Xanh' });
  assert.deepEqual(initialSelection(offers, 'group-lead'), { size: 'L', color: 'Đỏ' });
  assert.deepEqual(initialSelection([offer('x', 'M', 'Đỏ', 0)], 'x2'), { size: 'M', color: 'Đỏ' });
  assert.deepEqual(initialSelection([], 'x'), { size: null, color: null });
});

test('label and base name match the server naming', () => {
  assert.equal(variantLabel('M', 'Đỏ'), 'M / Đỏ');
  assert.equal(variantLabel(null, 'Đỏ'), 'Đỏ');
  assert.equal(baseName('Áo thun (M / Đỏ)', 'M', 'Đỏ'), 'Áo thun');
  assert.equal(baseName('Áo thun (L)', 'M', null), 'Áo thun (L)');
});
