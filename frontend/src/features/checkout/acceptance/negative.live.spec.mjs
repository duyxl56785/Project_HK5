import { test, expect, request } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { createHash, randomUUID } from 'node:crypto';

const required = name => {
  if (!process.env[name]) throw new Error(`NOT_RUN prerequisite missing: ${name}`);
  return process.env[name];
};
const base = new URL(required('TV3_NEG_BASE_URL'));
if (!['127.0.0.1', 'localhost'].includes(base.hostname) || base.username || base.password || base.pathname !== '/')
  throw new Error('Use only an owner-approved loopback demo');
const project = required('TV3_NEG_PROJECT');
if (!/^tv3-live-[a-z0-9-]+$/.test(project)) throw new Error('Isolated TV3 demo required');
const evidence = resolve(required('TV3_NEG_EVIDENCE'));
const label = randomUUID().slice(0, 8);
const sha = value => createHash('sha256').update(JSON.stringify(value)).digest('hex');
const literal = value => "'" + String(value).replaceAll("'", "''") + "'";
const docker = ['--host=unix:///var/run/docker.sock'];
const dockerEnv = { ...process.env };
for (const name of ['DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH']) delete dockerEnv[name];
let context, customer, seller1, seller2, admin, customerId, lines, original, baselineId, currentDir;
let http = [];
const ownedProducts = new Set();
const shipping = { name: 'Synthetic TV3 negative fixture', phone: '0901234567', address: 'Synthetic isolated acceptance address' };

function save(name, body) {
  mkdirSync(currentDir, { recursive: true });
  writeFileSync(resolve(currentDir, name), JSON.stringify(body, null, 2) + '\n');
}
function query(db, sql) {
  const ids = execFileSync('docker', [...docker, 'ps', '-q', '--filter', `label=com.docker.compose.project=${project}`,
    '--filter', 'label=com.docker.compose.service=postgres'], { encoding: 'utf8', env: dockerEnv }).trim().split(/\s+/);
  expect(ids).toHaveLength(1);
  const info = JSON.parse(execFileSync('docker', [...docker, 'inspect', ids[0]], { encoding: 'utf8', env: dockerEnv }))[0];
  expect(info.Config.Labels['com.docker.compose.project']).toBe(project);
  expect(info.Config.Labels['com.docker.compose.project.working_dir']).toBe(required('TV3_NEG_COMPOSE_DIR'));
  return JSON.parse(execFileSync('docker', [...docker, 'exec', '-i', ids[0], 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1',
    '-U', required('TV3_NEG_PG_USER'), '-d', db], { input: `BEGIN READ ONLY;\n${sql};\nROLLBACK;\n`, encoding: 'utf8', env: dockerEnv }).trim());
}
async function api(path, token, method = 'GET', body, key) {
  if (!path.startsWith('/api/')) throw new Error('Public Gateway APIs only');
  let response;
  try {
    response = await context.fetch(base.origin + path, { method, data: body, timeout: 15_000, failOnStatusCode: false,
      headers: { Origin: base.origin, 'X-CSRF-Protection': '1', ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(key ? { 'Idempotency-Key': key } : {}) } });
  } catch { throw new Error(`Gateway ${method} ${path} transport failed; no credentials logged`); }
  let data;
  try { data = await response.json(); } catch { data = {}; }
  // Deliberate projection: never retain Auth response, headers, request shipping or arbitrary body.
  http.push({ method, path, status: response.status(), ...(key ? { keySha256: sha(key) } : {}),
    ...(path === '/api/orders' && body ? { expectedItems: body.expectedItems, expectedRevision: body.expectedRevision } : {}),
    ...(!response.ok() ? { error: { code: data.code, message: data.message } } : {}),
    ...(data.orderId ? { orderId: data.orderId, sellerOrderCount: data.sellerOrderCount } : {}),
    ...(data.product ? { product: { id: data.product.id, sku: data.product.sku, status: data.product.status,
      version: data.product.version, unitPrice: data.product.unitPrice } } : {}) });
  return { status: response.status(), data };
}
async function ok(path, token, method = 'GET', body, status = 200) {
  const response = await api(path, token, method, body);
  expect(response.status, `Fixture API ${method} ${path}`).toBe(status);
  return response.data;
}
async function login(email, password, role) {
  const response = await api('/api/auth/login', null, 'POST', { email, password });
  expect(response.status).toBe(200);
  const me = await ok('/api/auth/me', response.data.accessToken);
  expect(me.roles).toContain(role);
  return { token: response.data.accessToken, id: me.userId };
}
async function activate(product, seller) {
  const submitted = await ok(`/api/seller/products/${product.id}/submit`, seller, 'POST', { expectedVersion: product.version });
  const approved = await ok(`/api/admin/products/${product.id}/approve`, admin, 'POST',
    { expectedVersion: submitted.product.version, reason: 'Synthetic isolated TV3 negative acceptance fixture' });
  expect(approved.status).toBe('ACTIVE');
  return approved;
}
async function createProduct(seller, categoryId, suffix) {
  const created = await ok('/api/seller/products', seller, 'POST', { categoryId, sku: `TV3-NEG-${label}-${suffix}`,
    name: `Synthetic TV3 negative ${label} ${suffix}`, unitPrice: 10000, description: 'Disposable acceptance fixture', initialStock: 8 }, 201);
  ownedProducts.add(created.product.id);
  return activate(created.product, seller);
}
async function cart() { return ok('/api/cart', customer); }
async function fillCart(quantity = 1) {
  const empty = await cart();
  expect(empty.claimed).toBe(false); expect(empty.items).toEqual([]);
  for (const line of lines) await ok('/api/cart/items', customer, 'POST', { productId: line.productId, quantity });
  return cart();
}
async function clearOwnCart() {
  const state = await cart();
  expect(state.claimed, 'Never release or edit an uncertain claim').toBe(false);
  for (const item of state.items) {
    expect(ownedProducts.has(item.productId), 'Never delete another fixture Cart line').toBe(true);
    await ok(`/api/cart/items/${item.productId}`, customer, 'DELETE');
  }
}
function bodyFor(state) {
  return { expectedItems: state.items.map(({ productId, quantity }) => ({ productId, quantity })), expectedRevision: state.revision, shipping };
}
async function snapshot(phase) {
  const order = query('order_db', `select json_build_object(
    'orderCount',(select count(*) from orders), 'sellerOrderCount',(select count(*) from seller_orders),
    'orders',(select coalesce(json_agg(x order by x.id),'[]') from (select id,status,total::text from orders where customer_id=${literal(customerId)}) x),
    'attempts',(select coalesce(json_agg(x order by x.id),'[]') from (select id,state,order_id,fingerprint from checkout_attempts where customer_id=${literal(customerId)}) x),
    'drafts',(select coalesce(json_agg(x order by x.checkout_attempt_id),'[]') from (select d.checkout_attempt_id,d.payload_sha256 from checkout_order_drafts d join checkout_attempts a on a.id=d.checkout_attempt_id where a.customer_id=${literal(customerId)}) x),
    'journals',(select coalesce(json_agg(x order by x.operation_id),'[]') from (select operation_id,operation_type,state,send_attempts,last_error_code,canonical_lines,payload_fingerprint from inventory_operation_journal) x),
    'outboxCount',(select count(*) from outbox_events))`);
  const inventory = query('inventory_db', `select json_build_object(
    'balances',(select coalesce(json_agg(x order by x.sku),'[]') from (select sku,available_quantity as available,reserved_quantity as reserved,version from inventory_item) x),
    'operations',(select coalesce(json_agg(x order by x.operation_id),'[]') from (select operation_id,operation_type,request_hash from inventory_operation) x))`);
  const state = await cart();
  // Cart comes from Gateway plus independent DB revision/claim check, never export the claim key.
  const dbCart = query('cart_db', `select json_build_object('cart',(select row_to_json(x) from
    (select revision,(checkout_claim is not null) as claimed from carts where customer_id=${literal(customerId)}) x))`);
  const result = { order, inventory, cart: state, dbCart };
  save(`${phase}.json`, result);
  return result;
}
async function rejected(name, body, key, expectedMessage, kind = 'preflight') {
  const before = await snapshot('before');
  const response = await api('/api/orders', customer, 'POST', body, key);
  const after = await snapshot('after');
  save('http.json', http);
  save('comparison.json', { name, status: response.status, error: response.data,
    orders: [before.order.orderCount, after.order.orderCount], sellerOrders: [before.order.sellerOrderCount, after.order.sellerOrderCount],
    cartRevision: [before.cart.revision, after.cart.revision], inventoryOperations: [before.inventory.operations.length, after.inventory.operations.length],
    inventoryBalancesUnchanged: JSON.stringify(before.inventory.balances) === JSON.stringify(after.inventory.balances),
    journalDelta: after.order.journals.filter(x => !before.order.journals.some(b => b.operation_id === x.operation_id)),
    draftDelta: after.order.drafts.length - before.order.drafts.length });
  expect(response.status).toBe(409);
  expect(response.data.message).toContain(expectedMessage);
  expect(after.order.orderCount).toBe(before.order.orderCount);
  expect(after.order.sellerOrderCount).toBe(before.order.sellerOrderCount);
  expect(after.order.orders).toEqual(before.order.orders);
  expect(after.order.outboxCount).toBe(before.order.outboxCount);
  expect(after.inventory).toEqual(before.inventory);
  expect(after.cart.claimed).toBe(false);
  expect(after.cart.items).toEqual(before.cart.items);
  expect(after.dbCart.cart).toEqual({ revision: after.cart.revision, claimed: false });
  const newJournals = after.order.journals.filter(x => !before.order.journals.some(b => b.operation_id === x.operation_id));
  if (kind === 'stock') {
    expect(newJournals).toHaveLength(1);
    expect(newJournals[0]).toMatchObject({ operation_type: 'RESERVE', state: 'REJECTED', send_attempts: 1, last_error_code: 'INSUFFICIENT_STOCK' });
    expect(after.order.drafts.length).toBe(before.order.drafts.length + 1);
  } else {
    expect(after.order.journals).toEqual(before.order.journals);
    expect(after.order.drafts).toEqual(before.order.drafts);
  }
  if (kind === 'replay') {
    expect(after.order.attempts).toEqual(before.order.attempts);
    expect(after.cart.revision).toBe(before.cart.revision);
  } else {
    const attempts = after.order.attempts.filter(x => !before.order.attempts.some(b => b.id === x.id));
    expect(attempts).toHaveLength(1); expect(attempts[0].state).toBe('FAILED');
    expect(after.cart.revision).toBe(before.cart.revision + 2); // claim + safe release
  }
}

test.beforeAll(async () => {
  currentDir = resolve(evidence, 'setup'); http = [];
  context = await request.newContext();
  const signed = await login(required('TV3_NEG_CUSTOMER_EMAIL'), required('TV3_NEG_PASSWORD'), 'CUSTOMER');
  customer = signed.token; customerId = signed.id;
  seller1 = (await login(required('TV3_NEG_SELLER_A_EMAIL'), required('TV3_NEG_PASSWORD'), 'SELLER')).token;
  seller2 = (await login(required('TV3_NEG_SELLER_B_EMAIL'), required('TV3_NEG_PASSWORD'), 'SELLER')).token;
  admin = (await login(required('TV3_NEG_ADMIN_EMAIL'), required('TV3_NEG_ADMIN_PASSWORD'), 'ADMIN')).token;
  const initial = await cart(); expect(initial.claimed).toBe(false); expect(initial.items).toEqual([]);
  const catalog = await ok('/api/catalog/products?size=50'); expect(catalog.items.length).toBeGreaterThan(0);
  const a = await createProduct(seller1, catalog.items[0].categoryId, 'A');
  const b = await createProduct(seller2, catalog.items[0].categoryId, 'B');
  expect(a.storeId).not.toBe(b.storeId);
  lines = [a, b].map(p => ({ productId: p.id, sku: p.sku, storeId: p.storeId }));
  const state = await fillCart(); original = { body: bodyFor(state), key: randomUUID() };
  await snapshot('before-baseline');
  const placed = await api('/api/orders', customer, 'POST', original.body, original.key);
  expect(placed.status).toBe(200); expect(placed.data.sellerOrderCount).toBe(2); baselineId = placed.data.orderId;
  const after = await snapshot('after-baseline');
  expect(after.order.orders.some(o => o.id === baselineId)).toBe(true);
  save('fixture.json', { customerId, lines, baselineOrderId: baselineId, originalKeySha256: sha(original.key),
    sourceHead: required('TV3_NEG_SOURCE_HEAD'), deployedApplicationHead: required('TV3_NEG_DEPLOYED_HEAD'), mockedNetwork: false });
  save('http.json', http);
});
test.beforeEach(async ({}, info) => {
  currentDir = resolve(evidence, info.title.split(' ')[0]); http = [];
});
test.afterEach(async ({}, info) => {
  save('http.json', http); save('result.json', { title: info.title, status: info.status, expectedStatus: info.expectedStatus });
  // Only own unclaimed lines are removed, through public API; no uncertain reconciliation.
  await clearOwnCart();
});
test.afterAll(async () => { await context?.dispose(); });

test('replay-body same key with changed expectedItems rejects without effects', async () => {
  const changed = structuredClone(original.body); changed.expectedItems[0].quantity += 1;
  await rejected('same-key changed body', changed, original.key, 'IDEMPOTENCY_PAYLOAD_MISMATCH', 'replay');
});
test('replay-shipping same key with changed shipping rejects without effects', async () => {
  const changed = structuredClone(original.body); changed.shipping.address += ' changed';
  await rejected('same-key changed shipping', changed, original.key, 'IDEMPOTENCY_PAYLOAD_MISMATCH', 'replay');
});
test('expected-items mismatch rejects before Inventory', async () => {
  const changed = bodyFor(await fillCart()); changed.expectedItems[1].quantity += 1;
  await rejected('expectedItems mismatch', changed, randomUUID(), 'Cart and expectedItems differ');
});
test('catalog-price changed since Cart rejects before Inventory', async () => {
  const body = bodyFor(await fillCart());
  let current = (await ok(`/api/seller/products/${lines[1].productId}`, seller2)).product;
  current = (await ok(`/api/seller/products/${current.id}/deactivate`, seller2, 'POST', { expectedVersion: current.version })).product;
  current = (await ok(`/api/seller/products/${current.id}`, seller2, 'PUT', { categoryId: current.categoryId,
    name: current.name, unitPrice: Number(current.unitPrice) + 1000, expectedVersion: current.version })).product;
  current = await activate(current, seller2);
  expect(current.unitPrice).toBe('11000');
  save('price-fixture.json', { productId: current.id, status: current.status, currentPrice: current.unitPrice,
    cartPrice: (await cart()).items.find(i => i.productId === current.id).unitPriceSnapshot });
  await rejected('Catalog repriced after Cart', body, randomUUID(), 'Product price changed since adding to cart');
});
test('insufficient-stock two Store batch rejects without partial reservation', async () => {
  const body = bodyFor(await fillCart(2));
  const originalStock = (await ok(`/api/seller/products/${lines[1].productId}`, seller2)).available;
  await ok(`/api/seller/products/${lines[1].productId}/stock`, seller2, 'PUT', { available: 1 });
  try { await rejected('Insufficient second Store stock', body, randomUUID(), 'Not enough stock', 'stock'); }
  finally {
    // Restore only this run's synthetic Product available stock through its actual owner API.
    await ok(`/api/seller/products/${lines[1].productId}/stock`, seller2, 'PUT', { available: originalStock });
  }
});
test('product-inactive one unsellable Store line rejects the whole Checkout', async () => {
  const body = bodyFor(await fillCart());
  const current = (await ok(`/api/seller/products/${lines[1].productId}`, seller2)).product;
  const inactive = await ok(`/api/seller/products/${current.id}/deactivate`, seller2, 'POST', { expectedVersion: current.version });
  expect(inactive.product.status).toBe('INACTIVE');
  await rejected('Inactive Product after Cart', body, randomUUID(), 'Product, seller or store not available');
});
