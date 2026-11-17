
import { execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, test, type Browser, type Page, type Response } from '@playwright/test';

test.use({ trace: 'off', video: 'off', screenshot: 'off' });
test.describe.configure({ retries: 0 });

const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
type Product = { id: string; name: string; sku: string; available: number; store: { id: string; name: string } };
type Cart = { claimed: boolean; revision: number; items: { productId: string; storeId: string; quantity: number }[] };
type Child = { id: string; orderId: string; storeId: string; storeName: string; items: { productId: string; sku: string; quantity: number }[] };
type Order = { id: string; paymentMethod: string; storeOrders: Child[] };
type Balance = { sku: string; available: number; reserved: number };
type Session = { page: Page; token: string; userId: string };
const storeNames = ['Tiệm Áo Sài Gòn', 'Nhà Xinh'] as const;
const sqlLiteral = (value: string) => "'" + value.replaceAll("'", "''") + "'";
const repo = resolve(import.meta.dirname, '../..');

function setup() {
  const sha = process.env.TV3_REHEARSAL_SHA ?? '';
  const project = process.env.TV3_REHEARSAL_PROJECT ?? '';
  const evidence = process.env.TV3_REHEARSAL_EVIDENCE;
  if (!/^[a-f0-9]{40}$/.test(sha) || project !== 'hk5-tv3-rehearsal' || !evidence) {
    throw new Error('Start this test through Run-TV3DemoRehearsal.ps1 with the approved isolated project.');
  }
  const actual = execFileSync('git', ['rev-parse','HEAD'], { cwd: repo, encoding:'utf8', stdio:['ignore','pipe','ignore'] }).trim();
  expect(actual, 'rehearsal source SHA').toBe(sha);
  const url = new URL(process.env.E2E_BASE_URL ?? '');
  expect(url.protocol === 'http:' && ['127.0.0.1','localhost'].includes(url.hostname) &&
    !url.username && !url.password && url.pathname === '/' && !url.search && !url.hash).toBe(true);
  return { sha, project, evidence, origin: url.origin };
}

function watch(page: Page, origin: string, path: string, method: string) {
  return page.waitForResponse((response: Response) => {
    const url = new URL(response.url());
    return url.origin === origin && url.pathname === path && response.request().method() === method;
  });
}

async function http(origin: string, path: string, token?: string, post?: { key: string; body: string }) {
  const headers = new Headers({ Origin: origin });
  if (token) headers.set('Authorization', 'Bearer ' + token);
  if (post) {
    headers.set('Content-Type', 'application/json');
    headers.set('Idempotency-Key', post.key);
  }
  try {
    return await fetch(origin + path, { headers, method: post ? 'POST' : 'GET', body: post?.body,
      redirect: 'error', signal: AbortSignal.timeout(15_000) });
  } catch {
    throw new Error('Gateway HTTP request unavailable');
  }
}

async function data<T>(origin: string, path: string, token?: string): Promise<T> {
  const response = await http(origin, path, token);
  expect(response.status, 'Gateway GET status').toBe(200);
  return response.json() as Promise<T>;
}

function query<T>(project: string, database: 'order_db' | 'inventory_db', select: string): T {
  try {
    const output = execFileSync('docker', ['exec','-i',project + '-postgres-1','psql','-X','-qAt','-v','ON_ERROR_STOP=1',
      '-U','hk5','-d',database], { input:'BEGIN READ ONLY;\n' + select + ';\nROLLBACK;\n',
      encoding:'utf8', timeout:12_000, stdio:['pipe','pipe','pipe'] });
    return JSON.parse(output.trim()) as T;
  } catch {
    throw new Error('Read-only PostgreSQL oracle unavailable for ' + database);
  }
}

test('TV3 rehearsal: two-store COD, ownership, replay and stock', async ({ browser }) => {
  test.skip(!process.env.TV3_REHEARSAL_EVIDENCE, 'Requires the isolated-stack runner');
  test.setTimeout(180_000);
  const { sha, project, evidence, origin } = setup();
  const usePostgres = process.env.TV3_REHEARSAL_PG_READ_ONLY === '1';
  const { logIn, accounts, env } = await import('./support');
  const contexts: Awaited<ReturnType<Browser['newContext']>>[] = [];
  const login = async (account: { email: string; password: string }, role: 'CUSTOMER' | 'SELLER'): Promise<Session> => {
    expect(Boolean(account.password), 'demo password configured').toBe(true);
    const context = await browser.newContext({ baseURL:origin, viewport:{ width:1440,height:1000 }, serviceWorkers:'block' });
    contexts.push(context);
    await context.route('**/*', route => {
      const request = route.request();
      const url = new URL(request.url());
      const allowedPost = ['/api/auth/login','/api/auth/refresh'].includes(url.pathname) ||
        (role === 'CUSTOMER' && ['/api/cart/items','/api/orders'].includes(url.pathname));
      if (url.origin !== origin || !(['GET','HEAD'].includes(request.method()) || (request.method() === 'POST' && allowedPost))) {
        return route.abort();
      }
      return route.continue();
    });
    const page = await context.newPage();
    const tokenResponse = watch(page, origin, '/api/auth/login', 'POST');
    const [, response] = await Promise.all([logIn(page, account), tokenResponse]).catch(() => {
      throw new Error('Demo login did not complete');
    });
    expect(response.status()).toBe(200);
    const token = (await response.json() as { accessToken: string }).accessToken;
    expect(typeof token === 'string' && token.length > 0, 'login returned bearer').toBe(true);
    const me = await data<{ userId: string; roles: string[] }>(origin, '/api/auth/me', token);
    expect(me.roles).toContain(role);
    expect(uuid.test(me.userId), 'valid demo identity').toBe(true);
    return { page, token, userId: me.userId };
  };

  try {
    const { page, token, userId } = await login(accounts.customer, 'CUSTOMER');
    const empty = await data<Cart>(origin, '/api/cart', token);
    expect(empty.claimed, 'Cart must be unclaimed').toBe(false);
    expect(empty.items.length, 'Demo Cart must be empty; stop without clearing it').toBe(0);
    const customerOrders = () => query<number>(project, 'order_db',
      'select count(*) from orders where customer_id=' + sqlLiteral(userId));
    const customerOrderIds = async () => {
      const ids: string[] = [];
      for (let page = 0; page < 10; page++) {
        const rows = await data<Order[]>(origin, '/api/orders?size=50&page=' + page, token);
        expect(rows.every(row => uuid.test(row.id))).toBe(true);
        ids.push(...rows.map(row => row.id));
        if (rows.length < 50) {
          expect(new Set(ids).size, 'stable Customer Order list').toBe(ids.length);
          return ids.sort();
        }
      }
      throw new Error('Customer fixture has too many orders for the read oracle');
    };
    const idsBefore = await customerOrderIds();
    const ordersBefore = usePostgres ? customerOrders() : idsBefore.length;
    const catalog = await data<{ items: Product[] }>(origin, '/api/catalog/products?size=50');
    const products: Product[] = [];
    for (const name of storeNames) {
      const item = catalog.items.find(item => item.store?.name === name && uuid.test(item.id));
      expect(Boolean(item), 'seed Product exists for ' + name).toBe(true);
      const product = await data<Product>(origin, '/api/catalog/products/' + item!.id);
      expect([product.id, product.store.name]).toEqual([item!.id, name]);
      expect(typeof product.sku === 'string' && product.sku.trim().length > 0).toBe(true);
      products.push(product);
    }
    expect(new Set(products.map(p => p.sku)).size).toBe(2);
    expect(new Set(products.map(p => p.store.id)).size).toBe(2);
    const balances = () => query<Balance[]>(project, 'inventory_db',
      "select coalesce(json_agg(b order by b.sku),'[]'::json) from (select sku, available_quantity as available, reserved_quantity as reserved from inventory_item where sku in (" +
      products.map(p => sqlLiteral(p.sku)).join(',') + ')) b');
    const catalogStock = async () => Promise.all(products.map(async p =>
      ({sku:p.sku, available:(await data<Product>(origin, '/api/catalog/products/' + p.id)).available})));
    const before = usePostgres ? balances() : await catalogStock();
    expect(before).toHaveLength(2);
    expect(before.every(b => Number.isSafeInteger(b.available) && b.available >= 1)).toBe(true);
    if (usePostgres) expect((before as Balance[]).every(b => Number.isSafeInteger(b.reserved) && b.reserved >= 0)).toBe(true);
    const expectedCatalog = products.map(p => ({ sku:p.sku, available:before.find(b => b.sku === p.sku)!.available - 1 }));
    const catalogMatches = async (expected: typeof expectedCatalog, minimumAgeMs = 0) => {
      const earliest = Date.now() + minimumAgeMs;
      await expect.poll(async () => Date.now() < earliest ? null : await catalogStock(),
        { timeout:35_000, intervals:[500,1000,2000] }).toEqual(expected);
      return await catalogStock();
    };
    if (usePostgres) await catalogMatches(products.map(p => ({sku:p.sku, available:before.find(b => b.sku === p.sku)!.available})));

    for (const product of products) {
      await page.goto('/products/' + product.id);
      const added = watch(page, origin, '/api/cart/items', 'POST');
      await page.getByRole('button', { name:'Thêm vào giỏ hàng', exact:true }).click();
      expect((await added).ok()).toBe(true);
    }
    const cart = await data<Cart>(origin, '/api/cart', token);
    expect(cart.claimed).toBe(false);
    expect(Number.isSafeInteger(cart.revision) && cart.revision > empty.revision).toBe(true);
    expect(cart.items).toHaveLength(2);
    for (const p of products) {
      expect(cart.items.some(i => i.productId === p.id && i.storeId === p.store.id && i.quantity === 1)).toBe(true);
    }
    await page.goto('/cart');
    const cards = page.locator('main article.tv3-cart__store');
    await expect(cards).toHaveCount(2);
    for (const p of products) {
      const card = cards.filter({ has:page.getByRole('heading',{name:'Cửa hàng: ' + p.store.name,exact:true}) });
      await expect(card.getByText(p.name,{exact:true})).toBeVisible();
      await expect(card.getByRole('spinbutton',{name:'Số lượng'})).toHaveValue('1');
    }
    await page.locator('main').screenshot({path:resolve(evidence,'Hinh_6_5_Cart_2_Store_REHEARSAL.png'),animations:'disabled'});

    await page.getByRole('link',{name:'Tiến hành đặt hàng'}).click();
    await page.getByLabel('Người nhận').fill('Khách Giả Lập TV3');
    await page.getByLabel('Số điện thoại').fill('0900000000');
    await page.getByLabel('Địa chỉ',{exact:true}).fill('Địa chỉ giả lập QA SOOPI');
    const attemptFile = resolve(evidence, 'checkout-attempt.json');
    writeFileSync(attemptFile, JSON.stringify({ sourceSha:sha, project, state:'OUTCOME_UNVERIFIED' }), {flag:'wx'});
    const placedResponse = watch(page, origin, '/api/orders', 'POST');
    await page.getByRole('button',{name:/đặt hàng/i}).click();
    const placed = await placedResponse;
    expect(placed.status(), 'Initial COD response; if not committed, stop without resubmitting').toBe(200);
    const orderId = (await placed.json() as { orderId: string }).orderId;
    expect(uuid.test(orderId), 'Order response UUID').toBe(true);
    const key = await placed.request().headerValue('idempotency-key');
    const body = placed.request().postData();
    expect(Boolean(key && body), 'UI supplied a key and payload').toBe(true);
    const original = JSON.parse(body!) as { expectedRevision: number; expectedItems: { productId:string; quantity:number }[]; shipping: { name:string; phone:string; address:string } };
    expect(original.expectedRevision).toBe(cart.revision);
    expect(original.expectedItems.map(i => i.productId).sort()).toEqual(products.map(p => p.id).sort());
    expect(original.expectedItems.every(i => i.quantity === 1)).toBe(true);
    await expect(page).toHaveURL(origin + '/orders/' + orderId);
    const path = '/api/orders/' + orderId;
    const order = await data<Order>(origin, path, token);
    expect([order.id, order.paymentMethod]).toEqual([orderId,'COD']);
    expect(order.storeOrders).toHaveLength(2);
    expect(new Set(order.storeOrders.map(c => c.id)).size).toBe(2);
    expect(new Set(order.storeOrders.map(c => c.storeId)).size).toBe(2);
    expect(order.storeOrders.every(c => uuid.test(c.id) && c.orderId === orderId)).toBe(true);
    const children = products.map(p => order.storeOrders.find(c => c.storeId === p.store.id)!);
    expect(children.every(Boolean)).toBe(true);

    const orderProof = () => query<{ roots:number; children:number; succeeded:number }>(project,'order_db',
      'select json_build_object(\'roots\',(select count(*) from orders where id=' + sqlLiteral(orderId) + ' and customer_id=' + sqlLiteral(userId) +
      '),\'children\',(select count(*) from seller_orders where order_id=' + sqlLiteral(orderId) +
      '),\'succeeded\',(select count(*) from checkout_attempts where order_id=' + sqlLiteral(orderId) +
      " and state='SUCCEEDED'))");
    if (usePostgres) expect(orderProof()).toEqual({ roots:1, children:2, succeeded:1 });
    expect(await customerOrderIds()).toEqual([...idsBefore,orderId].sort());
    if (usePostgres) expect(customerOrders()).toBe(ordersBefore + 1);
    writeFileSync(attemptFile, JSON.stringify({ sourceSha:sha, project, state:'COMMITTED', orderId }));
    const reserveCount = () => query<number>(project,'inventory_db',
      'select count(*) from inventory_operation where operation_id=' + sqlLiteral('order-' + orderId + '-reserve') + " and operation_type='RESERVE'");
    const afterCheckout = usePostgres ? balances() : await catalogMatches(expectedCatalog);
    if (usePostgres) {
      expect(reserveCount()).toBe(1);
      expect(afterCheckout).toEqual((before as Balance[]).map(b => ({ ...b, available:b.available-1, reserved:b.reserved+1 })));
      await catalogMatches(expectedCatalog);
    } else {
      expect(afterCheckout).toEqual(expectedCatalog);
    }

    const orderCards = page.locator('main article.panel').filter({ has:page.getByRole('heading',{level:2}) });
    await expect(orderCards).toHaveCount(2);
    for (const [index, child] of children.entries()) {
      const product = products[index];
      expect(child.items.map(i => [i.productId,i.sku,i.quantity])).toEqual([[product.id,product.sku,1]]);
      const card = orderCards.filter({ has:page.getByRole('heading',{name:product.store.name,exact:true}) });
      await expect(card.getByRole('link',{name:product.name,exact:true})).toBeVisible();
    }
    const main = page.locator('main');
    const shipping = main.locator('p').filter({hasText:/^Giao đến:/});
    await expect(shipping).toBeVisible();
    await main.screenshot({path:resolve(evidence,'Hinh_6_6_1_Root_2_SellerOrders_REHEARSAL.png'),animations:'disabled',
      mask:[shipping,main.locator('input,textarea,select,article p.muted')]});

    const replay = await http(origin, '/api/orders', token, { key:key!, body:body! });
    expect(replay.status).toBe(200);
    const replayId = (await replay.json() as { orderId: string }).orderId;
    expect(replayId).toBe(orderId);
    const altered = JSON.stringify({ ...original, shipping:{ ...original.shipping,address:'Địa chỉ giả lập khác' } });
    const mismatch = await http(origin,'/api/orders',token,{ key:key!,body:altered });
    expect(mismatch.status).toBe(409);
    expect(await customerOrderIds()).toEqual([...idsBefore,orderId].sort());
    if (usePostgres) expect(customerOrders()).toBe(ordersBefore + 1);
    if (usePostgres) expect(orderProof()).toEqual({ roots:1, children:2, succeeded:1 });
    // Cached Catalog immediately after replay cannot detect a change.
    const afterReplay = usePostgres ? balances() : await catalogMatches(expectedCatalog, 11_000);
    expect(afterReplay).toEqual(afterCheckout);
    if (usePostgres) expect(reserveCount()).toBe(1);

    const anonymous = (await http(origin,path)).status;
    expect(anonymous).toBe(401);
    const sellers = [];
    for (const [index, email] of [accounts.seller.email,'seller2@hk5.local'].entries()) {
      const seller = await login({email,password:env.SEED_PASSWORD},'SELLER');
      const own = children[index];
      const other = children[1-index];
      const response = await data<Child>(origin,'/api/seller/orders/' + own.id,seller.token);
      expect([response.id,response.storeId,response.orderId]).toEqual([own.id,own.storeId,orderId]);
      const listed = await data<Child[]>(origin,'/api/seller/orders',seller.token);
      expect(listed.some(o => o.id === own.id)).toBe(true);
      expect(listed.some(o => o.id === other.id)).toBe(false);
      const cross = (await http(origin,'/api/seller/orders/' + other.id,seller.token)).status;
      const root = (await http(origin,path,seller.token)).status;
      expect(cross).toBe(404);
      expect([403,404]).toContain(root);
      sellers.push({ ownSellerOrderId:own.id, ownStatus:200, ownListed:true, otherListed:false, crossStatus:cross, customerRootStatus:root });
    }
    writeFileSync(resolve(evidence,'receipt.json'), JSON.stringify({
      kind:'TV3_CHECKOUT_REHEARSAL', sourceSha:sha, project, capturedAt:new Date().toISOString(),
      status:usePostgres?'PASS':'PARTIAL', orderId, paymentMethod:order.paymentMethod, rootCount:1,
      customerOrderDelta:usePostgres ? customerOrders()-ordersBefore : (await customerOrderIds()).length-ordersBefore,
      storeOrders:children.map(c => ({id:c.id,orderId:c.orderId,storeId:c.storeId})),
      replay:{ status:replay.status,orderId:replayId,mismatchStatus:mismatch.status,reserveOperationCount:usePostgres ? reserveCount() : null },
      ownership:{anonymousStatus:anonymous,sellers}, stock:{scope:usePostgres?'POSTGRES_READ_ONLY':'CATALOG_API_ONLY',before,afterCheckout,afterReplay}
    },null,2)+'\n',{flag:'wx'});
  } finally {
    await Promise.allSettled(contexts.map(context => context.close()));
  }
});
