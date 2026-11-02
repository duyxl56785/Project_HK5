import { expect, test } from '@playwright/test';
import {
  allOperations, assertPlaced, balances, detail, expectDelta, json, login, operations,
  placeOverHttp, products, required, resource,
} from './live-support';

test('real HTTP: customer cancellation and Seller rejection each release exact held lines once', async ({ page, browser }) => {
  const customer = await login(page);
  const lines = await products(page, customer);
  const initial = balances(lines);
  const placed = await placeOverHttp(page, customer, lines);
  const order = await assertPlaced(page, customer, placed.id, lines);
  expectDelta(initial, balances(lines), lines, -1, 1);
  const a = order.storeOrders.find(o => o.storeId === lines[0].storeId)!;
  const b = order.storeOrders.find(o => o.storeId === lines[1].storeId)!;
  await json(await resource(page, customer, `/api/orders/${placed.id}/store-orders/${a.id}/cancel`, 'POST', { reason: 'TV3 isolated cancellation' }));
  const sellerPage = await (await browser.newContext()).newPage();
  try {
    const seller = await login(sellerPage, 'SELLER');
    const managed = await json<{ id: string }>(await resource(sellerPage, seller, '/api/stores/me'));
    expect(managed.id).toBe(lines[1].storeId);
    await json(await resource(sellerPage, seller, `/api/seller/orders/${b.id}/reject`, 'POST', { reason: 'TV3 isolated rejection' }));
    await expect.poll(() => balances(lines), { timeout: 30_000 }).toEqual(initial);
    const ops = operations(placed.id, [a.id, b.id]);
    expect(ops.filter(o => o.type === 'RELEASE').map(o => o.id).sort())
      .toEqual([`seller-order-${a.id}-release`, `seller-order-${b.id}-release`].sort());
    expect(ops.filter(o => o.type === 'CONSUME')).toHaveLength(0);
    const all = allOperations();
    const cancelledAgain = await resource(page, customer, `/api/orders/${placed.id}/store-orders/${a.id}/cancel`, 'POST', { reason: 'same terminal action' });
    expect([200, 409]).toContain(cancelledAgain.status());
    expect([200, 409]).toContain((await resource(sellerPage, seller, `/api/seller/orders/${b.id}/reject`, 'POST', { reason: 'same terminal action' })).status());
    expect(balances(lines)).toEqual(initial);
    expect(operations(placed.id, [a.id, b.id])).toEqual(ops);
    expect(allOperations()).toEqual(all);
  } finally { await sellerPage.context().close(); }
});

test('TV4 contract gate: CONSUME follows committed DISPATCHED, never waits for DELIVERED/COD', async ({ page, browser }) => {
  // Require an explicit contract reference; do not invent a versioned replacement for TV4.
  required('TV3_TV4_CONTRACT_REFERENCE');
  const customer = await login(page);
  const lines = await products(page, customer);
  const initial = balances(lines);
  const placed = await placeOverHttp(page, customer, lines);
  const order = await assertPlaced(page, customer, placed.id, lines);
  const b = order.storeOrders.find(o => o.storeId === lines[1].storeId)!;
  const sellerPage = await (await browser.newContext()).newPage();
  try {
    const seller = await login(sellerPage, 'SELLER');
    expect((await json<{ id: string }>(await resource(sellerPage, seller, '/api/stores/me'))).id).toBe(lines[1].storeId);
    for (const action of ['accept', 'prepare', 'ready']) {
      await json(await resource(sellerPage, seller, `/api/seller/orders/${b.id}/${action}`, 'POST', {}));
    }
    expect(operations(placed.id, [b.id]).filter(o => o.type === 'CONSUME')).toHaveLength(0);
    await json(await resource(sellerPage, seller, `/api/seller/orders/${b.id}/dispatch`, 'POST', {}));
    expect((await detail(page, customer, placed.id)).storeOrders.find(o => o.id === b.id)!.status).toBe('DISPATCHED');
    await expect.poll(() => operations(placed.id, [b.id]).filter(o => o.type === 'CONSUME'), { timeout: 30_000 })
      .toEqual([{ id: `seller-order-${b.id}-consume`, type: 'CONSUME', hash: expect.any(String) }]);
    const afterDispatch = balances(lines);
    const first = initial.find(s => s.sku === lines[0].sku)!;
    const second = initial.find(s => s.sku === lines[1].sku)!;
    expect(afterDispatch).toContainEqual({ sku: first.sku, available: first.available - lines[0].quantity, reserved: first.reserved + lines[0].quantity });
    expect(afterDispatch).toContainEqual({ sku: second.sku, available: second.available - lines[1].quantity, reserved: second.reserved });
    const ops = operations(placed.id, [b.id]);
    await json(await resource(sellerPage, seller, `/api/seller/orders/${b.id}/deliver`, 'POST', {}));
    expect(operations(placed.id, [b.id])).toEqual(ops);
    expect(balances(lines)).toEqual(afterDispatch);
  } finally { await sellerPage.context().close(); }
});
