import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createServer } from 'vite';

let server, orderPayable;
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  ({ orderPayable } = await server.ssrLoadModule('/src/features/orders/orderPayable.ts'));
});
after(async () => { await server?.close(); });

const first = { status: 'PLACED', gross: 140000, grossExact: '140000.00' };
const second = { status: 'PLACED', gross: 275000, grossExact: '275000.00' };

test('C2 formats the sum of exact post-discount amounts once', () => {
  assert.equal(orderPayable([first, second]), '415.000 ₫');
});

test('C2 excludes cancelled and rejected SellerOrders from the payable amount', () => {
  assert.equal(orderPayable([first, { ...second, status: 'CANCELLED' }]), '140.000 ₫');
  assert.equal(orderPayable([{ ...first, status: 'REJECTED' }, second]), '275.000 ₫');
});

test('payable still accepts legacy Order snapshots without grossExact', () => {
  assert.equal(orderPayable([{ status: 'DELIVERED', gross: 150000 }, second]), '425.000 ₫');
});
