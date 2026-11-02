// Seeds an already-started isolated UAT stack through public APIs only.
// It never reads or writes .env.demo; Start-IsolatedUat supplies the unique env file.
import { readFileSync } from 'node:fs';

const envFile = process.env.HK5_UAT_ENV_FILE;
if (!envFile || !process.env.HK5_UAT_PROJECT?.startsWith('hk5-uat-')) {
  throw new Error('Refusing to seed without an isolated HK5_UAT_ENV_FILE and hk5-uat-* project.');
}
const env = Object.fromEntries(readFileSync(envFile, 'utf8').split('\n')
  .filter((line) => line.includes('=') && !line.startsWith('#'))
  .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1).trim()]));
const gateway = process.env.GATEWAY_URL;
if (!gateway?.startsWith('http://127.0.0.1:')) throw new Error('Isolated seeding requires a loopback GATEWAY_URL.');
const origin = env.ALLOWED_ORIGINS.split(',')[0];

async function call(path, { token, method = 'GET', body } = {}) {
  const response = await fetch(gateway + path, { method, headers: { Origin: origin, 'X-CSRF-Protection': '1', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await response.text();
  return { status: response.status, ok: response.ok, json: text ? JSON.parse(text) : null };
}
async function must(path, options) { const reply = await call(path, options); if (!reply.ok) throw new Error(`${options?.method ?? 'GET'} ${path} -> ${reply.status} ${JSON.stringify(reply.json)}`); return reply.json; }
const login = async (email, password) => (await must('/api/auth/login', { method: 'POST', body: { email, password } })).accessToken;
async function account(email) { const known = await call('/api/auth/login', { method: 'POST', body: { email, password: env.SEED_PASSWORD } }); if (known.ok) return known.json.accessToken; await must('/api/auth/register', { method: 'POST', body: { email, password: env.SEED_PASSWORD } }); return login(email, env.SEED_PASSWORD); }
async function approvedSeller(email, admin) {
  const token = await account(email);
  if ((await call('/api/sellers/me', { token })).status === 404) await must('/api/sellers', { token, method: 'POST', body: { representativeName: 'Nguyễn Văn An', phone: '0901234567', pickupAddress: '1 Lê Lợi, Quận 1, TP.HCM' } });
  const { seller } = await must('/api/sellers/me', { token });
  if (seller.status === 'PENDING_REVIEW') await must(`/api/admin/sellers/${seller.id}/approve`, { token: admin, method: 'POST', body: { expectedVersion: seller.version } });
  for (let tries = 0; tries < 40; tries += 1) { const fresh = await login(email, env.SEED_PASSWORD); if ((await must('/api/auth/me', { token: fresh })).roles.includes('SELLER')) return login(email, env.SEED_PASSWORD); await new Promise((done) => setTimeout(done, 500)); }
  throw new Error(`${email} never received the SELLER role`);
}
async function openStore(token, name, slug) { const found = await call('/api/stores/me', { token }); const store = found.ok ? found.json : await must('/api/stores', { token, method: 'POST', body: { name, slug } }); if (!store.open) await must(`/api/stores/${store.id}/open`, { token, method: 'PATCH', body: { open: true, expectedVersion: store.version } }); }
async function category(admin, name) { const rows = await must('/api/categories'); return (rows.find((row) => row.name === name) ?? await must('/api/categories', { token: admin, method: 'POST', body: { name, parentId: null } })).id; }
async function product(token, admin, categoryId, item) {
  // Product-image storage belongs to its own owner/PR. A two-Store UAT seed
  // needs a sellable Product, not an image upload; do not hide image failures.
  const existing = (await must(`/api/seller/products?size=100&term=${encodeURIComponent(item.sku)}`, { token })).products.find((p) => p.sku === item.sku);
  if (existing?.status === 'ACTIVE') return;
  const current = existing ?? (await must('/api/seller/products', { token, method: 'POST', body: { ...item, categoryId, initialStock: 50 } })).product;
  const submitted = await must(`/api/seller/products/${current.id}/submit`, { token, method: 'POST', body: { expectedVersion: current.version } });
  await must(`/api/admin/products/${current.id}/approve`, { token: admin, method: 'POST', body: { expectedVersion: (submitted.product ?? submitted).version, reason: 'UAT seed data' } });
}

const admin = await login(env.ADMIN_BOOTSTRAP_EMAIL, env.ADMIN_BOOTSTRAP_PASSWORD);
await account('customer@hk5.local');
const first = await approvedSeller('seller1@hk5.local', admin), second = await approvedSeller('seller2@hk5.local', admin), third = await approvedSeller('seller3@hk5.local', admin);
await openStore(first, 'Tiệm Áo Sài Gòn', 'tiem-ao-sai-gon'); await openStore(second, 'Nhà Xinh', 'nha-xinh'); await openStore(third, 'Góc Tiện Ích', 'goc-tien-ich');
const catalog = [['Thời trang', first, [['AO-001', 'Áo thun cotton', '150000', 'Áo thun cotton 100%, form rộng.'], ['QUAN-001', 'Quần jean ống đứng', '420000', 'Quần jean co giãn nhẹ.']]], ['Nhà cửa', second, [['DEN-001', 'Đèn bàn gỗ', '280000', 'Đèn bàn gỗ sồi, bóng LED ấm.'], ['GOI-001', 'Gối tựa sofa', '135000', 'Gối tựa vải bố.']]], ['Điện tử', third, [['DT-001', 'Tai nghe Bluetooth', '390000', 'Tai nghe Bluetooth 5.3.'], ['DT-002', 'Sạc dự phòng 10000mAh', '250000', 'Sạc nhanh 20W.']]]];
for (const [name, seller, items] of catalog) { const categoryId = await category(admin, name); for (const [sku, label, unitPrice, description] of items) await product(seller, admin, categoryId, { sku, name: label, unitPrice, description }); }
console.log('Seeded fake customer, admin and three sellers across three UAT Stores from the isolated env file.');
