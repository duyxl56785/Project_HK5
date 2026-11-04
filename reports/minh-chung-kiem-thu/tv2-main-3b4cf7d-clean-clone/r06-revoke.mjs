// R06 on a disposable demo stack: revoke between two calls with the same still-unexpired access token.
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
const env = Object.fromEntries(readFileSync('D:/hk5c/.env.demo', 'utf8').split(/\r?\n/).filter((l) => l.includes('=')).map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
const gw = 'http://127.0.0.1:18080';
const H = { Origin: 'http://localhost:5173', 'X-CSRF-Protection': '1', 'Content-Type': 'application/json' };
const call = async (path, { token, method = 'GET', body } = {}) => {
  const r = await fetch(gw + path, { method, headers: { ...H, ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body && JSON.stringify(body) });
  let json = null; try { json = await r.json(); } catch {}
  return { status: r.status, code: json?.code ?? json?.error ?? '', json };
};
const login = async (email, password) => (await call('/api/auth/login', { method: 'POST', body: { email, password } })).json.accessToken;
const psql = (sql) => execFileSync('docker', ['exec', 'hk5-clean-postgres-1', 'psql', '-U', 'hk5', '-d', 'auth_db', '-tAc', sql]).toString().trim();
const row = (label, r) => console.log(`${label.padEnd(58)} ${r.status} ${r.code}`);
const ts = Date.now();

const admin = await login('admin@hk5.local', env.ADMIN_BOOTSTRAP_PASSWORD);
row('A1 admin GET /api/admin/accounts (before)', await call('/api/admin/accounts', { token: admin }));
row('A2 admin POST /api/categories (before)', await call('/api/categories', { token: admin, method: 'POST', body: { name: `R06 before ${ts}`, parentId: null } }));
console.log('DB  admin status ->', psql("update accounts set status='DISABLED' where email='admin@hk5.local' returning status"));
try {
  row('A3 same admin token GET /api/admin/accounts (after)', await call('/api/admin/accounts', { token: admin }));
  row('A4 same admin token POST /api/categories (after)', await call('/api/categories', { token: admin, method: 'POST', body: { name: `R06 after ${ts}`, parentId: null } }));
  row('A5 same admin token GET /api/admin/products (after, read)', await call('/api/admin/products', { token: admin }));
  row('A6 admin re-login (after)', await call('/api/auth/login', { method: 'POST', body: { email: 'admin@hk5.local', password: env.ADMIN_BOOTSTRAP_PASSWORD } }));
} finally {
  console.log('DB  admin status ->', psql("update accounts set status='ACTIVE' where email='admin@hk5.local' returning status"));
}
const after = psql(`select count(*) from pg_stat_database where datname='auth_db'`);
const admin2 = await login('admin@hk5.local', env.ADMIN_BOOTSTRAP_PASSWORD);
const seller = await login('seller1@hk5.local', env.SEED_PASSWORD);
const sellerId = (await call('/api/auth/me', { token: seller })).json.userId;
row('S1 seller GET /api/seller/products (before)', await call('/api/seller/products?size=1', { token: seller }));
row('S2 admin disables seller1 via API', await call(`/api/admin/accounts/${sellerId}/disable`, { token: admin2, method: 'POST' }));
try {
  row('S3 same seller token POST /api/seller/products (after, write)', await call('/api/seller/products', { token: seller, method: 'POST', body: { sku: `R06-${ts}`, title: 'R06 probe', price: '1000' } }));
  row('S4 same seller token GET /api/seller/products (after, read)', await call('/api/seller/products?size=1', { token: seller }));
} finally {
  row('S5 admin re-enables seller1', await call(`/api/admin/accounts/${sellerId}/enable`, { token: admin2, method: 'POST' }));
}
void after;
