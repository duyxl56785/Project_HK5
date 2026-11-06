import { test, expect } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';

test.afterEach(async ({page}, info) => {
  const dir = process.env.SOOPI_QA_SCREENSHOT_DIR;
  if (!dir) throw new Error('Thiếu thư mục bằng chứng riêng cho lần chạy');
  await mkdir(dir, {recursive:true});
  const label = info.title.replace(/[^a-zA-Z0-9-]/g,'-');
  await page.screenshot({path:path.join(dir, `${label}-${info.status}.png`),fullPage:true});
});

test('dashboard-hien-thi-suite-va-gates', async ({page}) => {
  await page.goto('/');
  await expect(page.getByRole('heading',{name:'Soopi TV5 QA Tester'})).toBeVisible();
  await expect(page.getByRole('checkbox',{name:/F15-RULES/})).toBeVisible();
  await expect(page.getByRole('checkbox',{name:/FINANCE-PRODUCTION/})).toBeVisible();
  await expect(page.getByText('BLOCKED_OWNER: Finance/Return production policy chưa được ký.')).toBeVisible();
});

test('dashboard-chi-goi-suite-allowlist', async ({page}) => {
  await page.goto('/');
  await page.getByRole('checkbox',{name:/F15-RULES/}).check();
  const history=await (await page.request.get('/api/history')).json();
  if(history.some(r=>r.status==='RUNNING'))await expect(page.getByRole('button',{name:'Run Selected'})).toBeDisabled();
  else await expect(page.getByRole('button',{name:'Run Selected'})).toBeEnabled();
  await expect(page.getByRole('button',{name:'Run All Safe Tests'})).toBeVisible();
});
test('dashboard-chan-doan-tieng-viet-khong-thuc-thi-html',async({page})=>{
 await page.route('**/api/history',route=>route.fulfill({json:[{id:'fixture-display-only',sha:'a'.repeat(40),timestamp:'2026-11-06T00:00:00Z',status:'FAIL',environment:{fixture:true},suites:[{id:'WEBSITE-SMOKE',status:'FAIL',expected:'HTTP200',actual:'ECONNREFUSED',bugs:[{id:'BUG-display-fixture',title:'Không kết nối được API local',module:'Website localhost',endpoint:'/api/stores',code:'ECONNREFUSED',type:'ENVIRONMENT_FAILURE',severity:'P1',verifiedCause:null,suspectedCause:'<img src=x onerror=window.soopiXss=1>',remediation:'Kiểm tra gateway 127.0.0.1:18080',owner:'TV5 QA',stackTrace:'ECONNREFUSED'}]}]}]}));
 await page.goto('/');await expect(page.getByRole('heading',{name:'Không kết nối được API local'})).toBeVisible();await expect(page.getByText(/API: \/api\/stores/)).toBeVisible();await expect(page.getByText('Chưa đủ bằng chứng')).toBeVisible();expect(await page.evaluate(()=>window.soopiXss)).toBeUndefined();
 await page.setViewportSize({width:375,height:812});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

// Opt-in diagnostic proving that a real Playwright assertion failure also saves a screenshot.
// This is a tester self-check, not a product defect and never part of Run Selected.
if (process.env.SOOPI_QA_VERIFY_FAILURE === '1') test('diagnostic-co-y-fail-de-kiem-tra-anh', async ({page}) => {
  await page.goto('/');
  await expect(page.getByRole('heading',{name:'Không tồn tại'})).toBeVisible();
});
