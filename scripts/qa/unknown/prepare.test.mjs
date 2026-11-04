import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { validate, overlay, prepare } from './prepare.mjs';
import { spawnSync } from 'node:child_process';
const valid = { case: 'lost-response', project: 'hk5-unknown-20261104120000', operationId: 'fixed-operation', bodySha256: 'a'.repeat(64) };
test('shared names, traversal, unsupported cases and unbound requests fail before provisioning', () => {
  for (const patch of [{ project: 'hk5-demo' }, { project: '../hk5-unknown-20261104120000' }, { case: 'claim-conflict' }, { operationId: 'x\n' }, { bodySha256: 'unknown' }]) assert.throws(() => validate({ ...valid, ...patch }));
});
test('overlay changes only private pair/Order transport and publishes no host port', () => {
  const text = overlay(valid, 'C:/isolated/plan.json', 'C:/source/helper.mjs');
  assert.match(text, /network_mode: "service:inventory-service"/);
  assert.match(text, /HK5_UNKNOWN_PAIR_OPT_IN:-false/);
  assert.match(text, /HK5_UNKNOWN_INVENTORY_URL:-http:\/\/inventory-service:8105/);
  assert.match(text, /profiles: \["unknown-fault"\]/);
  assert.doesNotMatch(text, /ports:|privileged:|HK5_FINANCE_POLICY_APPROVED/);
  assert.match(text, /read_only: true/);
});
test('prepare creates separate credentials and a disabled exact-source plan; refuses reuse', () => {
  const temp = mkdtempSync(join(tmpdir(), 'hk5-unknown-config-test-'));
  try {
    const output = join(temp, valid.project);
    const result = prepare({ ...valid, output });
    assert.equal(result.executionApproved, false);
    assert.match(result.sourceSha, /^[a-f0-9]{40}$/);
    const plan = JSON.parse(readFileSync(join(output, 'plan.json')));
    assert.equal(plan.executionApproved, false);
    assert.equal(plan.providerCommitVerified, false);
    assert.match(plan.helperSha256, /^[a-f0-9]{64}$/);
    const env = readFileSync(join(output, '.env'), 'utf8');
    assert.match(env, /^INVENTORY_ORDER_PAIR_ENABLED=false$/m);
    assert.match(env, /^HK5_FINANCE_POLICY_APPROVED=false$/m);
    assert.throws(() => prepare({ ...valid, output }));
  } finally { rmSync(temp, { recursive: true }); }
});
test('refuses unscoped output directories', () => assert.throws(() => prepare({ ...valid, output: join(tmpdir(), 'shared-runtime') })));
test('rejects a double-dot-prefixed child of the checkout before writing credentials', () => {
  assert.throws(() => prepare({ ...valid, output: join(process.cwd(), '..runtime', valid.project) }), /outside all repository/);
});
test('rejects the primary checkout shared-secret tree from a linked worktree', () => {
  const common = spawnSync('git', ['rev-parse', '--path-format=absolute', '--git-common-dir'], { encoding: 'utf8' });
  assert.equal(common.status, 0);
  assert.throws(() => prepare({ ...valid, output: join(dirname(common.stdout.trim()), 'docker', 'secrets', valid.project) }), /outside all repository/);
});
test('proxy process exits before import/listen without reviewed execution artifact', () => {
  const temp = mkdtempSync(join(tmpdir(), 'hk5-unknown-denial-test-'));
  try {
    const plan = join(temp, 'plan.json');writeFileSync(plan, JSON.stringify({ executionApproved: false }));
    const result = spawnSync(process.execPath, ['scripts/qa/unknown/proxy.mjs'], { env: { ...process.env, HK5_UNKNOWN_PLAN_FILE: plan }, encoding: 'utf8', timeout: 5000 });
    assert.notEqual(result.status, 0);assert.match(result.stderr, /has not been approved/);
  } finally { rmSync(temp, { recursive: true }); }
});
