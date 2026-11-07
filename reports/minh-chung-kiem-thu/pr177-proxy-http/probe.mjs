import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

const mode = process.argv[2];
assert.ok(['host', 'private-peer'].includes(mode), 'Choose host or private-peer');
assert.match(process.env.HK5_THROTTLE_ISOLATED_PROJECT ?? '', /^hk5-uat-[a-z0-9-]{3,40}$/);
const origin = 'http://127.0.0.1:5176';
const tag = randomUUID();
let requests = 0;

async function ready(base) {
  const deadline = Date.now() + 120_000;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${base}/api/auth/login`, {
        method: 'POST', signal: AbortSignal.timeout(5000),
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: 'readiness@example.invalid', password: 'synthetic-readiness' }),
      });
      if (response.status === 403 && (await response.json()).code === 'AUTH_CSRF_REJECTED') return;
    } catch {}
    await new Promise(resolve => setTimeout(resolve, 2000));
  }
  assert.fail(`Auth route not ready: ${base}`);
}

async function window(label, base, route, forged) {
  const statuses = [];
  for (let attempt = 1; attempt <= 11; attempt++) {
    const response = await fetch(`${base}/api/auth/${route}`, {
      method: 'POST', redirect: 'error', signal: AbortSignal.timeout(15_000),
      headers: {
        'Content-Type': 'application/json', Origin: origin, 'X-CSRF-Protection': '1',
        ...(forged ? { 'X-Forwarded-For': `7.7.7.${attempt}` } : {}),
      },
      body: JSON.stringify({
        email: `${label}-${tag}${route === 'register' ? `-${attempt}` : ''}@example.invalid`,
        password: 'Synthetic-Rehearsal-Password-2026',
      }),
    });
    const body = await response.json();
    requests++;
    statuses.push(response.status);
    const expected = attempt === 11 ? 429 : route === 'register' ? 201 : 401;
    assert.equal(response.status, expected, `${label} attempt ${attempt}: ${body.code ?? 'unexpected status'}`);
    if (expected === 401) assert.equal(body.code, 'AUTH_INVALID_CREDENTIALS');
    if (expected === 201) assert.deepEqual(body.roles, ['CUSTOMER']);
    if (expected === 429) {
      assert.equal(body.code, 'AUTH_RATE_LIMITED');
      assert.match(response.headers.get('retry-after') ?? '', /^[1-9][0-9]*$/);
    }
  }
  console.log(JSON.stringify({ label, statuses, result: 'PASS' }));
}

if (mode === 'host') {
  await ready(origin);
  await ready('http://127.0.0.1:18083');
  await window('nginx-legitimate', origin, 'login', false);
  await window('nginx-forged', origin, 'login', true);
  await window('gateway-forged', 'http://127.0.0.1:18083', 'login', true);
  await window('nginx-register', origin, 'register', true);
} else {
  await ready('http://auth-service:8101');
  await window('private-forged', 'http://auth-service:8101', 'login', true);
  await window('private-register', 'http://auth-service:8101', 'register', true);
}
console.log(JSON.stringify({ mode, requests, result: 'PASS', syntheticOnly: true }));
