import { expect, test } from '@playwright/test';
import { accounts, sessionFor, unique } from './support';

/** Full C1 happy-path on a clean-stack seeded Seller/Admin; no real Checkout discount. */
test('seller publishes 20 vouchers, asks for 25, Admin approves, seller publishes 21st', async ({ browser }) => {
  test.setTimeout(240_000);
  const seller=await sessionFor(browser,accounts.seller);
  const admin=await sessionFor(browser,accounts.admin);
  try {
    const time=(millis:number)=>new Date(millis+7*3600000).toISOString().slice(0,16);
    const start=time(Date.now()+3600000);
    const end=time(Date.now()+2*86400000);
    const grantFrom=time(Date.now()-3600000);
    const grantUntil=time(Date.now()+3*86400000);
    const label='QA'+unique().toUpperCase().slice(0,6);
    await seller.goto('/seller/vouchers');
    await expect(seller.getByRole('heading',{name:'Voucher cửa hàng'})).toBeVisible();
    await expect(seller.getByText('0/20')).toBeVisible();

    async function createAndPublish(number:number){
      const code=label+number.toString().padStart(2,'0');
      await seller.getByRole('button',{name:'Tạo voucher'}).click();
      const form=seller.locator('form').filter({has:seller.getByRole('heading',{name:'Tạo voucher mới'})});
      await form.getByLabel(/Mã voucher/).fill(code);
      await form.getByLabel('Tên chương trình').fill('Test C1 '+code);
      await form.getByLabel(/Bắt đầu/).fill(start);
      await form.getByLabel(/Kết thúc/).fill(end);
      await form.getByRole('button',{name:'Lưu bản nháp'}).click();
      await expect(form).toHaveCount(0);
      const card=seller.locator('article').filter({hasText:code});
      await card.getByRole('button',{name:'Công bố'}).click();
      await expect(card.getByText('SCHEDULED')).toBeVisible();
    }
    for(let n=1;n<=20;n++) await createAndPublish(n);
    await expect(seller.getByText('20/20')).toBeVisible();

    const request=seller.locator('form').filter({has:seller.getByRole('heading',{name:'Yêu cầu mở rộng hạn mức'})});
    await request.getByLabel('Tổng slot xin cấp').fill('25');
    await request.getByLabel('Lý do').fill('Cần mở rộng xả kho cho nhiều sản phẩm');
    await request.getByLabel(/Từ ngày/).fill(grantFrom);
    await request.getByLabel(/Đến ngày/).fill(grantUntil);
    await request.getByRole('button',{name:'Gửi yêu cầu'}).click();
    await expect(seller.getByText('Đã gửi yêu cầu. Chờ Admin xét duyệt.')).toBeVisible();

    await admin.goto('/admin/voucher-limit-requests');
    await expect(admin.getByRole('heading',{name:'Xét duyệt nâng hạn mức voucher'})).toBeVisible();
    const pending=admin.locator('article').filter({hasText:'Cần mở rộng xả kho cho nhiều sản phẩm'});
    await expect(pending).toBeVisible();
    await pending.getByRole('button',{name:'Duyệt'}).click();
    await expect(pending).toHaveCount(0);

    await seller.reload();
    await expect(seller.getByText('20/25')).toBeVisible();
    await createAndPublish(21);
    await expect(seller.getByText('21/25')).toBeVisible();
  } finally {
    await seller.context().close();
    await admin.context().close();
  }
});

test('customer cannot open voucher administration',async({browser})=>{
  const customer=await sessionFor(browser,accounts.customer);
  try {
    for(const path of ['/seller/vouchers','/admin/voucher-limit-requests']){
      await customer.goto(path);
      await expect(customer.getByText('Tài khoản của bạn không có quyền truy cập trang này.')).toBeVisible();
    }
  } finally {await customer.context().close();}
});
