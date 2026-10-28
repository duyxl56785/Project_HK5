import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFulfillmentBoard } from './fulfillmentModel.ts';

const storeId = '11111111-1111-4111-8111-111111111111';
const order = { id: '22222222-2222-4222-8222-222222222222', parentOrderId: '33333333-3333-4333-8333-333333333333', storeId, status: 'PLACED', codStatus: 'UNRECORDED', createdAt: '2026-10-28T10:00:00Z', actions: [{ id: 'ACCEPT', label: 'Nhận đơn' }] };

test('accepts a complete Store-scoped board projection', () => {
  const board = readFulfillmentBoard({ storeId, orders: [order] });
  assert.equal(board?.orders.length, 1);
  assert.equal(board?.orders[0].actions[0].id, 'ACCEPT');
});

test('rejects a mixed Store collection without returning partial orders', () => {
  assert.equal(readFulfillmentBoard({ storeId, orders: [order, { ...order, id: '44444444-4444-4444-8444-444444444444', storeId: '55555555-5555-4555-8555-555555555555' }] }), null);
});

test('rejects duplicate order IDs, malformed timestamps and duplicate command IDs', () => {
  assert.equal(readFulfillmentBoard({ storeId, orders: [order, { ...order }] }), null);
  assert.equal(readFulfillmentBoard({ storeId, orders: [{ ...order, createdAt: 'not-a-date' }] }), null);
  assert.equal(readFulfillmentBoard({ storeId, orders: [{ ...order, actions: [{ id: 'ACCEPT', label: 'Nhận đơn' }, { id: 'ACCEPT', label: 'Lặp' }] }] }), null);
});
