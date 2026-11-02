import test from 'node:test';
import assert from 'node:assert/strict';
import { assertCheckoutRefusal } from './negative-refusal-contract.mjs';

const item = { productId: 'fixture-product-B', reason: 'OUT_OF_STOCK' };
const modern = { code: 'CHECKOUT_ITEMS_REJECTED', message: 'Some Cart items cannot be ordered', items: [item] };
const legacy = { code: 'BUSINESS_CONFLICT', message: 'Not enough stock for fixture-SKU-B' };
const options = { contract: 'itemized147', legacyMessage: 'Not enough stock', item };

test('147 accepts exactly the declared product and stock reason', () => {
  assertCheckoutRefusal(modern, options);
});
test('147 cannot pass with the old generic refusal', () => {
  assert.throws(() => assertCheckoutRefusal(legacy, options));
});
test('147 rejects a different product identity', () => {
  assert.throws(() => assertCheckoutRefusal({ ...modern, items: [{ ...item, productId: 'fixture-product-A' }] }, options));
});
test('147 rejects an invented stock reason or missing item list', () => {
  assert.throws(() => assertCheckoutRefusal({ ...modern, items: [{ ...item, reason: 'INVALID_QUANTITY' }] }, options));
  assert.throws(() => assertCheckoutRefusal({ ...modern, items: undefined }, options));
});
test('147 does not silently discard extra refused products', () => {
  assert.throws(() => assertCheckoutRefusal({ ...modern, items: [item, { ...item, productId: 'fixture-product-A' }] }, options));
});
test('145 retains its explicitly selected legacy contract', () => {
  assertCheckoutRefusal(legacy, { ...options, contract: 'legacy145' });
  assert.throws(() => assertCheckoutRefusal(modern, { ...options, contract: 'legacy145' }));
});
test('payload mismatch remains non-itemized under both contracts', () => {
  for (const contract of ['legacy145', 'itemized147'])
    assertCheckoutRefusal({ code: 'REQUEST_REJECTED', message: 'IDEMPOTENCY_PAYLOAD_MISMATCH' },
      { contract, legacyMessage: 'IDEMPOTENCY_PAYLOAD_MISMATCH' });
});
test('unknown contract fails closed', () => {
  assert.throws(() => assertCheckoutRefusal(modern, { ...options, contract: 'guessed' }));
});
