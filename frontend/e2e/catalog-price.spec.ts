import { expect, test } from '@playwright/test';

const prices = async (page: import('@playwright/test').Page) =>
  (await page.locator('.product .price').allInnerTexts())
    .map((text) => Number(text.trim().split(/\s+/).at(-2)?.replace(/\./g, '')));

test('a shopper types a price range and presses Enter; both bounds are applied', async ({ page }) => {
  await page.goto('/products');
  await page.getByLabel('Giá từ').fill('200000');
  await page.getByLabel('đến').fill('400000');
  await page.getByLabel('đến').press('Enter');

  await expect(page).toHaveURL(/minPrice=200000/);
  await expect(page).toHaveURL(/maxPrice=400000/);
  // The list keeps the previous results on screen while the filtered page loads, so wait for it to settle.
  await expect.poll(async () => {
    const shown = await prices(page);
    return shown.length > 0 && shown.every((price) => price >= 200000 && price <= 400000);
  }).toBe(true);

  // Clearing "đến" with the button lifts the upper bound again.
  await page.getByLabel('đến').fill('');
  await page.getByRole('button', { name: 'Áp dụng' }).click();
  await expect(page).not.toHaveURL(/maxPrice=/);
  await expect(page).toHaveURL(/minPrice=200000/);

  // One button clears every filter and empties the price fields.
  await page.getByRole('button', { name: 'Xóa bộ lọc' }).click();
  await expect(page).not.toHaveURL(/minPrice=/);
  await expect(page.getByLabel('Giá từ')).toHaveValue('');
  await expect(page.getByRole('button', { name: 'Xóa bộ lọc' })).toHaveCount(0);
});
