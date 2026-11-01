// Replays V7 against real Auth for a Seller suspended by the build before it. This deletes rows, so it starts its own
// stack and removes it with its volumes whatever happens; it refuses any other project. Stop other demo stacks first:
// they use the same ports. Usage: node scripts/demo/rehearse-v7-upgrade.mjs [--skip-build]
import { execSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { newProject, requireDisposable } from './disposable.mjs';

const root = resolve(import.meta.dirname, '../..');
const project = process.env.COMPOSE_PROJECT_NAME ?? newProject();
const sh = (command, stdio = 'pipe') =>
  execSync(command, { cwd: root, stdio, env: { ...process.env, COMPOSE_PROJECT_NAME: project } })?.toString().trim();
requireDisposable(project, sh(`docker ps -aq --filter label=com.docker.compose.project=${project}`).split('\n').filter(Boolean));

const gateway = process.env.GATEWAY_URL ?? 'http://localhost:18080';
const wait = (ms) => new Promise((done) => setTimeout(done, ms));
let fails = 0;
function check(name, actual, expected) {
  const ok = actual === expected;
  if (!ok) fails += 1;
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name} -> ${actual}${ok ? '' : ` (expected ${expected})`}`);
}

try {
  sh(`node scripts/demo/up.mjs --approved-inventory-pair --approved-finance-policy${process.argv.includes('--skip-build') ? ' --skip-build' : ''}`, 'inherit');
  const env = Object.fromEntries(readFileSync(resolve(root, '.env.demo'), 'utf8').split('\n')
    .filter((line) => line.includes('=') && !line.startsWith('#'))
    .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1).trim()]));
  const call = async (path, { token, method = 'GET', body } = {}) => {
    const response = await fetch(gateway + path, {
      method,
      headers: {
        Origin: env.ALLOWED_ORIGINS.split(',')[0], 'X-CSRF-Protection': '1', 'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    return { status: response.status, json: text ? JSON.parse(text) : null };
  };
  const login = async (email, password = env.SEED_PASSWORD) =>
    (await call('/api/auth/login', { method: 'POST', body: { email, password } })).json.accessToken;
  const seller2 = () => login('seller2@hk5.local');
  const claims = (token) => JSON.parse(Buffer.from(token.split('.')[1], 'base64url'));
  const sql = (database, statement) => sh(`docker exec ${project}-postgres-1 psql -U hk5 -d ${database} -Atc "${statement}"`);

  const admin = await login(env.ADMIN_BOOTSTRAP_EMAIL, env.ADMIN_BOOTSTRAP_PASSWORD);
  const token = await seller2();
  const { seller } = (await call('/api/sellers/me', { token })).json;
  check('suspend', (await call(`/api/admin/sellers/${seller.id}/suspend`, {
    token: admin, method: 'POST', body: { expectedVersion: seller.version, reason: 'Rehearsal' },
  })).status, 200);
  for (let tries = 0; tries < 30 && sql('seller_db', 'select count(*) from seller_outbox where delivered_at is null') !== '0'; tries += 1) await wait(500);

  // What the old build left: suspended at Seller, role already gone at Auth, V7 still to run.
  sql('auth_db', `delete from account_role_grants where account_id = '${claims(token).sub}' and role = 'SELLER'`);
  sql('seller_db', `delete from flyway_schema_history where script = 'V7__resend_suspended_seller_role.sql'`);
  const stranded = await seller2();
  check('role gone before V7', claims(stranded).roles.includes('SELLER'), false);
  check('order list refused before V7', (await call('/api/seller/orders', { token: stranded })).status, 403);

  sh(`docker restart ${project}-seller-service-1`);
  let restored = false;
  for (let tries = 0; tries < 120 && !restored; tries += 1) {
    restored = claims(await seller2()).roles.includes('SELLER');
    if (!restored) await wait(1000);
  }
  check('Auth gets the role back from V7', restored, true);
  check('order list open again', (await call('/api/seller/orders', { token: await seller2() })).status, 200);
} finally {
  sh('docker compose --env-file .env.demo -f docker/compose.demo.yml down -v', 'inherit');
}

console.log(`FAILS=${fails}`);
process.exit(fails ? 1 : 0);
