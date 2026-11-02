import { expect, test } from '@playwright/test';
import { accounts, sessionFor } from './support';

// Runs after order-journey, which leaves a visible review with a unique comment.
test('an admin hides a review with a reason and shows it again', async ({ browser, request, baseURL }) => {
  const bearer = async (account: { email: string; password: string }) => {
    const login = await request.post('/api/auth/login', { headers: { 'X-CSRF-Protection': '1', Origin: baseURL! }, data: account });
    return { Authorization: `Bearer ${(await login.json()).accessToken}` };
  };
  const adminAuth = await bearer(accounts.admin);
  const admin = await sessionFor(browser, accounts.admin);
  await admin.goto('/admin/reviews');
  const visible = admin.getByRole('row').filter({ has: admin.getByRole('button', { name: 'Ẩn', exact: true }) }).first();
  const comment = await visible.getByRole('cell').nth(2).innerText();
  const row = admin.getByRole('row').filter({ hasText: comment }).first();

  await expect(row.getByRole('button', { name: 'Ẩn', exact: true })).toBeDisabled();
  const reviews = await (await request.get('/api/admin/reviews', { headers: adminAuth })).json();
  const review = reviews.find((r: { comment: string }) => r.comment === comment);
  const refused = await request.post(`/api/admin/reviews/${review.id}/hide`, { headers: adminAuth });
  expect(refused.status()).toBe(400);
  expect((await refused.json()).code).toBe('REVIEW_HIDE_REASON_REQUIRED');
  await row.getByLabel('Lý do').fill('Nội dung quảng cáo');
  await row.getByRole('button', { name: 'Ẩn', exact: true }).click();
  await expect(row.getByText('Lý do ẩn: Nội dung quảng cáo')).toBeVisible();
  // The reason is an Admin note, so the review's author does not get it back.
  const mine = await (await request.get('/api/reviews/mine', { headers: await bearer(accounts.customer) })).json();
  expect(mine.find((r: { id: string }) => r.id === review.id).hiddenReason).toBeNull();

  await row.getByRole('button', { name: 'Hiện', exact: true }).click();
  await expect(row.getByRole('button', { name: 'Ẩn', exact: true })).toBeVisible();
  await expect(row.getByText(/Lý do ẩn/)).toHaveCount(0);
  await admin.context().close();
});
