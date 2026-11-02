import { expect, test } from '@playwright/test';
import {
  allOperations, assertPlaced, balances, customerOrderIds, emptyCart, expectDelta,
  json, login, products, resource, shipping, uuid, type Submission,
} from './live-support';

test('real browser: two Stores, exact stock delta and same-key replay', async ({ page }) => {
  const token = await login(page);
  const me = await json<{ userId: string }>(await resource(page, token, '/api/auth/me'));
  const lines = await products(page, token);
  await emptyCart(page, token);
  const beforeStock = balances(lines);
  const beforeOrders = customerOrderIds(me.userId);
  for (const line of lines) {
    await page.goto(`/products/${line.productId}`);
    await page.getByLabel('Số lượng', { exact: true }).fill(String(line.quantity));
    const added = page.waitForResponse(r => new URL(r.url()).pathname === '/api/cart/items' && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Thêm vào giỏ hàng', exact: true }).click();
    expect((await added).status()).toBe(200);
  }
  const cart = await json<{ items: { productId: string; quantity: number }[]; groups: unknown[] }>(await resource(page, token, '/api/cart'));
  expect(cart.groups).toHaveLength(2);
  expect(cart.items.map(i => ({ productId: i.productId, quantity: i.quantity })).sort((a, b) => a.productId.localeCompare(b.productId)))
    .toEqual(lines.map(({ productId, quantity }) => ({ productId, quantity })).sort((a, b) => a.productId.localeCompare(b.productId)));
  await page.goto('/cart');
  await page.getByRole('link', { name: 'Tiến hành đặt hàng', exact: true }).click();
  await page.getByLabel('Người nhận', { exact: true }).fill(shipping.name);
  await page.getByLabel('Số điện thoại', { exact: true }).fill(shipping.phone);
  await page.getByLabel('Địa chỉ', { exact: true }).fill(shipping.address);
  const submitted = page.waitForRequest(r => new URL(r.url()).pathname === '/api/orders' && r.method() === 'POST');
  const completed = page.waitForResponse(r => new URL(r.url()).pathname === '/api/orders' && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Xác nhận đặt hàng COD', exact: true }).click();
  const original = await submitted;
  const key = await original.headerValue('Idempotency-Key');
  expect(key).toBeTruthy();
  const body = original.postDataJSON() as Submission;
  const response = await completed;
  expect(response.status()).toBe(200);
  const id = uuid((await response.json() as { orderId: string }).orderId);
  await expect(page).toHaveURL(new RegExp(`/orders/${id}$`));
  await assertPlaced(page, token, id, lines);
  expect(customerOrderIds(me.userId)).toEqual([...beforeOrders, id].sort());
  expectDelta(beforeStock, balances(lines), lines, -1, 1);
  const heldStock = balances(lines);
  const savedOperations = allOperations();
  // Replay the actual browser submission, including revision/shipping/key. No generated retry key.
  for (const replay of await Promise.all(Array.from({ length: 3 }, () => resource(page, token, '/api/orders', 'POST', body, key!)))) {
    expect((await json<{ orderId: string }>(replay)).orderId).toBe(id);
  }
  await assertPlaced(page, token, id, lines);
  expect(customerOrderIds(me.userId)).toEqual([...beforeOrders, id].sort());
  expect(balances(lines)).toEqual(heldStock);
  expect(allOperations()).toEqual(savedOperations); // Detect a new operation ID, not just the expected ID.
});
