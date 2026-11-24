import assert from 'node:assert/strict';
import test from 'node:test';
import { clearFilters, hasFilters } from './catalogFilters.ts';

test('clearing filters drops price, category, store and the page but keeps the search and the sort', () => {
  const params = new URLSearchParams('q=ao&sort=PRICE_ASC&minPrice=100000&maxPrice=500000&categoryId=c1&storeId=s1&page=2');
  assert.equal(clearFilters(params).toString(), 'q=ao&sort=PRICE_ASC');
  assert.equal(params.get('minPrice'), '100000', 'the current params are not changed');
});

test('filters count only price, category and store', () => {
  assert.equal(hasFilters(new URLSearchParams('q=ao&sort=NAME_ASC&page=1')), false);
  for (const key of ['minPrice', 'maxPrice', 'categoryId', 'storeId']) {
    assert.equal(hasFilters(new URLSearchParams(`${key}=x`)), true, key);
  }
});
