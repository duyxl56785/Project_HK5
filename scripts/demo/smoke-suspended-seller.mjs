// Checks a suspension on the running stack through the Gateway with real Auth tokens: the role sync, a token issued
// before and after it, what a suspended Seller may still do, a write caught in flight by the suspension, and a Seller
// suspended by the build before V7. Suspends seller2, then reinstates it; holds row locks and restarts seller-service.
// Usage: node scripts/demo/smoke-suspended-seller.mjs
import { execSync, spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const env = Object.fromEntries(readFileSync(resolve(import.meta.dirname, '../../.env.demo'), 'utf8').split('\n')
  .filter((line) => line.includes('=') && !line.startsWith('#'))
  .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1).trim()]));
const gateway = process.env.GATEWAY_URL ?? 'http://localhost:18080';
const origin = env.ALLOWED_ORIGINS.split(',')[0];
const wait = (ms) => new Promise((done) => setTimeout(done, ms));

let fails = 0;
function check(name, actual, expected) {
  const ok = actual === expected;
  if (!ok) fails += 1;
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name} -> ${actual}${ok ? '' : ` (expected ${expected})`}`);
}

async function call(path, { token, method = 'GET', body, headers = {} } = {}) {
  const response = await fetch(gateway + path, {
    method,
    headers: {
      Origin: origin, 'X-CSRF-Protection': '1', ...headers,
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  return { status: response.status, json: text ? JSON.parse(text) : null };
}

const login = async (email, password = env.SEED_PASSWORD) =>
  (await call('/api/auth/login', { method: 'POST', body: { email, password } })).json.accessToken;
const roles = (token) => JSON.parse(Buffer.from(token.split('.')[1], 'base64url')).roles;
const act = (token, id, action, reason) =>
  call(`/api/seller/orders/${id}/${action}`, { token, method: 'POST', body: reason === undefined ? {} : { reason } });

const admin = await login(env.ADMIN_BOOTSTRAP_EMAIL, env.ADMIN_BOOTSTRAP_PASSWORD);
const customer = await login('customer@hk5.local');
const otherSeller = await login('seller1@hk5.local');
const before = await login('seller2@hk5.local');

const product = (await call('/api/seller/products?size=100', { token: before })).json.products.find((row) => row.status === 'ACTIVE');
check('set stock', (await call(`/api/seller/products/${product.id}/stock`, { token: before, method: 'PUT', body: { available: 50 } })).status, 200);

async function place() {
  for (const item of (await call('/api/cart', { token: customer })).json.items ?? [])
    await call(`/api/cart/items/${item.productId}`, { token: customer, method: 'DELETE' });
  await call('/api/cart/items', { token: customer, method: 'POST', body: { productId: product.id, quantity: 1 } });
  const cart = (await call('/api/cart', { token: customer })).json;
  const placed = await call('/api/orders', {
    token: customer, method: 'POST', headers: { 'Idempotency-Key': randomUUID() },
    body: {
      expectedItems: cart.items.map(({ productId, quantity }) => ({ productId, quantity })), expectedRevision: cart.revision,
      shipping: { name: 'Smoke', phone: '0901234567', address: '1 Lê Lợi' },
    },
  });
  const order = (await call(`/api/orders/${placed.json.orderId}`, { token: customer })).json;
  return order.storeOrders.find((row) => row.items.some((item) => item.productId === product.id));
}

const pendingA = await place(), pendingB = await place(), accepted = await place();
check('accept before suspension', (await act(before, accepted.id, 'accept')).status, 200);
const store = (await call('/api/stores/me', { token: before })).json;
const seller = (await call('/api/sellers/me', { token: before })).json.seller;
check('suspend', (await call(`/api/admin/sellers/${seller.id}/suspend`, {
  token: admin, method: 'POST', body: { expectedVersion: seller.version, reason: 'Smoke' },
})).status, 200);

try {
  await wait(5000); // The role sync relay runs every 2 seconds.
  const after = await login('seller2@hk5.local');
  check('new token keeps SELLER', roles(after).includes('SELLER'), true);

  for (const [name, token] of [['old', before], ['new', after]]) {
    check(`${name} token: accept refused`, (await act(token, pendingA.id, 'accept')).status, 403);
    check(`${name} token: finance refused`, (await call('/api/seller/finance', { token })).status, 403);
    check(`${name} token: stock refused`, (await call(`/api/seller/products/${product.id}/stock`, {
      token, method: 'PUT', body: { available: 49 },
    })).status, 403);
    check(`${name} token: Store write refused`, (await call(`/api/stores/${store.id}/open`, {
      token, method: 'PATCH', body: { open: false, expectedVersion: store.version },
    })).status, 403);
  }
  const listed = (await call(`/api/seller/orders/${accepted.id}`, { token: before })).json;
  check('old token: order hides the fee', listed.platformFee, null);
  check('old token: order keeps the amount to collect', listed.gross > 0, true);
  check('old token: reject pending', (await act(before, pendingB.id, 'reject', 'Smoke')).status, 200);
  check('new token: prepare accepted', (await act(after, accepted.id, 'prepare')).status, 200);
  check('other Store cannot act', (await act(otherSeller, accepted.id, 'ready')).status, 404);
} finally {
  const current = (await call('/api/sellers/me', { token: before })).json.seller;
  check('reinstate', (await call(`/api/admin/sellers/${current.id}/reinstate`, {
    token: admin, method: 'POST', body: { expectedVersion: current.version },
  })).status, 200);
}

await wait(3000);
const reinstated = await login('seller2@hk5.local');
check('reinstated: accept', (await act(reinstated, pendingA.id, 'accept')).status, 200);
check('reinstated: finance', (await call('/api/seller/finance', { token: reinstated })).status, 200);
check('old token after reinstate: finance', (await call('/api/seller/finance', { token: before })).status, 200);

const project = process.env.COMPOSE_PROJECT_NAME ?? 'hk5-demo';
const postgres = `${project}-postgres-1`;
const sql = (database, statement) => execSync(`docker exec ${postgres} psql -U hk5 -d ${database} -Atc "${statement}"`).toString().trim();
const until = async (ready, ms) => {
  for (const end = Date.now() + ms; Date.now() < end; await wait(500)) if (await ready()) return true;
  return false;
};
const roleSynced = () => until(() =>
  sql('seller_db', `select count(*) from seller_outbox where seller_id = '${seller.id}' and delivered_at is null`) === '0', 15_000);
const setStatus = async (action, body = {}) => {
  const current = (await call('/api/sellers/me', { token: await login('seller2@hk5.local') })).json.seller;
  return call(`/api/admin/sellers/${current.id}/${action}`, { token: admin, method: 'POST', body: { expectedVersion: current.version, ...body } });
};
const suspend = () => setStatus('suspend', { reason: 'Smoke' });

// A row lock parks a real request after its authority check, which no timing of ordinary requests can do reliably.
function holdRow(database, select) {
  const psql = spawn('docker', ['exec', '-i', postgres, 'psql', '-U', 'hk5', '-d', database, '-Atq']);
  const held = new Promise((done) => psql.stdout.once('data', done));
  psql.stdin.write(`begin; ${select} for update;\n`);
  return { held, release: () => new Promise((done) => { psql.on('close', done); psql.stdin.end('commit;\n'); }) };
}

async function stopInFlight(name, database, select, write, unchanged) {
  const row = holdRow(database, select);
  await row.held;
  const pending = write();
  await wait(1500);
  check(`${name}: suspend while the write waits`, (await suspend()).status, 200);
  check(`${name}: role sync done first`, await roleSynced(), true);
  await row.release();
  check(`${name}: write stopped`, (await pending).status, 401);
  check(`${name}: nothing committed`, unchanged(), true);
  check(`${name}: reinstate`, (await setStatus('reinstate')).status, 200);
  await roleSynced();
}

const racer = await login('seller2@hk5.local');
const parked = await place();
await stopInFlight('accept', 'order_db', `select 1 from seller_orders where id = '${parked.id}'`, () => act(racer, parked.id, 'accept'),
  () => sql('order_db', `select status from seller_orders where id = '${parked.id}'`) === parked.status);

const live = (await call('/api/seller/products?size=100', { token: racer })).json.products.find((row) => row.id === product.id);
await stopInFlight('Product', 'product_db', `select 1 from products where id = '${product.id}'`,
  () => call(`/api/seller/products/${product.id}/deactivate`, { token: racer, method: 'POST', body: { expectedVersion: live.version } }),
  () => sql('product_db', `select status from products where id = '${product.id}'`) === 'ACTIVE');

// Store writes hold a shared lock on the Seller row, so the suspension itself waits for them and no fence is involved.
const freshStore = (await call('/api/stores/me', { token: racer })).json;
const storeRow = holdRow('seller_db', `select 1 from stores where id = '${freshStore.id}'`);
await storeRow.held;
const storeWrite = call(`/api/stores/${freshStore.id}/open`, {
  token: racer, method: 'PATCH', body: { open: false, expectedVersion: freshStore.version },
});
await wait(1500);
const storeSuspension = suspend();
await wait(1500);
check('Store: suspension waits for the write', sql('seller_db', `select status from seller_profiles where id = '${seller.id}'`), 'APPROVED');
await storeRow.release();
check('Store: write finished first', (await storeWrite).status, 200);
check('Store: then the suspension', (await storeSuspension).status, 200);
await roleSynced();
check('Store: reinstate', (await setStatus('reinstate')).status, 200);
await roleSynced();
const closedStore = (await call('/api/stores/me', { token: await login('seller2@hk5.local') })).json;
check('Store: reopen', (await call(`/api/stores/${freshStore.id}/open`, {
  token: await login('seller2@hk5.local'), method: 'PATCH', body: { open: true, expectedVersion: closedStore.version },
})).status, 200);

// Recreates what the build before V7 left: suspended at Seller, role already gone at Auth, V7 still to run.
const stranded = await place();
const owner = JSON.parse(Buffer.from(racer.split('.')[1], 'base64url')).sub;
check('upgrade: suspend', (await suspend()).status, 200);
await roleSynced();
sql('auth_db', `delete from account_role_grants where account_id = '${owner}' and role = 'SELLER'`);
sql('seller_db', `delete from flyway_schema_history where script = 'V7__resend_suspended_seller_role.sql'`);
check('upgrade: role gone before V7', roles(await login('seller2@hk5.local')).includes('SELLER'), false);
execSync(`docker restart ${project}-seller-service-1`);
check('upgrade: Auth gets the role back from V7', await until(async () =>
  roles(await login('seller2@hk5.local')).includes('SELLER'), 120_000), true);
check('upgrade: can reject a pending order again', (await act(await login('seller2@hk5.local'), stranded.id, 'reject', 'Smoke')).status, 200);
check('upgrade: reinstate', (await setStatus('reinstate')).status, 200);

console.log(`FAILS=${fails}`);
process.exit(fails ? 1 : 0);
