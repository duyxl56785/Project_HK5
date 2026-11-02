// Runs the TV3 live suites against the disposable tv3-live-uat18 stack. Never prints credential values.
import { spawnSync, execFileSync } from 'node:child_process';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const here = import.meta.dirname;
const tree = 'D:/monhoc/CNPM/project_hk5/.worktrees/uat18';
const frontend = `${tree}/frontend`;
const base = 'http://localhost:5173';
const project = 'tv3-live-uat18';
const head = '052388c84f378ee90732de5b37f7c7bef6fcc64f';
const env = Object.fromEntries(readFileSync(`${tree}/.env.demo`, 'utf8').split('\n')
  .filter((l) => l.includes('=') && !l.startsWith('#'))
  .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1).trim()]));
const headers = { Origin: base, 'X-CSRF-Protection': '1', 'Content-Type': 'application/json' };
const call = async (path, token, init = {}) => {
  const r = await fetch(base + path, { ...init, headers: { ...headers, ...(token ? { Authorization: `Bearer ${token}` } : {}) } });
  if (!r.ok) throw new Error(`${path} -> ${r.status}`);
  return r.json();
};
const login = async (email, password) => (await call('/api/auth/login', null, { method: 'POST', body: JSON.stringify({ email, password }) })).accessToken;
const s2 = await login('seller2@hk5.local', env.SEED_PASSWORD), s1 = await login('seller1@hk5.local', env.SEED_PASSWORD);
const second = (await call('/api/seller/products?size=100', s2)).products.find((p) => p.status === 'ACTIVE');
const first = (await call('/api/seller/products?size=100', s1)).products.find((p) => p.status === 'ACTIVE');
const compose = JSON.parse(execFileSync('docker', ['inspect', `${project}-postgres-1`], { encoding: 'utf8' }))[0].Config.Labels['com.docker.compose.project.working_dir'];

const shared = {
  ...process.env,
  NODE_OPTIONS: `--require ${resolve(here, '../live147/docker-shim.cjs').replaceAll('\\', '/')}`,
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
const suites = [
  ['two-store', ['--config', 'src/features/checkout/acceptance/live.config.ts', '--project', 'two-store']],
  ['tv4-lifecycle', ['--config', 'src/features/checkout/acceptance/live.config.ts', '--project', 'tv4-lifecycle']],
  ['negative', ['--config', 'src/features/checkout/acceptance/negative.live.config.mjs']],
];
const summary = {};
for (const [name, args] of suites) {
  const result = spawnSync('npx', ['playwright', 'test', ...args], { cwd: frontend, env: shared, encoding: 'utf8', shell: true, timeout: 480_000 });
  const output = `${result.stdout}\n${result.stderr}`;
  for (const secret of [env.SEED_PASSWORD, env.ADMIN_BOOTSTRAP_PASSWORD]) if (output.includes(secret)) throw new Error(`${name} output carried a credential, not saved`);
  writeFileSync(resolve(here, `evidence/${name}.log`), output);
  summary[name] = result.status;
  console.log(name, 'exit', result.status, '|', output.trim().split('\n').slice(-2).join(' | ').slice(0, 160));
}
writeFileSync(resolve(here, 'evidence/summary.json'), JSON.stringify({ head, project, summary }, null, 2));
