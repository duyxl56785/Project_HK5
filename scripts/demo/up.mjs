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
run('docker compose --env-file .env.demo -f docker/compose.demo.yml up -d --force-recreate');

const gateway = process.env.GATEWAY_URL ?? 'http://localhost:18080';
const deadline = Date.now() + 240_000;
for (;;) {
  const ready = await fetch(`${gateway}/api/categories`).then((r) => r.ok, () => false);
  if (ready) break;
  if (Date.now() > deadline) throw new Error('Stack did not become ready in 4 minutes; see docker compose logs');
  await new Promise((done) => setTimeout(done, 3000));
}
if (!process.argv.includes('--no-seed')) run('node scripts/demo/seed.mjs');
console.log('Backend ready. Start the UI: cd frontend && npm ci && npm run dev');
