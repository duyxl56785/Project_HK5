import { expect, test } from '@playwright/test';
import type { APIRequestContext } from '@playwright/test';
import { accounts, env, sessionFor } from './support';

const stock = async (request: APIRequestContext, id: string): Promise<number> =>
  (await (await request.get(`/api/catalog/products/${id}`)).json()).available;

test('one customer order splits into two store orders and reserves stock in both', async ({ browser }) => {
  const customer = await sessionFor(browser, accounts.customer);
  const catalog = await (await customer.request.get('/api/catalog/products?size=50')).json();
  const from = (store: string) => catalog.items.find((item: { store: { name: string } }) => item.store.name === store);
  const first = from('Tiệm Áo Sài Gòn');
  const second = from('Nhà Xinh');
  expect(first && second).toBeTruthy();
  const before = [await stock(customer.request, first.id), await stock(customer.request, second.id)];

  for (const product of [first, second]) {
    await customer.goto(`/products/${product.id}`);
    const added = customer.waitForResponse((r) => r.url().endsWith('/api/cart/items') && r.request().method() === 'POST');
    await customer.getByRole('button', { name: 'Thêm vào giỏ hàng' }).click();
    expect((await added).ok()).toBeTruthy();
  }

  await customer.goto('/cart');
  await expect(customer.getByText(`Cửa hàng: ${first.store.name}`)).toBeVisible();
  await expect(customer.getByText(`Cửa hàng: ${second.store.name}`)).toBeVisible();
  await customer.getByRole('link', { name: 'Tiến hành đặt hàng' }).click();
  await customer.getByLabel('Người nhận').fill('Khách hai cửa hàng');
  await customer.getByLabel('Số điện thoại').fill('0901234567');
  await customer.getByLabel('Địa chỉ', { exact: true }).fill('1 Lê Lợi, Quận 1');
  await customer.getByRole('button', { name: /đặt hàng/i }).click();
  await expect(customer).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);

  // One order URL, so both cards are store orders of the same customer order.
  const cards = customer.locator('article');
  await expect(cards).toHaveCount(2);
  await expect(cards.filter({ hasText: first.store.name })).toContainText(first.name);
  await expect(cards.filter({ hasText: second.store.name })).toContainText(second.name);

  await expect(async () => {
    expect([await stock(customer.request, first.id), await stock(customer.request, second.id)]).toEqual(before.map((n) => n - 1));
  }).toPass({ timeout: 30_000 });

  // Each seller sees only their own part of the order.
  const code = customer.url().split('/').pop()!.slice(0, 8).toUpperCase();
  const sellerCard = async (email: string) => {
    const seller = await sessionFor(browser, { email, password: env.SEED_PASSWORD });
    await seller.goto('/seller/orders');
    return { seller, card: seller.locator('article').filter({ hasText: `Đơn #${code}` }) };
  };
  const one = await sellerCard(accounts.seller.email);
  await expect(one.card).toContainText(first.name);
  await expect(one.card).not.toContainText(second.name);
  const two = await sellerCard('seller2@hk5.local');
  await expect(two.card).toContainText(second.name);
  await expect(two.card).not.toContainText(first.name);
  await one.seller.context().close();
  await two.seller.context().close();
});
