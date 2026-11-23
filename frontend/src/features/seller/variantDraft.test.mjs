import assert from 'node:assert/strict';
import test from 'node:test';
import { buildRows, rowErrors, skuPart, splitOptions, toVariantBody } from './variantDraft.ts';

test('options are split on commas, trimmed and deduplicated case-insensitively', () => {
  assert.deepEqual(splitOptions(' S, m ,M,, XL '), ['S', 'm', 'XL']);
  assert.deepEqual(splitOptions('   '), []);
});

test('sku parts are ASCII without Vietnamese marks', () => {
  assert.equal(skuPart('Đỏ đô'), 'DO-DO');
  assert.equal(skuPart('Xanh lá'), 'XANH-LA');
  assert.equal(skuPart('XL'), 'XL');
});

test('rows cover every size and color with a generated SKU and the shared price', () => {
  assert.deepEqual(buildRows(['M', 'L'], ['Đỏ', 'Xanh'], 'AO', '150000'), [
    { size: 'M', color: 'Đỏ', sku: 'AO-M-DO', unitPrice: '150000', initialStock: '0' },
    { size: 'M', color: 'Xanh', sku: 'AO-M-XANH', unitPrice: '150000', initialStock: '0' },
    { size: 'L', color: 'Đỏ', sku: 'AO-L-DO', unitPrice: '150000', initialStock: '0' },
    { size: 'L', color: 'Xanh', sku: 'AO-L-XANH', unitPrice: '150000', initialStock: '0' },
  ]);
  assert.deepEqual(buildRows(['S'], [], '', '1'), [{ size: 'S', color: null, sku: 'S', unitPrice: '1', initialStock: '0' }]);
  assert.deepEqual(buildRows([], ['Đen'], 'MU', ''), [{ size: null, color: 'Đen', sku: 'MU-DEN', unitPrice: '', initialStock: '0' }]);
  assert.deepEqual(buildRows([], [], 'X', '1'), []);
});

test('each row needs a unique SKU, a price and a stock in range', () => {
  const rows = [
    { size: 'M', color: null, sku: 'A', unitPrice: '1000', initialStock: '2' },
    { size: 'L', color: null, sku: 'a', unitPrice: '0', initialStock: '-1' },
    { size: 'XL', color: null, sku: ' ', unitPrice: '1.5', initialStock: '1000001' },
  ];
  assert.deepEqual(rowErrors(rows), [
    {},
    { sku: 'SKU bị trùng', unitPrice: 'Giá phải là số nguyên từ 1', initialStock: 'Tồn kho từ 0 đến 1.000.000' },
    { sku: 'Nhập SKU', unitPrice: 'Giá phải là số nguyên từ 1', initialStock: 'Tồn kho từ 0 đến 1.000.000' },
  ]);
  assert.equal(rowErrors([]).length, 0);
});

test('the request body carries numbers and absent options as null', () => {
  assert.deepEqual(toVariantBody([{ size: 'M', color: null, sku: ' A ', unitPrice: '1000', initialStock: '2' }]),
    [{ size: 'M', color: null, sku: 'A', unitPrice: 1000, initialStock: 2 }]);
});
