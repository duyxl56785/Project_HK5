import { expect, test } from '@playwright/test';
import {
  allOperations, balances, customerOrderIds, faultFixture, json, login, orderProof, resource,
} from './live-support';

for (const kind of ['claim-conflict', 'timeout'] as const) {
  test(`real HTTP: prepared ${kind} attempt reuses saved identity without releasing Cart`, async ({ page }) => {
    // Fault creation is an operator-controlled prerequisite, not a mocked response or invented API.
    const fixture = faultFixture(kind);
    const token = await login(page);
    const me = await json<{ userId: string }>(await resource(page, token, '/api/auth/me'));
    const before = orderProof(fixture.attemptId);
    expect(before.customerId).toBe(me.userId);
    expect(before.orders).toBe(0);
    expect(before.state).toBe('NEEDS_RECONCILIATION');
    expect(before.journals).toHaveLength(1); // Current #135 bypasses T1; this remains a real acceptance gate.
    expect(before.journals[0]).toMatchObject({ id: fixture.operationId, type: 'RESERVE' });
    expect([...before.journals[0].lines].sort((a, b) => a.sku.localeCompare(b.sku)))
      .toEqual(fixture.lines.map(({ sku, quantity }) => ({ sku, quantity })).sort((a, b) => a.sku.localeCompare(b.sku)));
    const beforeCart = await json<{ claimed: boolean; customerId: string }>(await resource(page, token, '/api/cart'));
    expect(beforeCart.claimed).toBe(true);
    expect(beforeCart.customerId).toBe(me.userId);
    const beforeStock = balances(fixture.lines);
    const beforeOps = allOperations();
    const beforeOrders = customerOrderIds(me.userId);
    for (let retry = 0; retry < 2; retry++) {
      const response = await resource(page, token, '/api/orders', 'POST', fixture.body, fixture.key);
      expect([409, 503]).toContain(response.status());
      const error = await response.json() as { message: string };
      expect(error.message).toMatch(/CHECKOUT_REQUIRES_RECONCILIATION|CHECKOUT_OUTCOME_UNKNOWN_RECONCILIATION_REQUIRED/);
    }
    expect(await json(await resource(page, token, '/api/cart'))).toEqual(beforeCart);
    expect(orderProof(fixture.attemptId)).toEqual(before);
    expect(customerOrderIds(me.userId)).toEqual(beforeOrders);
    expect(balances(fixture.lines)).toEqual(beforeStock);
    expect(allOperations()).toEqual(beforeOps);
  });
}
