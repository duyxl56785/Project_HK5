// Builds the backend jars, starts the local stack and seeds it.
// Usage: node scripts/demo/up.mjs [--skip-build] [--no-seed]
import { execSync } from 'node:child_process';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '../..');
const run = (command, cwd = root) => execSync(command, { cwd, stdio: 'inherit' });

run('node scripts/demo/gen-env.mjs');
if (!process.argv.includes('--skip-build')) {
  run(`${process.platform === 'win32' ? '.\\gradlew.bat' : './gradlew'} bootJar -q`, resolve(root, 'backend'));
}
// Jars are bind-mounted, so a running container would otherwise keep a stale or half-replaced jar.
run('docker compose --env-file .env.demo -f docker/compose.demo.yml up -d --build --force-recreate');

const gateway = process.env.GATEWAY_URL ?? 'http://localhost:18080';
const deadline = Date.now() + 240_000;
// The gateway answers before the services behind it, so each one must reply without a 5xx. Seed needs them all.
const headers = { Origin: 'http://localhost:5173', 'X-CSRF-Protection': '1', 'Content-Type': 'application/json' };
const probes = [['/api/auth/login', { method: 'POST', headers, body: '{"email":"probe@hk5.local","password":"not-a-real-password"}' }],
  ...['/api/categories', '/api/stores', '/api/catalog/products', '/api/cart', '/api/orders', '/api/notifications'].map((path) => [path, { headers }])];
const answers = ([path, options]) => fetch(gateway + path, options).then((r) => r.status < 500, () => false);
for (;;) {
  const ready = (await Promise.all(probes.map(answers))).every(Boolean);
  if (ready) break;
  if (Date.now() > deadline) throw new Error('Stack did not become ready in 4 minutes; see docker compose logs');
  await new Promise((done) => setTimeout(done, 3000));
}
if (!process.argv.includes('--no-seed')) run('node scripts/demo/seed.mjs');
console.log('Ready: http://localhost:5173');
