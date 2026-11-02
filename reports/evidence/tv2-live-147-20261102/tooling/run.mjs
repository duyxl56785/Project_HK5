// Runs the TV3 live suites against the disposable tv3-live-147 stack. Never prints credential values.
import { spawnSync, execFileSync } from 'node:child_process';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';

const here = import.meta.dirname;
const tree = 'D:/monhoc/CNPM/project_hk5/.worktrees/m147';
const frontend = `${tree}/frontend`;
const base = 'http://localhost:5173';
const project = 'tv3-live-147';
const head = 'b4e869dace9d03d4fbb27331d6194b11bb083bc9';
const env = Object.fromEntries(readFileSync(`${tree}/.env.demo`, 'utf8').split('\n')
  .filter((line) => line.includes('=') && !line.startsWith('#'))
  .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1).trim()]));
const headers = { Origin: base, 'X-CSRF-Protection': '1', 'Content-Type': 'application/json' };
const call = async (path, token, init = {}) => {
  const response = await fetch(base + path, { ...init, headers: { ...headers, ...(token ? { Authorization: `Bearer ${token}` } : {}) } });
  if (!response.ok) throw new Error(`${path} -> ${response.status}`);
  return response.json();
};
const login = async (email, password) => (await call('/api/auth/login', null, { method: 'POST', body: JSON.stringify({ email, password }) })).accessToken;

const seller2 = await login('seller2@hk5.local', env.SEED_PASSWORD);
const owned = (await call('/api/seller/products?size=100', seller2)).products.filter((p) => p.status === 'ACTIVE');
const seller1 = await login('seller1@hk5.local', env.SEED_PASSWORD);
const first = (await call('/api/seller/products?size=100', seller1)).products.find((p) => p.status === 'ACTIVE');
const second = owned[0];
const compose = JSON.parse(execFileSync('docker', ['inspect', `${project}-postgres-1`], { encoding: 'utf8' }))[0].Config.Labels['com.docker.compose.project.working_dir'];

const shared = {
  ...process.env,
  NODE_OPTIONS: `--require ${here.replaceAll('\\', '/')}/docker-shim.cjs`,
  TV3_E2E_BASE_URL: base,
  TV3_E2E_CUSTOMER_EMAIL: 'customer@hk5.local',
  TV3_E2E_CUSTOMER_PASSWORD: env.SEED_PASSWORD,
  TV3_E2E_PRODUCTS: JSON.stringify([{ productId: first.id, quantity: 1 }, { productId: second.id, quantity: 1 }]),
  TV3_E2E_PG_CONTAINER: `${project}-postgres-1`,
  TV3_E2E_PG_USER: 'hk5',
  TV3_E2E_ORDER_DB: 'order_db',
  TV3_E2E_INVENTORY_DB: 'inventory_db',
  TV3_E2E_SELLER_B_EMAIL: 'seller2@hk5.local',
  TV3_E2E_SELLER_B_PASSWORD: env.SEED_PASSWORD,
  TV3_TV4_CONTRACT_REFERENCE: 'PR #135 at 3ec0b340, CONSUME at DISPATCHED',
  TV3_NEG_BASE_URL: base,
  TV3_NEG_PROJECT: project,
  TV3_NEG_COMPOSE_DIR: compose,
  TV3_NEG_PG_USER: 'hk5',
  TV3_NEG_CUSTOMER_EMAIL: 'customer@hk5.local',
  TV3_NEG_SELLER_A_EMAIL: 'seller1@hk5.local',
  TV3_NEG_SELLER_B_EMAIL: 'seller2@hk5.local',
  TV3_NEG_PASSWORD: env.SEED_PASSWORD,
  TV3_NEG_ADMIN_EMAIL: env.ADMIN_BOOTSTRAP_EMAIL,
  TV3_NEG_ADMIN_PASSWORD: env.ADMIN_BOOTSTRAP_PASSWORD,
  TV3_NEG_EVIDENCE: resolve(here, 'evidence/negative'),
  TV3_NEG_SOURCE_HEAD: head,
  TV3_NEG_DEPLOYED_HEAD: head,
  TV3_NEG_REFUSAL_CONTRACT: 'itemized147',
};
mkdirSync(resolve(here, 'evidence'), { recursive: true });

const runs = [
  ['two-store', ['src/features/checkout/acceptance/live.config.ts', '--project', 'two-store']],
  ['tv4-lifecycle', ['src/features/checkout/acceptance/live.config.ts', '--project', 'tv4-lifecycle']],
  ['negative', ['src/features/checkout/acceptance/negative.live.config.mjs']],
];
const summary = {};
for (const [name, args] of runs) {
  if (process.argv[2] && process.argv[2] !== name) continue;
  const result = spawnSync('npx', ['playwright', 'test', '--config', ...args], { cwd: frontend, env: shared, encoding: 'utf8', shell: true, timeout: 480_000 });
  const output = `${result.stdout}\n${result.stderr}`;
  for (const secret of [env.SEED_PASSWORD, env.ADMIN_BOOTSTRAP_PASSWORD]) if (output.includes(secret)) throw new Error(`${name} output carried a credential, not saved`);
  writeFileSync(resolve(here, `evidence/${name}.log`), output);
  summary[name] = result.status;
  console.log(name, 'exit', result.status);
  console.log(output.split('\n').slice(-14).join('\n'));
}
writeFileSync(resolve(here, 'evidence/summary.json'), JSON.stringify({ head, project, summary }, null, 2));
void createRequire;
