import {test,expect} from '@playwright/test';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {redact} from '../server/diagnostics.mjs';
import {readFile} from 'node:fs/promises';
import {createHmac} from 'node:crypto';
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
test('cart-hai-store-quantity-remove-va-jwt-het-han',async({page})=>{
 const email=`soopi-cart-${Date.now()}@hk5.local`,password=`Soopi-${crypto.randomUUID()}!`;
 const csrf={'X-CSRF-Protection':'1',Origin:'http://localhost:5173'};
 const call=async(method,endpoint,options={})=>{const r=await page.request[method](endpoint,options);observations.get(page).push({kind:'http',endpoint,status:r.status()});return r;};
 expect((await call('post','/api/auth/register',{headers:csrf,data:{email,password}})).status()).toBe(201);
 const login=await call('post','/api/auth/login',{headers:csrf,data:{email,password}});expect(login.status()).toBe(200);const {accessToken}=await login.json();
 const auth={...csrf,Authorization:`Bearer ${accessToken}`};
 expect((await call('get','/api/admin/finance',{headers:auth})).status()).toBe(403);
 const parts=accessToken.split('.');const claims=JSON.parse(Buffer.from(parts[1],'base64url'));claims.exp=Math.floor(Date.now()/1000)-3600;claims.iat=claims.exp-60;
 const values=Object.fromEntries((await readFile(new URL('../../../.env.demo',import.meta.url),'utf8')).split('\n').filter(l=>l.includes('=')&&!l.startsWith('#')).map(l=>[l.slice(0,l.indexOf('=')),l.slice(l.indexOf('=')+1).trim()]));
 const unsigned=parts[0]+'.'+Buffer.from(JSON.stringify(claims)).toString('base64url');const expired=unsigned+'.'+createHmac('sha256',Buffer.from(values.JWT_HS256_CURRENT_KEY_B64,'base64')).update(unsigned).digest('base64url');
 expect((await call('get','/api/orders',{headers:{Authorization:`Bearer ${expired}`}})).status()).toBe(401);
 const catalog=await (await call('get','/api/catalog/products?size=50')).json();const chosen=[];
 for(const item of catalog.items)if(!chosen.some(p=>p.store.id===item.store.id))chosen.push(item);
 expect(chosen.length).toBeGreaterThanOrEqual(2);
 for(const p of chosen.slice(0,2))expect((await call('post','/api/cart/items',{headers:auth,data:{productId:p.id,quantity:1}})).status()).toBe(200);
 const changed=await call('patch',`/api/cart/items/${chosen[0].id}`,{headers:auth,data:{quantity:2}});expect(changed.status()).toBe(200);
 expect((await call('patch',`/api/cart/items/${chosen[0].id}`,{headers:auth,data:{quantity:0}})).status()).toBe(400);
 expect((await call('delete',`/api/cart/items/${chosen[0].id}`,{headers:auth})).status()).toBe(200);
 expect((await call('delete','/api/cart',{headers:auth})).status()).toBe(200);
 await page.goto('/login');await page.getByLabel('Email').fill(email);await page.getByLabel('Mật khẩu').fill(password);await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await expect(page.getByRole('button',{name:'Đăng xuất'})).toBeVisible();await page.goto('/cart');await expect(page.getByText('Giỏ hàng hiện đang trống')).toBeVisible();
});
