import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { accounts, sessionFor } from './support';

const addToCart = async (customer: Page, productId: string) => {
  await customer.goto(`/products/${productId}`);
  const added = customer.waitForResponse((r) => r.url().endsWith('/api/cart/items') && r.request().method() === 'POST');
  await customer.getByRole('button', { name: 'Thêm vào giỏ hàng' }).click();
  expect((await added).ok()).toBeTruthy();
};

const clearCart = async (customer: Page, answer: 'accept' | 'dismiss') => {
  await customer.goto('/cart');
  customer.once('dialog', (dialog) => void dialog[answer]());
  await customer.getByRole('button', { name: 'Xóa giỏ hàng' }).click();
};

test('clearing the Cart asks first, and a closed Store is named on its own line', async ({ browser }) => {
  const customer = await sessionFor(browser, accounts.customer);
  const catalog = await (await customer.request.get('/api/catalog/products?size=50')).json();
  const product = catalog.items.find((item: { store: { name: string } }) => item.store.name === 'Tiệm Áo Sài Gòn');
  expect(product).toBeTruthy();

  await addToCart(customer, product.id);
  await clearCart(customer, 'dismiss');
  await expect(customer.getByText(product.name)).toBeVisible();
  await clearCart(customer, 'accept');
  await expect(customer.getByText('Giỏ hàng hiện đang trống')).toBeVisible();

  // A closed Store refuses new Cart lines, so the line goes in before the Store closes.
  await addToCart(customer, product.id);
  const seller = await sessionFor(browser, accounts.seller);
  await seller.goto('/seller');
  await seller.getByRole('button', { name: 'Đóng cửa hàng' }).click();
  await seller.getByRole('button', { name: 'Mở cửa hàng' }).waitFor();
  try {
    await customer.goto('/checkout');
    await customer.getByLabel('Người nhận').fill('Khách E2E');
    await customer.getByLabel('Số điện thoại').fill('0901234567');
    await customer.getByLabel('Địa chỉ', { exact: true }).fill('1 Lê Lợi, Quận 1');
    await customer.getByRole('button', { name: /đặt hàng/i }).click();
    const refusal = customer.getByRole('alert');
    await expect(refusal).toContainText(product.name);
    await expect(refusal).toContainText('cửa hàng đang đóng hoặc tạm khóa');
    await expect(customer).toHaveURL(/\/checkout$/);
  } finally {
    await seller.getByRole('button', { name: 'Mở cửa hàng' }).click();
    await seller.getByRole('button', { name: 'Đóng cửa hàng' }).waitFor();
    await seller.context().close();
  }
  await clearCart(customer, 'accept');
  await expect(customer.getByText('Giỏ hàng hiện đang trống')).toBeVisible();
  await customer.context().close();
});
