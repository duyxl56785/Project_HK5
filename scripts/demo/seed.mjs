// Seeds the running local stack through the public API, so every business rule still applies.
// Safe to re-run: existing accounts, stores and products are reused.
// Usage: node scripts/demo/seed.mjs
import { randomBytes } from 'node:crypto';
import { appendFileSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const envFile = resolve(import.meta.dirname, '../../.env.demo');
const env = Object.fromEntries(readFileSync(envFile, 'utf8').split('\n')
  .filter((line) => line.includes('=') && !line.startsWith('#'))
  .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1).trim()]));
if (!env.SEED_PASSWORD) {
  env.SEED_PASSWORD = randomBytes(15).toString('base64url');
  appendFileSync(envFile, `SEED_PASSWORD=${env.SEED_PASSWORD}\n`);
}

const gateway = process.env.GATEWAY_URL ?? 'http://localhost:18080';
const origin = env.ALLOWED_ORIGINS.split(',')[0];

async function call(path, { token, method = 'GET', body } = {}) {
  const response = await fetch(gateway + path, {
    method,
    headers: {
      Origin: origin,
      'X-CSRF-Protection': '1',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  const json = text ? JSON.parse(text) : null;
  return { status: response.status, ok: response.ok, json };
}

async function must(path, options) {
  const reply = await call(path, options);
  if (!reply.ok) throw new Error(`${options?.method ?? 'GET'} ${path} -> ${reply.status} ${JSON.stringify(reply.json)}`);
  return reply.json;
}

const login = async (email, password) => (await must('/api/auth/login', { method: 'POST', body: { email, password } })).accessToken;

async function account(email) {
  const created = await call('/api/auth/register', { method: 'POST', body: { email, password: env.SEED_PASSWORD } });
  if (!created.ok && created.status !== 409) throw new Error(`register ${email} -> ${created.status} ${JSON.stringify(created.json)}`);
  return login(email, env.SEED_PASSWORD);
}

async function approvedSeller(email, admin) {
  const token = await account(email);
  if ((await call('/api/sellers/me', { token })).status === 404) await must('/api/sellers', {
    token, method: 'POST', body: { representativeName: 'Nguyễn Văn An', phone: '0901234567', pickupAddress: '1 Lê Lợi, Quận 1, TP.HCM' },
  });
  const { seller } = await must('/api/sellers/me', { token });
  if (seller.status === 'PENDING_REVIEW') {
    await must(`/api/admin/sellers/${seller.id}/approve`, { token: admin, method: 'POST', body: { expectedVersion: seller.version } });
  }
  // The SELLER role reaches the account through Seller's outbox, a moment after approval.
  for (let tries = 0; tries < 40; tries += 1) {
    const fresh = await login(email, env.SEED_PASSWORD);
    if ((await must('/api/auth/me', { token: fresh })).roles.includes('SELLER')) return fresh;
    await new Promise((done) => setTimeout(done, 500));
  }
  throw new Error(`${email} never received the SELLER role`);
}

async function openStore(token, name, slug) {
  const found = await call('/api/stores/me', { token });
  const store = found.ok ? found.json : await must('/api/stores', { token, method: 'POST', body: { name, slug } });
  if (!store.open) await must(`/api/stores/${store.id}/open`, { token, method: 'PATCH', body: { open: true, expectedVersion: store.version } });
}

async function category(admin, name) {
  const rows = await must('/api/categories');
  return (rows.find((row) => row.name === name) ?? await must('/api/categories', { token: admin, method: 'POST', body: { name, parentId: null } })).id;
}

async function product(token, admin, categoryId, item) {
  const existing = (await must(`/api/seller/products?size=100&term=${encodeURIComponent(item.sku)}`, { token })).products.find((p) => p.sku === item.sku);
  if (existing) return;
  const { product: created } = await must('/api/seller/products', { token, method: 'POST', body: { ...item, categoryId, initialStock: 50 } });
  const submitted = await must(`/api/seller/products/${created.id}/submit`, { token, method: 'POST', body: { expectedVersion: created.version } });
  const version = (submitted.product ?? submitted).version;
  await must(`/api/admin/products/${created.id}/approve`, { token: admin, method: 'POST', body: { expectedVersion: version, reason: 'Seed data' } });
}

const admin = await login(env.ADMIN_BOOTSTRAP_EMAIL, env.ADMIN_BOOTSTRAP_PASSWORD);
await account('customer@hk5.local');
const fashion = await category(admin, 'Thời trang');
const home = await category(admin, 'Nhà cửa');
const first = await approvedSeller('seller1@hk5.local', admin);
const second = await approvedSeller('seller2@hk5.local', admin);
await openStore(first, 'Tiệm Áo Sài Gòn', 'tiem-ao-sai-gon');
await openStore(second, 'Nhà Xinh', 'nha-xinh');
const shirt = { sku: 'AO-001', name: 'Áo thun cotton', unitPrice: '150000', description: 'Áo thun cotton 100%, form rộng.' };
const jeans = { sku: 'QUAN-001', name: 'Quần jean ống đứng', unitPrice: '420000', description: 'Quần jean co giãn nhẹ.' };
const lamp = { sku: 'DEN-001', name: 'Đèn bàn gỗ', unitPrice: '280000', description: 'Đèn bàn gỗ sồi, bóng LED ấm.' };
await product(first, admin, fashion, shirt);
await product(first, admin, fashion, jeans);
await product(second, admin, home, lamp);

console.log('Seeded customer@hk5.local, seller1@hk5.local, seller2@hk5.local (password: SEED_PASSWORD in .env.demo).');
console.log(`Admin: ${env.ADMIN_BOOTSTRAP_EMAIL} (password: ADMIN_BOOTSTRAP_PASSWORD in .env.demo).`);
