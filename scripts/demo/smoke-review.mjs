// Checks the running stack for what source review cannot show: internal tokens, admin history access,
// the Nginx proxy and a PostgreSQL restart. It restarts postgres. Usage: node scripts/demo/smoke-review.mjs
import { execSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '../..');
const env = Object.fromEntries(readFileSync(resolve(root, '.env.demo'), 'utf8').split('\n')
  .filter((line) => line.includes('=') && !line.startsWith('#'))
  .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1).trim()]));
const gateway = process.env.GATEWAY_URL ?? 'http://localhost:18080';
const site = process.env.E2E_BASE_URL ?? 'http://localhost:5173';
const project = process.env.COMPOSE_PROJECT_NAME ?? 'hk5-demo';
const origin = env.ALLOWED_ORIGINS.split(',')[0];
const sh = (command) => execSync(command, { stdio: ['ignore', 'pipe', 'pipe'] }).toString();
const wait = (ms) => new Promise((done) => setTimeout(done, ms));

let fails = 0;
function check(name, actual, expected) {
  const ok = actual === expected;
  if (!ok) fails += 1;
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name} -> ${actual}${ok ? '' : ` (expected ${expected})`}`);
}

async function call(path, { token, method = 'GET', body } = {}) {
  const response = await fetch(gateway + path, {
    method,
    headers: {
      Origin: origin, 'X-CSRF-Protection': '1',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  try {
    return { status: response.status, json: text ? JSON.parse(text) : null };
  } catch {
    return { status: response.status, json: null };
  }
}
const login = async (email, password) => (await call('/api/auth/login', { method: 'POST', body: { email, password } })).json?.accessToken;

// Internal routes are not on the host, so call them from inside the Docker network.
const inside = (service, token) => Number(sh(`docker exec ${project}-api-gateway-1 curl -s -o /dev/null -w "%{http_code}" -m 5 -X POST `
  + `-H "Content-Type: application/json" ${token ? `-H "X-Internal-Token: ${token}" ` : ''}-d "{}" http://${service}/internal/cart/claim`));
check('cart claim without a token', inside('cart-service:8106'), 401);
check('cart claim with a wrong token', inside('cart-service:8106', 'wrong'), 401);
check('gateway does not route internal paths', (await call('/internal/cart/claim', { method: 'POST', body: {} })).status >= 400, true);

const admin = await login(env.ADMIN_BOOTSTRAP_EMAIL, env.ADMIN_BOOTSTRAP_PASSWORD);
const customer = await login('customer@hk5.local', env.SEED_PASSWORD);
const seller = await login('seller1@hk5.local', env.SEED_PASSWORD);
const { seller: profile } = (await call('/api/sellers/me', { token: seller })).json;
const history = `/api/admin/sellers/${profile.id}/history`;
check('history without a token', (await call(history)).status, 401);
check('history as customer', (await call(history, { token: customer })).status, 403);
check('history as seller', (await call(history, { token: seller })).status, 403);
const rows = await call(history, { token: admin });
check('history as admin', rows.status, 200);
check('history has the approval', rows.json.at(-1).toStatus, 'APPROVED');

const deepLink = await fetch(`${site}/orders`);
check('deep link served by Nginx', deepLink.status, 200);
check('deep link is the app', (await deepLink.text()).includes('id="root"'), true);
check('Nginx proxies /api', (await fetch(`${site}/api/categories`)).status, 200);

const inspect = sh(`docker inspect ${project}-postgres-1 ${project}-auth-service-1 ${project}-order-service-1`);
check('secrets absent from docker inspect', [env.POSTGRES_PASSWORD, env.AUTH_DB_PASSWORD, env.ORDER_CART_TOKEN].some((value) => inspect.includes(value)), false);

const categories = (await call('/api/categories')).json.length;
sh(`docker restart ${project}-postgres-1`);
let back = false;
for (let tries = 0; tries < 40 && !back; tries += 1) {
  await wait(1500);
  back = await login('customer@hk5.local', env.SEED_PASSWORD).then(Boolean, () => false);
}
check('login works after PostgreSQL restart', back, true);
check('data survives PostgreSQL restart', (await call('/api/categories')).json.length, categories);

console.log(`FAILS=${fails}`);
process.exit(fails ? 1 : 0);
