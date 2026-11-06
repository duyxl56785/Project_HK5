import {test,expect} from '@playwright/test';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {redact} from '../server/diagnostics.mjs';
const observations=new WeakMap();
test.beforeEach(async({page})=>{
 const events=[];observations.set(page,events);
 page.on('console',m=>{if(m.type()==='error')events.push({kind:'console',message:redact(m.text())});});
 page.on('pageerror',e=>events.push({kind:'pageerror',message:redact(e.message)}));
 page.on('requestfailed',r=>events.push({kind:'network',endpoint:new URL(r.url()).pathname,code:redact(r.failure()?.errorText)}));
 page.on('response',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))events.push({kind:'http',endpoint:new URL(r.url()).pathname,status:r.status()});});
});
test.afterEach(async({page},info)=>{
 const dir=process.env.SOOPI_QA_SCREENSHOT_DIR;if(!dir)throw Error('Thiếu evidence directory');await mkdir(dir,{recursive:true});
 const label=info.title.replace(/[^a-zA-Z0-9-]/g,'-');
 await page.screenshot({path:path.join(dir,`${label}-${info.status}.png`),fullPage:true,mask:[page.locator('input'),page.getByText(/@/)]});
 const events=observations.get(page)??[];await writeFile(path.join(dir,`${label}.json`),JSON.stringify(events,null,2));
 for(const item of events)if(item.kind==='network'||item.kind==='pageerror'||item.status>=500)console.log('SOOPI_OBSERVATION '+JSON.stringify(item));
});
test('public-catalog-stores-va-responsive',async({page})=>{
 for(const endpoint of ['/api/stores','/api/catalog/products']){const r=await page.request.get(endpoint);expect(r.status()).toBe(200);}
 await page.goto('/');await expect(page.locator('main')).toBeVisible();
 await page.setViewportSize({width:375,height:812});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
test('register-login-logout-validation-va-duplicate',async({page})=>{
 const email=`soopi-${Date.now()}@hk5.local`;const password=`Soopi-${crypto.randomUUID()}!`;
 await page.goto('/register');await page.getByRole('button',{name:'Đăng ký',exact:true}).click();await expect(page.getByText('Email không hợp lệ')).toBeVisible();
 await page.getByLabel('Email').fill(email);await page.getByLabel('Mật khẩu').fill(password);
 await page.getByRole('button',{name:'Đăng ký',exact:true}).click();await expect(page.getByRole('button',{name:'Đăng xuất'})).toBeVisible();
 const headers={'X-CSRF-Protection':'1',Origin:'http://localhost:5173'};
 const duplicate=await page.request.post('/api/auth/register',{headers,data:{email,password}});expect(duplicate.status()).toBe(409);
 await page.getByRole('button',{name:'Đăng xuất'}).click();await expect(page.getByRole('link',{name:'Đăng nhập',exact:true})).toBeVisible();
 await page.goto('/login');await page.getByLabel('Email').fill(email);await page.getByLabel('Mật khẩu').fill(password);await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await expect(page.getByRole('button',{name:'Đăng xuất'})).toBeVisible();
 await page.goto('/admin/finance');await expect(page.getByText('Tài khoản của bạn không có quyền truy cập trang này.')).toBeVisible();
 const invalid=await page.request.get('/api/orders',{headers:{Authorization:'Bearer invalid-local-test'}});expect(invalid.status()).toBe(401);
});
test('dang-nhap-sai-thong-bao-loi-va-xss-safe',async({page})=>{
 await page.goto('/login');await page.getByLabel('Email').fill(`absent-${Date.now()}@hk5.local`);await page.getByLabel('Mật khẩu').fill('Invalid-local-password-123!');await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await expect(page.getByRole('alert')).toBeVisible();
 await page.goto('/?q=%3Cscript%3Ewindow.soopiInjected%3D1%3C%2Fscript%3E');expect(await page.evaluate(()=>window.soopiInjected)).toBeUndefined();
});
