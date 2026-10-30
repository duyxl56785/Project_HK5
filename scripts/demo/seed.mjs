// Seeds the running local stack through the public API, so every business rule still applies.
// Safe to re-run: existing accounts, stores and products are reused.
// Usage: node scripts/demo/seed.mjs
import { randomBytes } from 'node:crypto';
import { appendFileSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { deflateSync } from 'node:zlib';

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
  const known = await call('/api/auth/login', { method: 'POST', body: { email, password: env.SEED_PASSWORD } });
  if (known.ok) return known.json.accessToken;
  await must('/api/auth/register', { method: 'POST', body: { email, password: env.SEED_PASSWORD } });
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
    // /me reads current roles but a token keeps those it was minted with, so log in again once the role shows.
    if ((await must('/api/auth/me', { token: fresh })).roles.includes('SELLER')) return login(email, env.SEED_PASSWORD);
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

const crc = (bytes) => {
  let value = ~0;
  for (const byte of bytes) {
    value ^= byte;
    for (let bit = 0; bit < 8; bit += 1) value = value & 1 ? (value >>> 1) ^ 0xedb88320 : value >>> 1;
  }
  return ~value >>> 0;
};

function chunk(type, data) {
  const body = Buffer.concat([Buffer.from(type), data]);
  const frame = Buffer.alloc(8 + data.length + 4);
  frame.writeUInt32BE(data.length);
  body.copy(frame, 4);
  frame.writeUInt32BE(crc(body), 8 + data.length);
  return frame;
}

// shortcut: placeholder art drawn from the hue, replace with real product photos if the demo needs them.
function placeholderPng(hue) {
  const size = 400;
  const channel = (n, light) => {
    const k = (n + hue / 30) % 12;
    return Math.round(255 * (light - 0.3 * Math.min(light, 1 - light) * Math.max(-1, Math.min(k - 3, 9 - k, 1))));
  };
  const rows = Buffer.alloc((size * 3 + 1) * size);
  for (let y = 0, at = 0; y < size; y += 1) {
    rows[at++] = 0;
    for (let x = 0; x < size; x += 1) {
      const inside = (x - 200) ** 2 + (y - 200) ** 2 < 120 ** 2;
      const light = inside ? 0.94 : 0.76 + (0.12 * (x + y)) / (2 * size);
      for (const n of [0, 8, 4]) rows[at++] = channel(n, light);
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(size, 0);
  header.writeUInt32BE(size, 4);
  header.set([8, 2], 8);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', header),
    chunk('IDAT', deflateSync(rows)), chunk('IEND', Buffer.alloc(0))]);
}

async function image(token, id, sku, hue) {
  const { images } = await must(`/api/seller/products/${id}`, { token });
  if (images.length > 0) return;
  const form = new FormData();
  form.append('files', new Blob([placeholderPng(hue)], { type: 'image/png' }), `${sku}.png`);
  const response = await fetch(`${gateway}/api/seller/products/${id}/images`, {
    method: 'POST', headers: { Origin: origin, 'X-CSRF-Protection': '1', Authorization: `Bearer ${token}` }, body: form,
  });
  if (!response.ok) throw new Error(`upload ${sku} -> ${response.status} ${await response.text()}`);
}

async function product(token, admin, categoryId, item, hue) {
  const existing = (await must(`/api/seller/products?size=100&term=${encodeURIComponent(item.sku)}`, { token })).products.find((p) => p.sku === item.sku);
  if (existing) return image(token, existing.id, item.sku, hue);
  const { product: created } = await must('/api/seller/products', { token, method: 'POST', body: { ...item, categoryId, initialStock: 50 } });
  await image(token, created.id, item.sku, hue);
  const submitted = await must(`/api/seller/products/${created.id}/submit`, { token, method: 'POST', body: { expectedVersion: created.version } });
  const version = (submitted.product ?? submitted).version;
  await must(`/api/admin/products/${created.id}/approve`, { token: admin, method: 'POST', body: { expectedVersion: version, reason: 'Seed data' } });
}

const admin = await login(env.ADMIN_BOOTSTRAP_EMAIL, env.ADMIN_BOOTSTRAP_PASSWORD);
await account('customer@hk5.local');
const first = await approvedSeller('seller1@hk5.local', admin);
const second = await approvedSeller('seller2@hk5.local', admin);
const third = await approvedSeller('seller3@hk5.local', admin);
await openStore(first, 'Tiệm Áo Sài Gòn', 'tiem-ao-sai-gon');
await openStore(second, 'Nhà Xinh', 'nha-xinh');
await openStore(third, 'Góc Tiện Ích', 'goc-tien-ich');

const catalog = [
  ['Thời trang', first, [
    ['AO-001', 'Áo thun cotton', '150000', 'Áo thun cotton 100%, form rộng.'],
    ['QUAN-001', 'Quần jean ống đứng', '420000', 'Quần jean co giãn nhẹ.'],
    ['AO-002', 'Áo sơ mi linen', '320000', 'Áo sơ mi linen thoáng mát, tay dài.'],
    ['VAY-001', 'Chân váy xếp ly', '260000', 'Chân váy xếp ly dáng dài, lưng thun.'],
  ]],
  ['Phụ kiện', first, [
    ['TUI-001', 'Túi tote canvas', '120000', 'Túi tote canvas dày, có ngăn trong.'],
    ['NON-001', 'Nón lưỡi trai', '95000', 'Nón kaki, khóa điều chỉnh phía sau.'],
    ['VI-001', 'Ví da mini', '210000', 'Ví da bò gọn nhẹ, nhiều ngăn thẻ.'],
  ]],
  ['Nhà cửa', second, [
    ['DEN-001', 'Đèn bàn gỗ', '280000', 'Đèn bàn gỗ sồi, bóng LED ấm.'],
    ['GOI-001', 'Gối tựa sofa', '135000', 'Gối tựa vải bố, vỏ tháo giặt được.'],
    ['KE-001', 'Kệ gỗ treo tường', '240000', 'Kệ gỗ thông ba tầng, kèm phụ kiện lắp.'],
  ]],
  ['Mẹ và bé', second, [
    ['BE-001', 'Bình sữa thủy tinh', '185000', 'Bình sữa thủy tinh 240ml, núm silicon.'],
    ['BE-002', 'Khăn sữa cotton', '75000', 'Bộ 10 khăn sữa cotton mềm.'],
  ]],
  ['Thực phẩm', second, [
    ['TP-001', 'Cà phê rang xay', '110000', 'Cà phê rang mộc, gói 250g.'],
    ['TP-002', 'Hạt điều rang muối', '165000', 'Hạt điều Bình Phước, hũ 500g.'],
  ]],
  ['Điện tử', third, [
    ['DT-001', 'Tai nghe Bluetooth', '390000', 'Tai nghe Bluetooth 5.3, pin 24 giờ.'],
    ['DT-002', 'Sạc dự phòng 10000mAh', '250000', 'Sạc nhanh 20W, hai cổng ra.'],
    ['DT-003', 'Chuột không dây', '175000', 'Chuột không dây yên tĩnh, pin AA.'],
  ]],
  ['Làm đẹp', third, [
    ['LD-001', 'Sữa rửa mặt dịu nhẹ', '145000', 'Sữa rửa mặt 150ml cho da nhạy cảm.'],
    ['LD-002', 'Kem chống nắng SPF50', '230000', 'Kem chống nắng 50ml, không nhờn.'],
  ]],
  ['Thể thao', third, [
    ['TT-001', 'Thảm tập yoga', '220000', 'Thảm TPE dày 6mm, chống trượt.'],
    ['TT-002', 'Bình nước 1 lít', '130000', 'Bình nước nhựa Tritan, có vạch chia.'],
  ]],
  ['Sách', third, [
    ['SACH-001', 'Sổ tay bìa cứng', '65000', 'Sổ tay A5 200 trang giấy kem.'],
    ['SACH-002', 'Sách học lập trình', '195000', 'Sách nhập môn lập trình cho người mới.'],
    ['SACH-003', 'Bút gel 12 màu', '85000', 'Bộ bút gel 12 màu, ngòi 0.5mm.'],
  ]],
];
for (const [index, [name, seller, items]] of catalog.entries()) {
  const categoryId = await category(admin, name);
  for (const [sku, label, unitPrice, description] of items) {
    await product(seller, admin, categoryId, { sku, name: label, unitPrice, description }, index * 40);
  }
}

console.log('Seeded customer@hk5.local, seller1@hk5.local, seller2@hk5.local, seller3@hk5.local (password: SEED_PASSWORD in .env.demo).');
console.log(`Admin: ${env.ADMIN_BOOTSTRAP_EMAIL} (password: ADMIN_BOOTSTRAP_PASSWORD in .env.demo).`);
