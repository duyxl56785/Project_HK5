import { parseArgs } from 'node:util';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync, realpathSync } from 'node:fs';
import { resolve, dirname, join, relative, isAbsolute, basename, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

export const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
export function validate(input) {
  if (input.case !== 'lost-response') throw new Error('Only lost-response preparation is supported; claim-conflict is BLOCKED_DESIGN');
  if (!/^hk5-unknown-[0-9]{14}$/.test(input.project ?? '')) throw new Error('A unique hk5-unknown-YYYYMMDDHHmmss project is required');
  if (!/^[A-Za-z0-9._:-]{1,128}$/.test(input.operationId ?? '') || !/^[a-f0-9]{64}$/.test(input.bodySha256 ?? '')) throw new Error('Exact operation ID and transport body SHA256 required');
}
export function overlay(input, planPath, helperPath) {
  validate(input);
  const q = value => JSON.stringify(value.replaceAll('\\', '/'));
  return `# Preparation only. No execution permission or provider COMMIT proof.
services:
  inventory-service:
    environment:
      INVENTORY_ORDER_PAIR_ENABLED: "\${HK5_UNKNOWN_PAIR_OPT_IN:-false}"
  order-service:
    environment:
      INVENTORY_INTERNAL_URL: "\${HK5_UNKNOWN_INVENTORY_URL:-http://inventory-service:8105}"
  unknown-proxy:
    profiles: ["unknown-fault"]
    image: node:22-alpine
    network_mode: "service:inventory-service"
    restart: "no"
    depends_on:
      inventory-service:
        condition: service_started
    read_only: true
    cap_drop: [ALL]
    security_opt: ["no-new-privileges:true"]
    volumes:
      - type: bind
        source: ${q(planPath)}
        target: /fixture/plan.json
        read_only: true
      - type: bind
        source: ${q(helperPath)}
        target: /fixture/lost-response.mjs
        read_only: true
      - type: bind
        source: ${q(join(root, 'scripts/qa/unknown/proxy.mjs'))}
        target: /fixture/proxy.mjs
        read_only: true
    command: ["node", "/fixture/proxy.mjs"]
`;
}
export function prepare(input) {
  validate(input);
  if (!input.output) throw new Error('An explicit unused isolated output directory is required');
  const output = resolve(input.output);
  if (existsSync(output)) throw new Error('Refusing to reuse an existing runtime directory');
  const git = args => {
    const result = spawnSync('git', args, { cwd: root, encoding: 'utf8' });
    if (result.status !== 0) throw new Error('Cannot establish source identity');
    return result.stdout.trim();
  };
  const protectedRoots = [root, dirname(git(['rev-parse', '--path-format=absolute', '--git-common-dir']))].map(path => realpathSync(path));
  let ancestor = output;
  while (!existsSync(ancestor)) ancestor = dirname(ancestor);
  const physical = resolve(realpathSync(ancestor), relative(ancestor, output));
  const outside = (path, base) => { const rel = relative(base, path); return isAbsolute(rel) || rel === '..' || rel.startsWith('..' + sep); };
  if (basename(output) !== input.project || protectedRoots.some(base => !outside(output, base) || !outside(physical, base))) throw new Error('Output must be outside all repository checkouts and named after the isolated project');
  // The existing generator is reused without changing its default-OFF gates.
  const envFile = join(output, '.env'), secrets = join(output, 'secrets');
  const helper = join(root, 'scripts/qa/tv4-unknown/lost-response.mjs');
  const plan = { version: 1, case: input.case, project: input.project, sourceSha: git(['rev-parse', 'HEAD']),
    sourceRef: git(['branch', '--show-current']), sourceDirty: git(['status', '--porcelain']).length > 0, operationId: input.operationId, bodySha256: input.bodySha256,
    helperSha256: hash(readFileSync(helper)), proxySha256: hash(readFileSync(join(root, 'scripts/qa/unknown/proxy.mjs'))),
    gatewayPort: 18082, frontendPort: 5175, executionApproved: false, providerCommitVerified: false };
  mkdirSync(output, { recursive: true, mode: 0o700 });
  const generated = spawnSync(process.execPath, [join(root, 'scripts/qa/uat/Generate-IsolatedUatEnv.mjs')], {
    env: { ...process.env, HK5_UAT_ENV_FILE: envFile, HK5_UAT_SECRETS_DIR: secrets,
      HK5_UAT_ALLOWED_ORIGINS: 'http://127.0.0.1:5175,http://localhost:5175' }, stdio: 'pipe' });
  if (generated.status !== 0) throw new Error('Isolated credential generation failed; inspect only the new runtime directory');
  const planPath = join(output, 'plan.json');
  writeFileSync(planPath, JSON.stringify(plan, null, 2) + '\n', { mode: 0o600 });
  writeFileSync(join(output, 'compose.unknown.yml'), overlay(input, planPath, helper), { mode: 0o600 });
  writeFileSync(join(output, 'metadata.json'), JSON.stringify({ ...plan, planSha256: hash(readFileSync(planPath)), overlaySha256: hash(readFileSync(join(output, 'compose.unknown.yml'))),
    volumes: [`${input.project}-pgdemo`, `${input.project}-productimages`] }, null, 2) + '\n', { mode: 0o600 });
  return { output, project: input.project, sourceSha: plan.sourceSha, sourceDirty: plan.sourceDirty, executionApproved: false };
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { values } = parseArgs({ options: { case: { type: 'string' }, project: { type: 'string' }, operationId: { type: 'string' }, bodySha256: { type: 'string' }, output: { type: 'string' } } });
  console.log(JSON.stringify(prepare(values)));
}
