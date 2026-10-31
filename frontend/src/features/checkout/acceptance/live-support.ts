import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { expect, type APIResponse, type Page } from '@playwright/test';

export interface Line { productId: string; sku: string; storeId: string; quantity: number }
export interface Balance { sku: string; available: number; reserved: number }
export interface StoreOrder { id: string; storeId: string; status: string; items: { productId: string; sku: string; quantity: number }[] }
export interface Order { id: string; storeOrders: StoreOrder[] }
export interface Submission {
  expectedItems: { productId: string; quantity: number }[];
  expectedRevision: number;
  shipping: { name: string; phone: string; address: string };
}
export interface Journal { id: string; type: string; lines: { sku: string; quantity: number }[]; fingerprint: string }
export interface OrderProof { orders: number; sellerOrders: number; placedEvents: number; customerId: string; state: string; journals: Journal[] }
export interface Operation { id: string; type: string; hash: string }
export const shipping = { name: 'TV3 acceptance', phone: '0901234567', address: 'TV3 isolated test address' };

export function required(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`NOT_RUN prerequisite missing: ${name}`);
  return value;
}
export function uuid(value: string): string {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)) throw new Error('Expected UUID fixture identity');
  return value;
}
export function baseUrl(): string {
  const url = new URL(required('TV3_E2E_BASE_URL'));
  if (!['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname) || url.username || url.password || url.pathname !== '/') {
    throw new Error('Use an explicitly supplied loopback disposable stack origin');
  }
  return url.origin;
}
export async function resource(page: Page, token: string, path: string, method = 'GET', data?: unknown, key?: string): Promise<APIResponse> {
  if (!path.startsWith('/api/')) throw new Error('Only actual public Gateway APIs are allowed');
  try {
    return await page.request.fetch(baseUrl() + path, {
      method, data, timeout: 15_000, failOnStatusCode: false,
      headers: { Authorization: `Bearer ${token}`, ...(key ? { 'Idempotency-Key': key } : {}) },
    });
  } catch {
    // Do not leak the real Authorization header through Playwright request-error diagnostics.
    throw new Error(`Real HTTP ${method} ${path} unavailable`);
  }
}
export async function json<T>(response: APIResponse, status = 200): Promise<T> {
  expect(response.status()).toBe(status);
  return await response.json() as T;
}
export async function login(page: Page, role: 'CUSTOMER' | 'SELLER' = 'CUSTOMER'): Promise<string> {
  baseUrl();
  const prefix = role === 'CUSTOMER' ? 'TV3_E2E_CUSTOMER' : 'TV3_E2E_SELLER_B';
  await page.goto('/login');
  await page.getByLabel('Email', { exact: true }).fill(required(`${prefix}_EMAIL`));
  await page.getByLabel('Mật khẩu', { exact: true }).fill(required(`${prefix}_PASSWORD`));
  const pending = page.waitForResponse(r => new URL(r.url()).pathname === '/api/auth/login' && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  const response = await pending;
  expect(response.status()).toBe(200);
  const body = await response.json() as { accessToken: string };
  expect(typeof body.accessToken).toBe('string');
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).waitFor();
  const me = await json<{ roles: string[] }>(await resource(page, body.accessToken, '/api/auth/me'));
  expect(me.roles).toContain(role);
  return body.accessToken;
}

// Test-only read oracle: BEGIN READ ONLY, fixed SELECTs, no schema/data/role changes.
// Container/user names are supplied by the operator, never discovered from secret files.
function identifier(name: string): string {
  const value = required(name);
  if (!/^[a-zA-Z0-9_.-]+$/.test(value)) throw new Error(`Invalid ${name}`);
  return value;
}
function literal(value: string): string { return "'" + value.replaceAll("'", "''") + "'"; }
function query<T>(database: 'order' | 'inventory', select: string): T {
  const output = execFileSync('docker', [
    '--host=unix:///var/run/docker.sock', 'exec', '-i', identifier('TV3_E2E_PG_CONTAINER'),
    'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', identifier('TV3_E2E_PG_USER'),
    '-d', identifier(database === 'order' ? 'TV3_E2E_ORDER_DB' : 'TV3_E2E_INVENTORY_DB'),
  ], { input: `BEGIN READ ONLY;\n${select};\nROLLBACK;\n`, encoding: 'utf8', timeout: 10_000, stdio: ['pipe', 'pipe', 'pipe'] });
  return JSON.parse(output.trim()) as T;
}
export function balances(lines: Line[]): Balance[] {
  const rows = query<Balance[]>('inventory', `select coalesce(json_agg(x order by x.sku),'[]'::json) from
    (select sku,available_quantity as available,reserved_quantity as reserved from inventory_item
     where sku in (${lines.map(l => literal(l.sku)).join(',')})) x`);
  for (const row of rows) {
    expect(Number.isSafeInteger(row.available) && row.available >= 0).toBe(true);
    expect(Number.isSafeInteger(row.reserved) && row.reserved >= 0).toBe(true);
  }
  return rows;
}
export function operations(orderId: string, storeIds: string[] = []): Operation[] {
  const ids = [`order-${uuid(orderId)}-reserve`, ...storeIds.flatMap(id => [
    `seller-order-${uuid(id)}-release`, `seller-order-${id}-consume`,
  ])];
  return query('inventory', `select coalesce(json_agg(x order by x.id),'[]'::json) from
    (select operation_id as id,operation_type as type,request_hash as hash from inventory_operation
     where operation_id in (${ids.map(literal).join(',')})) x`);
}
export function allOperations(): Operation[] {
  return query('inventory', `select coalesce(json_agg(x order by x.id),'[]'::json) from
    (select operation_id as id,operation_type as type,request_hash as hash from inventory_operation) x`);
}
export function customerOrderIds(customerId: string): string[] {
  return query('order', `select coalesce(json_agg(id order by id),'[]'::json) from orders where customer_id=${literal(uuid(customerId))}`);
}
export function orderProof(orderId: string): OrderProof {
  const id = literal(uuid(orderId));
  return query('order', `select json_build_object(
    'orders',(select count(*) from orders where id=${id}),
    'sellerOrders',(select count(*) from seller_orders where order_id=${id}),
    'placedEvents',(select count(*) from outbox_events where aggregate_id=${id} and topic='order.placed.v1'),
    'customerId',(select customer_id from checkout_attempts where id=${id}),
    'state',(select state from checkout_attempts where id=${id}),
    'journals',(select coalesce(json_agg(json_build_object('id',operation_id,'type',operation_type,
      'lines',canonical_lines,'fingerprint',payload_fingerprint) order by operation_id),'[]'::json)
      from inventory_operation_journal where checkout_attempt_id=${id}))`);
}
export function expectDelta(before: Balance[], after: Balance[], lines: Line[], available: number, reserved: number) {
  expect(before).toHaveLength(2);
  expect(after).toHaveLength(2);
  for (const line of lines) {
    const a = before.find(b => b.sku === line.sku)!;
    const b = after.find(b => b.sku === line.sku)!;
    expect(b.available).toBe(a.available + available * line.quantity);
    expect(b.reserved).toBe(a.reserved + reserved * line.quantity);
  }
}
export async function products(page: Page, token: string): Promise<Line[]> {
  const input = JSON.parse(required('TV3_E2E_PRODUCTS')) as { productId: string; quantity: number }[];
  if (!Array.isArray(input) || input.length !== 2) throw new Error('Supply exactly two real product IDs and quantities');
  const lines: Line[] = [];
  for (const item of input) {
    uuid(item.productId);
    if (!Number.isInteger(item.quantity) || item.quantity < 1 || item.quantity > 1000) throw new Error('Invalid fixture quantity');
    const p = await json<{ id: string; sku: string; store: { id: string }; available: number }>(
      await resource(page, token, `/api/catalog/products/${item.productId}`));
    expect(p.id).toBe(item.productId);
    expect(typeof p.sku).toBe('string');
    expect(p.sku.trim()).toBe(p.sku);
    expect(p.sku.length).toBeGreaterThan(0);
    expect(p.sku.length).toBeLessThanOrEqual(128);
    expect(p.sku).not.toBe(item.productId);
    expect(p.available).toBeGreaterThanOrEqual(item.quantity);
    lines.push({ ...item, sku: p.sku, storeId: uuid(p.store.id) });
  }
  expect(new Set(lines.map(l => l.productId)).size).toBe(2);
  expect(new Set(lines.map(l => l.sku)).size).toBe(2);
  expect(new Set(lines.map(l => l.storeId)).size).toBe(2);
  return lines;
}
export async function emptyCart(page: Page, token: string) {
  const cart = await json<{ claimed: boolean; items: unknown[] }>(await resource(page, token, '/api/cart'));
  expect(cart.claimed).toBe(false);
  expect(cart.items).toHaveLength(0); // Fail instead of deleting somebody else's Cart.
}
export async function placeOverHttp(page: Page, token: string, lines: Line[]) {
  await emptyCart(page, token);
  for (const line of lines) await json(await resource(page, token, '/api/cart/items', 'POST', { productId: line.productId, quantity: line.quantity }));
  const cart = await json<{ revision: number }>(await resource(page, token, '/api/cart'));
  const body: Submission = { expectedItems: lines.map(({ productId, quantity }) => ({ productId, quantity })), expectedRevision: cart.revision, shipping };
  const key = crypto.randomUUID(); // First submission only; retries below always reuse this exact key/body.
  const placed = await json<{ orderId: string }>(await resource(page, token, '/api/orders', 'POST', body, key));
  return { id: uuid(placed.orderId), key, body };
}
export async function detail(page: Page, token: string, id: string): Promise<Order> {
  return json<Order>(await resource(page, token, `/api/orders/${uuid(id)}`));
}
export async function assertPlaced(page: Page, token: string, id: string, lines: Line[]) {
  const proof = orderProof(id);
  expect(proof.orders).toBe(1);
  expect(proof.sellerOrders).toBe(2);
  expect(proof.placedEvents).toBe(1);
  expect(proof.state).toBe('SUCCEEDED');
  const order = await detail(page, token, id);
  expect(order.id).toBe(id);
  expect(order.storeOrders).toHaveLength(2);
  for (const line of lines) {
    const store = order.storeOrders.find(s => s.storeId === line.storeId)!;
    expect(store).toBeTruthy();
    expect(store.items).toHaveLength(1);
    expect(store.items[0]).toMatchObject({ productId: line.productId, sku: line.sku, quantity: line.quantity });
  }
  const ops = operations(id);
  expect(ops).toHaveLength(1);
  expect(ops[0]).toMatchObject({ id: `order-${id}-reserve`, type: 'RESERVE' });
  return order;
}
export function faultFixture(kind: 'claim-conflict' | 'timeout') {
  const path = required(kind === 'claim-conflict' ? 'TV3_E2E_CLAIM_CASE_FILE' : 'TV3_E2E_TIMEOUT_CASE_FILE');
  const input = JSON.parse(readFileSync(path, 'utf8')) as {
    kind: string; attemptId: string; operationId: string; key: string; body: Submission; lines: Line[];
    evidenceFile: string; evidenceSha256: string; faultStillActive: boolean; settledBeforeReplay: boolean;
  };
  expect(input.kind).toBe(kind);
  uuid(input.attemptId);
  expect(input.operationId).toBe(`order-${input.attemptId}-reserve`);
  expect(input.lines).toHaveLength(2);
  expect(input.faultStillActive).toBe(true);
  expect(input.settledBeforeReplay).toBe(true);
  expect(typeof input.key).toBe('string');
  expect(input.key.length).toBeGreaterThan(0);
  // Actual operator fault evidence is required; this suite never fabricates a 409/timeout endpoint.
  expect(createHash('sha256').update(readFileSync(input.evidenceFile)).digest('hex')).toBe(input.evidenceSha256);
  return input;
}
