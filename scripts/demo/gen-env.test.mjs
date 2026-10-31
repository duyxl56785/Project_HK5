// Run with: node --test scripts/demo/gen-env.test.mjs
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import test from 'node:test';

function sandbox(context) {
  const root = mkdtempSync(join(tmpdir(), 'hk5-gen-env-'));
  context.after(() => rmSync(root, { recursive: true, force: true }));
  mkdirSync(join(root, 'scripts/demo'), { recursive: true });
  copyFileSync(resolve(import.meta.dirname, 'gen-env.mjs'), join(root, 'scripts/demo/gen-env.mjs'));
  const generate = (...flags) => execFileSync(process.execPath, ['scripts/demo/gen-env.mjs', ...flags], { cwd: root, stdio: 'pipe' });
  const read = () => readFileSync(join(root, '.env.demo'), 'utf8');
  const gates = () => ['INVENTORY_ORDER_PAIR_ENABLED', 'HK5_FINANCE_POLICY_APPROVED'].map((key) => read().match(new RegExp(`^${key}=(.*)$`, 'm'))[1]);
  return { root, generate, read, gates };
}

test('a fresh file leaves both gates off', (context) => {
  const { generate, gates } = sandbox(context);
  generate();
  assert.deepEqual(gates(), ['false', 'false']);
});

test('each flag turns on only its own gate', (context) => {
  const { generate, gates } = sandbox(context);
  generate('--approved-inventory-pair');
  assert.deepEqual(gates(), ['true', 'false']);
  generate('--approved-finance-policy');
  assert.deepEqual(gates(), ['false', 'true']);
  generate('--approved-inventory-pair', '--approved-finance-policy');
  assert.deepEqual(gates(), ['true', 'true']);
});

test('a file left on by an earlier run goes off without a flag and keeps its secrets', (context) => {
  const { root, generate, read, gates } = sandbox(context);
  generate();
  const legacy = read().replace(/^(INVENTORY_ORDER_PAIR_ENABLED|HK5_FINANCE_POLICY_APPROVED)=false$/gm, '$1=true');
  writeFileSync(join(root, '.env.demo'), legacy);
  generate();
  assert.deepEqual(gates(), ['false', 'false']);
  assert.equal(read().match(/^JWT_HS256_CURRENT_KEY_B64=.*$/m)[0], legacy.match(/^JWT_HS256_CURRENT_KEY_B64=.*$/m)[0]);
});

test('the old combined flag no longer opens anything', (context) => {
  const { generate, gates } = sandbox(context);
  generate('--approved-local-policy');
  assert.deepEqual(gates(), ['false', 'false']);
});
