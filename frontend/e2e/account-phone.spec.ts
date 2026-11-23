import { expect, test } from '@playwright/test';
import { accounts, sessionFor, unique } from './support';

test('a customer picks the calling code and types the phone number as at home', async ({ browser }) => {
  const page = await sessionFor(browser, accounts.customer);
  await page.goto('/account');
  const profile = page.locator('form').filter({ has: page.getByRole('heading', { name: 'Hồ sơ' }) });
  const code = profile.getByLabel('Mã quốc gia');
  const number = profile.getByRole('textbox', { name: 'Số điện thoại' });
  await expect(code).toHaveValue(/\+\d+/);
  const before = { code: await code.inputValue(), number: await number.inputValue() };

  await code.selectOption('+84');
  await number.fill('abc');
  await profile.getByRole('button', { name: 'Lưu hồ sơ' }).click();
  await expect(profile.getByRole('alert')).toHaveText('Số điện thoại không hợp lệ');

  await number.fill('0901 234 567');
  await profile.getByRole('button', { name: 'Lưu hồ sơ' }).click();
  await expect(profile.getByRole('status')).toHaveText('Đã lưu.');
  await page.reload();
  await expect(code).toHaveValue('+84');
  await expect(number).toHaveValue('901234567');

  const name = `Người nhận ${unique()}`;
  await page.getByRole('button', { name: 'Thêm địa chỉ' }).click();
  await page.getByLabel('Người nhận').fill(name);
  await page.getByLabel('Mã quốc gia').last().selectOption('+65');
  await page.getByRole('textbox', { name: 'Số điện thoại' }).last().fill('8123 4567');
  await page.getByLabel('Địa chỉ', { exact: true }).fill('1 Orchard Road');
  await page.getByLabel('Tỉnh/Thành phố').fill('Singapore');
  await page.getByRole('button', { name: 'Lưu địa chỉ' }).click();
  const row = page.getByRole('listitem').filter({ hasText: name });
  await expect(row).toContainText('+6581234567');
  await row.getByRole('button', { name: 'Xóa' }).click();
  await expect(row).toHaveCount(0);

  await code.selectOption(before.code);
  await number.fill(before.number);
  await profile.getByRole('button', { name: 'Lưu hồ sơ' }).click();
  await expect(profile.getByRole('status')).toHaveText('Đã lưu.');
});
