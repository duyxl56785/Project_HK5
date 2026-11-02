import assert from 'node:assert/strict';
import test from 'node:test';
import { canonicalCheckoutKey, createCheckoutOnce, prepareCheckout } from './checkoutModel.ts';

const cart = { revision: '9007199254740993', claimed: false, items: [
  { productId: 'p1', storeId: 's1', sellerId: 'seller1', title: 'Áo', quantity: 2, unitPriceSnapshot: '10000.00' },
  { productId: 'p2', storeId: 's2', sellerId: 'seller2', title: 'Sách', quantity: 1, unitPriceSnapshot: '15000.00' },
] };

test('two-Store expectedItems never invents SKU, price or a new idempotency key', () => {
  const req = prepareCheckout(cart, 'stable-key');
  assert.deepEqual(req, { idempotencyKey: 'stable-key', expectedRevision: '9007199254740993',
    expectedItems: [{ productId: 'p1', quantity: 2 }, { productId: 'p2', quantity: 1 }] });
  assert.doesNotMatch(JSON.stringify(req), /sku|unitPrice|sellerId|storeId|PRIVATE/);
});
test('invalid key, untrusted Cart and in-progress claim fail without request', () => {
  for (const key of ['', ' key', 'key ', '\u00a0key', 'key\u2003', 'x'.repeat(129)]) {
    assert.equal(canonicalCheckoutKey(key), false);
    assert.equal(prepareCheckout(cart, key), null);
  }
  assert.equal(prepareCheckout({ ...cart, claimed: true }, 'stable-key'), null);
  assert.equal(prepareCheckout({ ...cart, items: [] }, 'stable-key'), null);
  assert.equal(prepareCheckout({ ...cart, items: [...cart.items, cart.items[0]] }, 'stable-key'), null);
});
test('unknown from provider blocks all automatic repeat submissions', async () => {
  const gate = createCheckoutOnce();
  let count = 0;
  assert.equal(await gate.send(async () => { count++; return 'unknown'; }), 'unknown');
  assert.equal(await gate.send(async () => { count++; return 'confirmed'; }), 'blocked');
  assert.equal(count, 1);
  assert.equal(gate.status, 'unknown');
});
test('network failure is UNKNOWN and does not retry, mutate key or signal success', async () => {
  const gate = createCheckoutOnce();
  assert.equal(await gate.send(async () => { throw new Error('timeout after remote commit?'); }), 'unknown');
  assert.equal(gate.status, 'unknown');
  assert.equal(await gate.send(async () => 'confirmed'), 'blocked');
});
test('two concurrent clicks execute exactly one checkout request', async () => {
  const gate = createCheckoutOnce();
  let finish;
  let calls = 0;
  const first = gate.send(() => { calls++; return new Promise((resolve) => { finish = resolve; }); });
  const second = await gate.send(async () => { calls++; return 'confirmed'; });
  assert.equal(second, 'blocked');
  assert.equal(gate.status, 'sending');
  finish('confirmed');
  assert.equal(await first, 'confirmed');
  assert.equal(calls, 1);
  assert.equal(gate.status, 'confirmed');
});
