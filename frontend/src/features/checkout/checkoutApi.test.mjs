import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createServer } from 'vite';

let vite, classify, ApiError;
before(async () => {
  vite = await createServer({ server: { middlewareMode: true, hmr: false, watch: null }, optimizeDeps: { noDiscovery: true } });
  ({ classify } = await vite.ssrLoadModule('/src/features/checkout/checkoutApi.ts'));
  ({ ApiError } = await vite.ssrLoadModule('/src/shared/api.ts'));
});
after(() => vite?.close());

const conflict = (message) => new ApiError(409, 'BUSINESS_CONFLICT', message);

test('network failures and server errors never claim an outcome', () => {
  assert.deepEqual(classify(new TypeError('fetch failed')), { status: 'unknown' });
  assert.deepEqual(classify(new ApiError(503, 'REQUEST_REJECTED', 'CHECKOUT_OUTCOME_UNKNOWN_RECONCILIATION_REQUIRED')), { status: 'unknown' });
});

test('in-progress and reconciliation conflicts stay unknown', () => {
  assert.deepEqual(classify(new ApiError(409, 'REQUEST_REJECTED', 'CHECKOUT_REQUIRES_RECONCILIATION')), { status: 'unknown' });
  assert.deepEqual(classify(new ApiError(409, 'REQUEST_REJECTED', 'CHECKOUT_IN_PROGRESS_RETRY_LATER')), { status: 'unknown' });
});

test('definite conflicts map to the reason the customer can act on', () => {
  assert.equal(classify(conflict('Not enough stock for SKU-1')).reason, 'OUT_OF_STOCK');
  assert.equal(classify(conflict('Cart revision changed')).reason, 'CART_CHANGED');
  assert.equal(classify(conflict('Store is closed')).reason, 'STORE_UNAVAILABLE');
  assert.equal(classify(conflict('FINANCE_POLICY_NOT_APPROVED')).reason, 'CHECKOUT_PAUSED');
  assert.equal(classify(new ApiError(401, 'UNAUTHENTICATED', 'Sign in')).reason, 'UNAUTHORIZED');
});
