// One-off UAT gap checks for #18 against the disposable tv3-live-uat18 stack. Never prints credentials.
// Usage: node gaps.mjs <worktree>. Writes evidence/gaps.json next to this file.
import { execFileSync } from 'node:child_process';
import { randomUUID, randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const here = import.meta.dirname;
const tree = process.argv[2];
const project = 'tv3-live-uat18';
const base = 'http://localhost:5173';
const env = Object.fromEntries(readFileSync(`${tree}/.env.demo`, 'utf8').split('\n')
  .filter((l) => l.includes('=') && !l.startsWith('#'))
  .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1).trim()]));

const results = [];
const check = (id, name, actual, expected) => {
  const a = JSON.stringify(actual), e = JSON.stringify(expected), ok = a === e;
  results.push({ id, name, ok, actual, expected });
  console.log(`${ok ? 'PASS' : 'FAIL'} ${id} ${name} -> ${a}${ok ? '' : ` (expected ${e})`}`);
};
const ask = (id, name, cond, detail) => check(id, `${name}${detail === undefined ? '' : ` [${JSON.stringify(detail)}]`}`, Boolean(cond), true);
const call = async (path, { token, method = 'GET', body, headers = {} } = {}) => {
  const r = await fetch(base + path, { method, headers: { Origin: base, 'X-CSRF-Protection': '1',
    ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await r.text();
  let json = null; try { json = text ? JSON.parse(text) : null; } catch { /* non JSON edge reply */ }
  return { status: r.status, json };
};
const sql = (db, q) => execFileSync('docker', ['exec', `${project}-postgres-1`, 'psql', '-U', 'hk5', '-d', db, '-At', '-c', q], { encoding: 'utf8' }).trim();
const login = async (email, password) => (await call('/api/auth/login', { method: 'POST', body: { email, password } })).json.accessToken;
const num = (v) => Number(v);

const C = await login('customer@hk5.local', env.SEED_PASSWORD);
const S1 = await login('seller1@hk5.local', env.SEED_PASSWORD);
const S2 = await login('seller2@hk5.local', env.SEED_PASSWORD);
const A = await login(env.ADMIN_BOOTSTRAP_EMAIL, env.ADMIN_BOOTSTRAP_PASSWORD);
const c2Email = `uat-c2-${Date.now()}@hk5.local`, c2Password = randomBytes(18).toString('base64url');
const reg = await call('/api/auth/register', { method: 'POST', body: { email: c2Email, password: c2Password } });
ask('SETUP', 'register C2', reg.status < 300, reg.status);
const C2 = reg.json?.accessToken ?? await login(c2Email, c2Password);

const active = async (t) => (await call('/api/seller/products?size=100', { token: t })).json.products.filter((p) => p.status === 'ACTIVE');
const mine = await active(S1);
const unreviewed = mine.filter((p) => num(sql('product_db', `select count(*) from product_reviews where product_id='${p.id}'`)) === 0);
const [P3] = unreviewed;
const [P1, P5, P6] = mine.filter((p) => p.id !== P3.id);
const [P2] = await active(S2);
const setStock = async (t, id, available) => { const r = await call(`/api/seller/products/${id}/stock`, { token: t, method: 'PUT', body: { available } }); if (r.status !== 200) throw new Error(`stock ${r.status}`); };
const stock = async (t, id) => { const j = (await call(`/api/seller/products/${id}`, { token: t })).json; return { available: j.available, reserved: j.reserved }; };
for (const p of [P1, P3, P5, P6]) await setStock(S1, p.id, 50);
await setStock(S2, P2.id, 50);

const prepare = async (t, items) => {
  for (const item of (await call('/api/cart', { token: t })).json.items ?? []) await call(`/api/cart/items/${item.productId}`, { token: t, method: 'DELETE' });
  for (const [productId, quantity] of items) await call('/api/cart/items', { token: t, method: 'POST', body: { productId, quantity } });
  const cart = (await call('/api/cart', { token: t })).json;
  return { expectedItems: cart.items.map(({ productId, quantity }) => ({ productId, quantity })), expectedRevision: cart.revision,
    shipping: { name: 'UAT', phone: '0901234567', address: '1 Lê Lợi' } };
};
const post = (t, body) => call('/api/orders', { token: t, method: 'POST', headers: { 'Idempotency-Key': randomUUID() }, body });
const order = async (t, items) => {
  const placed = await post(t, await prepare(t, items));
  if (placed.status >= 300) throw new Error(`order ${placed.status} ${JSON.stringify(placed.json)}`);
  return (await call(`/api/orders/${placed.json.orderId}`, { token: t })).json;
};
const act = (t, id, action, reason) => call(`/api/seller/orders/${id}/${action}`, { token: t, method: 'POST', body: reason ? { reason } : {} });
const deliver = async (t, id) => { for (const a of ['accept', 'prepare', 'ready', 'dispatch', 'deliver']) { const r = await act(t, id, a); if (r.status !== 200) throw new Error(`${a} ${r.status}`); } };
const review = (t, orderId, productId, rating, comment) => call('/api/reviews', { token: t, method: 'POST', body: { orderId, productId, rating, comment } });
const until = async (name, test, ms = 45000) => { const end = Date.now() + ms;
  for (;;) { const v = await test(); if (v) return v; if (Date.now() > end) { console.log(`TIMEOUT ${name}`); return v; } await new Promise((d) => setTimeout(d, 500)); } };
const rating = async (id) => (await call(`/api/catalog/products/${id}`)).json.rating;
// The public Catalog is cached for about 10 seconds, so wait for the expected value instead of reading once.
const ratingIs = async (id, count) => (await until(`rating ${id}`, async () => { const r = await rating(id); return r.count === count ? r : null; })) ?? rating(id);
const reviewRows = (orderId) => num(sql('product_db', `select count(*) from product_reviews where order_id='${orderId}'`));

// Review rights arrive by Kafka; release and consume run through the outbox.
const reviewable = (orderId) => until(`reviewable ${orderId}`, () => num(sql('product_db', `select count(*) from reviewable_items where order_id='${orderId}'`)) > 0);
const settled = async (id, want) => { await until(`stock ${id}`, async () => JSON.stringify(await stock(S1, id)) === JSON.stringify(want)); await new Promise((d) => setTimeout(d, 3000)); return stock(S1, id); };

// F01: exact 100000 line on a normal Store.
const f = (await call(`/api/seller/products/${P5.id}`, { token: S1 })).json;
const edited = await call(`/api/seller/products/${P5.id}`, { token: S1, method: 'PUT', body: {
  categoryId: f.product.categoryId, name: f.product.name, unitPrice: 100000, description: f.description, expectedVersion: f.product.version } });
ask('F01', 'set price 100000 keeps product sellable', edited.status === 200 && edited.json.product.status === 'ACTIVE', [edited.status, edited.json?.product?.status]);
const fo = await order(C, [[P5.id, 1]]);
const fs = fo.storeOrders[0];
await deliver(S1, fs.id);
const fView = (await call(`/api/seller/orders/${fs.id}`, { token: S1 })).json;
check('F01', 'gross/fee/net', [num(fView.gross), num(fView.platformFee), num(fView.sellerNet)], [100000, 5000, 95000]);

// R01/R02: eligibility.
const r1 = await order(C, [[P1.id, 1]]);
const r1s = r1.storeOrders[0];
check('R02', 'review before delivery refused', (await review(C, r1.id, P1.id, 5)).status, 409);
await deliver(S1, r1s.id);
await reviewable(r1.id);
check('R02', 'other buyer refused', (await review(C2, r1.id, P1.id, 5)).status, 403);
check('R02', 'product outside order refused', (await review(C, r1.id, P2.id, 5)).status, 409);
check('R02', 'stars 0 refused', (await review(C, r1.id, P1.id, 0)).status, 400);
check('R02', 'stars 6 refused', (await review(C, r1.id, P1.id, 6)).status, 400);
check('R02', 'DB has no review after refusals', reviewRows(r1.id), 0);
const before1 = await rating(P1.id);
const made = await review(C, r1.id, P1.id, 5, 'UAT 5 sao');
check('R01', 'buyer reviews delivered product, 5 stars', [made.status, made.json?.orderId, made.json?.productId, made.json?.rating], [201, r1.id, P1.id, 5]);
const r1b = await order(C, [[P1.id, 1]]);
await deliver(S1, r1b.storeOrders[0].id);
await reviewable(r1b.id);
const low = await review(C, r1b.id, P1.id, 1, 'UAT 1 sao');
check('R01', 'buyer reviews delivered product, 1 star', [low.status, low.json?.rating], [201, 1]);
check('R01', 'rating count grew by 2', (await ratingIs(P1.id, before1.count + 2)).count, before1.count + 2);
check('R02', 'second review on same order and product refused', (await review(C, r1.id, P1.id, 4)).status, 409);

// R03: concurrent duplicates.
const r3 = await order(C, [[P1.id, 1]]);
await deliver(S1, r3.storeOrders[0].id);
await reviewable(r3.id);
const pair = await Promise.all([review(C, r3.id, P1.id, 4, 'a'), review(C, r3.id, P1.id, 3, 'b')]);
check('R03', 'concurrent reviews: one wins', pair.map((r) => r.status).sort(), [201, 409]);
check('R03', 'DB has one row', reviewRows(r3.id), 1);

// R04/R05: moderation and aggregates, on a product with no reviews.
const empty = await rating(P3.id);
check('R05', 'no visible review maps to count 0', empty.count, 0);
const r5 = await order(C, [[P3.id, 1]]);
await deliver(S1, r5.storeOrders[0].id);
await reviewable(r5.id);
const rv = await review(C, r5.id, P3.id, 1, 'UAT hide');
const one = await ratingIs(P3.id, 1);
check('R05', 'one 1-star review', [one.count, num(one.average)], [1, 1]);
const moderate = (t, action, body) => call(`/api/admin/reviews/${rv.json.id}/${action}`, { token: t, method: 'POST', body });
check('R04', 'Seller cannot hide', (await moderate(S1, 'hide', { reason: 'x' })).status, 403);
check('R04', 'Customer cannot hide', (await moderate(C, 'hide', { reason: 'x' })).status, 403);
check('R06', 'hide without reason refused', (await moderate(A, 'hide', {})).status, 400);
check('R06', 'reason over 500 refused', (await moderate(A, 'hide', { reason: 'x'.repeat(501) })).status, 400);
check('R04', 'Admin hides', (await moderate(A, 'hide', { reason: 'UAT' })).status, 200);
check('R05', 'hidden drops from count', (await ratingIs(P3.id, 0)).count, 0);
const replay = await moderate(A, 'hide', { reason: 'UAT' });
check('R04', 'hide replay keeps count', [replay.status < 500, (await rating(P3.id)).count], [true, 0]);
check('R04', 'Admin shows', (await moderate(A, 'show')).status, 200);
const back = await ratingIs(P3.id, 1);
check('R05', 'show restores count and average', [back.count, num(back.average)], [1, 1]);
const r6 = await order(C, [[P1.id, 1]]);
await deliver(S1, r6.storeOrders[0].id);
await reviewable(r6.id);
check('R06', 'comment over 2000 refused', (await review(C, r6.id, P1.id, 4, 'x'.repeat(2001))).status, 400);
check('R06', 'comment of 1001 refused (TV5_UAT.md limit 1000)', (await review(C, r6.id, P1.id, 4, 'x'.repeat(1001))).status, 400);

// F03: reachable delivered/paid/settled combinations.
const settle = (id, t = A) => call(`/api/admin/store-orders/${id}/settle`, { token: t, method: 'POST' });
const cod = (id, t = A) => call(`/api/admin/store-orders/${id}/cod-collected`, { token: t, method: 'POST' });
const f3 = await order(C, [[P1.id, 1]]);
const f3s = f3.storeOrders[0];
check('F03', 'F/F/F settle refused', (await settle(f3s.id)).status, 409);
check('F03', 'COD before delivery refused, so F/T/* is unreachable', (await cod(f3s.id)).status, 409);
await deliver(S1, f3s.id);
check('F03', 'T/F/F settle refused', (await settle(f3s.id)).status, 409);
check('F03', 'Seller cannot record COD', (await cod(f3s.id, S1)).status, 403);
check('F03', 'Seller cannot settle', (await settle(f3s.id, S1)).status, 403);
check('F03', 'COD collected', (await cod(f3s.id)).status, 200);
const finance = async () => (await call('/api/seller/finance', { token: S1 })).json.totals.settledNet;
const settledBefore = num(await finance());
const netOf = num((await call(`/api/seller/orders/${f3s.id}`, { token: S1 })).json.sellerNet);

// F04: concurrent settle, retry, restart.
const many = await Promise.all([1, 2, 3, 4, 5].map(() => settle(f3s.id)));
check('F03', 'T/T/F eligible, exactly one settles', many.filter((r) => r.status === 200).length, 1);
check('F04', 'concurrent settle: others refused', many.filter((r) => r.status === 409).length, 4);
const at = many.find((r) => r.status === 200)?.json.settledAt;
check('F03', 'T/T/T settle refused', (await settle(f3s.id)).status, 409);
check('F04', 'money moved once', num(await finance()), settledBefore + netOf);
execFileSync('docker', ['restart', `${project}-order-service-1`], { stdio: 'ignore' });
for (let i = 0; i < 60; i += 1) { if ((await call('/api/admin/finance', { token: A })).status === 200) break; await new Promise((d) => setTimeout(d, 3000)); }
check('F04', 'retry after restart refused', (await settle(f3s.id)).status, 409);
const afterRestart = (await call(`/api/seller/orders/${f3s.id}`, { token: S1 })).json.settledAt;
check('F04', 'settledAt and total durable after restart', [Date.parse(afterRestart), num(await finance())], [Date.parse(at), settledBefore + netOf]);

// F05: Store isolation.
const f5 = await order(C, [[P2.id, 1]]);
const f5s = f5.storeOrders[0];
const crossDetail = await call(`/api/seller/orders/${f5s.id}`, { token: S1 });
ask('F05', 'Seller A cannot read Store B order', [403, 404].includes(crossDetail.status), crossDetail.status);
const crossAct = await act(S1, f5s.id, 'accept');
ask('F05', 'Seller A cannot act on Store B order', [403, 404].includes(crossAct.status), crossAct.status);
const list = (await call('/api/seller/orders?size=200', { token: S1 })).json;
const rows = Array.isArray(list) ? list : (list.orders ?? list.items ?? []);
ask('F05', 'Seller A list has no Store B row', rows.length > 0 && !rows.some((r) => r.id === f5s.id), rows.length);
const money = (await call('/api/seller/finance', { token: S1 })).json;
ask('F05', 'Seller A finance has no Store B row', !(money.rows ?? []).some((r) => r.storeOrderId === f5s.id), (money.rows ?? []).length);
check('F05', 'Seller cannot read platform finance', (await call('/api/admin/finance', { token: S1 })).status, 403);
const otherBuyer = await call(`/api/orders/${f5.id}`, { token: C2 });
ask('C01', 'C2 cannot read C order', [403, 404].includes(otherBuyer.status), otherBuyer.status);

// C02: last unit race.
await setStock(S1, P6.id, 1);
const [b1, b2] = [await prepare(C, [[P6.id, 1]]), await prepare(C2, [[P6.id, 1]])];
const race = await Promise.all([post(C, b1), post(C2, b2)]);
check('C02', 'last unit: one checkout wins', race.map((r) => r.status).sort(), [200, 409]);
const after = await stock(S1, P6.id);
check('C02', 'stock not negative', [after.available, after.reserved], [0, 1]);

// C03: cancellation and rejection release once and never settle.
const cancel = (o, s) => call(`/api/orders/${o}/store-orders/${s}/cancel`, { token: C, method: 'POST', body: { reason: 'UAT' } });
const s0 = await settled(P1.id, { available: (await stock(S1, P1.id)).available, reserved: 0 });
const c3 = await order(C, [[P1.id, 2]]);
const c3s = c3.storeOrders[0];
check('C03', 'held 2', (await stock(S1, P1.id)).available, s0.available - 2);
check('C03', 'cancel', (await cancel(c3.id, c3s.id)).status, 200);
check('C03', 'cancel replay refused', (await cancel(c3.id, c3s.id)).status, 409);
const released = await settled(P1.id, s0);
check('C03', 'released once', [released.available, released.reserved], [s0.available, s0.reserved]);
check('C03', 'cancelled order cannot be settled', (await settle(c3s.id)).status, 409);
check('C03', 'cancelled order cannot record COD', (await cod(c3s.id)).status, 409);
const c3b = await order(C, [[P1.id, 1]]);
const rejectId = c3b.storeOrders[0].id;
check('C03', 'Seller rejects', (await act(S1, rejectId, 'reject', 'UAT')).status, 200);
check('C03', 'reject replay refused', (await act(S1, rejectId, 'reject', 'UAT')).status, 409);
const rel2 = await settled(P1.id, s0);
check('C03', 'reject released once', [rel2.available, rel2.reserved], [s0.available, s0.reserved]);

// A01: Gateway authorization.
check('A01', 'no token', (await call('/api/orders')).status, 401);
check('A01', 'fake token', (await call('/api/orders', { token: 'not.a.jwt' })).status, 401);
check('A01', 'wrong role', (await call('/api/admin/finance', { token: C })).status, 403);
const gw = await fetch(`http://localhost:18080/internal/catalog/products/${P1.id}`, { headers: { Authorization: `Bearer ${A}`, Origin: base, 'X-CSRF-Protection': '1' } });
check('A01', 'Gateway does not route /internal', gw.status, 404);
const site = await fetch(`${base}/internal/catalog/products/${P1.id}`, { headers: { Authorization: `Bearer ${A}` } });
ask('A01', 'site serves the SPA shell for /internal, no product data', (site.headers.get('content-type') ?? '').includes('text/html') && !(await site.text()).includes(P1.id), site.status);

// Side effects dead-lettered during the whole run.
const dead = await call('/api/admin/outbox/dead', { token: A });
check('OPS', 'no dead outbox rows', [dead.status, (Array.isArray(dead.json) ? dead.json : dead.json?.rows ?? dead.json?.items ?? []).length], [200, 0]);

writeFileSync(resolve(here, 'evidence/gaps.json'), JSON.stringify(results, null, 2));
const fails = results.filter((r) => !r.ok).length;
console.log(`${results.length - fails} PASS, ${fails} FAIL`);
process.exit(fails ? 1 : 0);
