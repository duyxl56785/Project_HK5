import assert from 'node:assert/strict';
import { afterEach, beforeEach, mock, test } from 'node:test';
import { ApiError, createApiClient } from './apiClient.ts';

const account = { userId: 'opaque-account-A', email: 'a@example.invalid', roles: ['CUSTOMER'] };
const token = (accessToken = 'test-only-access', value = account) => ({ accessToken, tokenType: 'Bearer', expiresIn: 900, account: value });
const response = (body, status = 200, headers) => new Response(status === 204 ? null : JSON.stringify(body), { status, headers });
const fail = (status, code, headers) => response({ code, message: 'test-only' }, status, headers);
const clients = [];
let calls, handler, lockTail;
function client() { const value = createApiClient(); clients.push(value); return value; }
function deferred() { let resolve; const promise = new Promise((done) => { resolve = done; }); return { promise, resolve }; }
beforeEach(() => {
  calls = []; lockTail = Promise.resolve();
  mock.method(globalThis, 'fetch', async (path, init) => { calls.push({ path, init }); return handler(path, init); });
  mock.getter(globalThis, 'navigator', () => ({ locks: { request: (_name, work) => {
    const result = lockTail.then(work); lockTail = result.catch(() => {}); return result;
  } } }));
  handler = (path) => path === '/api/auth/me' ? response(account) : response(token());
});
afterEach(() => { clients.splice(0).forEach((value) => value.dispose()); mock.restoreAll(); });

test('cookie operations use CSRF/include, never Bearer/Origin/body on refresh; /me supplies roles', async () => {
  const value = client();
  handler = (path) => path === '/api/auth/me' ? response({ ...account, roles: ['SELLER'] }) : response(token());
  await value.restore();
  assert.deepEqual(value.getSession().account.roles, ['SELLER']);
  const [refresh, me] = calls;
  assert.deepEqual(refresh.init.headers, { 'X-CSRF-Protection': '1' });
  assert.equal(refresh.init.credentials, 'include');
  assert.equal(refresh.init.body, undefined);
  assert.equal(me.init.headers.Authorization, 'Bearer test-only-access');
  assert.equal(me.init.credentials, 'omit');
  assert.equal(me.init.redirect, 'error');
  assert.equal(JSON.stringify(value.getSession()).includes('test-only-access'), false);
});

test('simultaneous and delayed 401s share one refresh and retry safe reads once with rotated token', async () => {
  const value = client(); await value.login('a@example.invalid', 'unchanged password'); calls = [];
  const gate = deferred();
  handler = async (path, init) => {
    if (path === '/api/auth/refresh') { await gate.promise; return response(token('rotated')); }
    if (path === '/api/auth/me') return response(account);
    return new Headers(init.headers).get('Authorization') === 'Bearer rotated' ? response({ ok: true }) : fail(401, 'AUTH_ACCESS_INVALID');
  };
  const requests = Array.from({ length: 12 }, () => value.request('/api/users/me'));
  await new Promise((done) => setImmediate(done)); gate.resolve();
  await Promise.all(requests);
  assert.equal(calls.filter((call) => call.path === '/api/auth/refresh').length, 1);
  assert.equal(calls.filter((call) => call.path === '/api/users/me').length, 24);
  assert.equal(value.getSession().status, 'authenticated');
});

for (const [status, code] of [[400, 'AUTH_REQUEST_INVALID'], [401, 'OTHER_401'], [403, 'AUTH_FORBIDDEN'], [429, 'AUTH_RATE_LIMITED'], [503, 'AUTHORIZATION_DEPENDENCY_UNAVAILABLE'], [504, 'GATEWAY_UPSTREAM_TIMEOUT']]) {
  test(`${status}/${code} does not refresh; retains status/code/Retry-After`, async () => {
    const value = client(); handler = () => fail(status, code, { 'Retry-After': '17' });
    await assert.rejects(value.request('/api/users/me'), (error) => error.status === status && error.code === code && error.retryAfter === '17');
    assert.equal(calls.length, 1);
  });
}

for (const method of ['POST', 'PUT', 'PATCH', 'DELETE']) {
  test(`${method} 401 never refreshes/replays or modifies original body/idempotency key`, async () => {
    const value = client(); await value.restore(); calls = [];
    handler = () => fail(401, 'AUTH_ACCESS_INVALID');
    await assert.rejects(value.request('/api/users/me', { method, headers: { 'Idempotency-Key': 'unchanged-key' }, body: 'unchanged-body' }), { status: 401 });
    assert.equal(calls.length, 1);
    assert.equal(calls[0].init.body, 'unchanged-body');
    assert.equal(calls[0].init.headers.get('Idempotency-Key'), 'unchanged-key');
    assert.equal(value.getSession().account, undefined);
  });
}

test('second protected 401 stops recursion and clears session/cache', async () => {
  const value = client(); await value.restore(); calls = [];
  value.queryClient.setQueryData(['private'], 'secret');
  handler = (path) => path === '/api/auth/me' ? response(account) : path === '/api/auth/refresh' ? response(token()) : fail(401, 'AUTH_ACCESS_INVALID');
  await assert.rejects(value.request('/api/users/me'), { status: 401 });
  assert.equal(calls.filter((call) => call.path === '/api/auth/refresh').length, 1);
  assert.equal(calls.filter((call) => call.path === '/api/users/me').length, 2);
  assert.equal(value.getSession().status, 'anonymous');
  assert.equal(value.queryClient.getQueryCache().getAll().length, 0);
});

for (const status of [401, 403, 429, 503]) {
  test(`refresh ${status} is not retried; clears memory, preserves temporary failure`, async () => {
    const value = client(); await value.restore(); calls = [];
    handler = () => fail(status, status === 401 ? 'AUTH_REFRESH_INVALID' : 'AUTH_DEPENDENCY_ERROR');
    await assert.rejects(value.restore(), { status });
    assert.equal(calls.length, 1);
    assert.equal(value.getSession().account, undefined);
    assert.equal(value.getSession().status, status === 401 ? 'anonymous' : 'error');
  });
}

for (const operation of ['login', 'logout']) {
  test(`${operation} failure is not retried or refreshed`, async () => {
    const value = client(); handler = () => fail(401, 'AUTH_INVALID_CREDENTIALS');
    await assert.rejects(operation === 'login' ? value.login('a@example.invalid', 'password') : value.logout(), { status: 401 });
    assert.equal(calls.length, 1);
  });
}

test('network and non-JSON upstream errors are mapped without retry', async () => {
  const value = client(); handler = () => { throw new TypeError('network'); };
  await assert.rejects(value.request('/api/users/me'), { status: 503, code: 'NETWORK_UNAVAILABLE' });
  handler = () => new Response('<html>down</html>', { status: 503 });
  await assert.rejects(value.request('/api/users/me'), { status: 503, code: 'HTTP_ERROR' });
  assert.equal(calls.length, 2);
});

test('logout immediately clears memory/cache even on server 503; late refresh cannot restore identity', async () => {
  const value = client(); await value.restore();
  value.queryClient.setQueryData(['private'], 'old-account');
  const gate = deferred();
  handler = async (path) => {
    if (path === '/api/auth/refresh') { await gate.promise; return response(token('late')); }
    return fail(503, 'AUTHORIZATION_DEPENDENCY_UNAVAILABLE');
  };
  const refresh = value.restore();
  await new Promise((done) => setImmediate(done));
  const refreshCheck = assert.rejects(refresh, { code: 'AUTH_SESSION_CHANGED' });
  const logout = value.logout();
  assert.equal(value.getSession().account, undefined);
  assert.equal(value.queryClient.getQueryCache().getAll().length, 0);
  gate.resolve();
  await refreshCheck;
  await assert.rejects(logout, { status: 503 });
  assert.equal(value.getSession().status, 'error');
});

test('expiry removes access/account/cache; login user switch cannot inherit cached private data', async () => {
  mock.timers.enable({ apis: ['setTimeout'] });
  const value = client(); await value.restore();
  value.queryClient.setQueryData(['private'], 'old-account');
  mock.timers.tick(900_000);
  assert.equal(value.getSession().status, 'expired');
  assert.equal(value.getSession().account, undefined);
  assert.equal(value.queryClient.getQueryData(['private']), undefined);
  const second = { ...account, userId: 'opaque-account-B', roles: ['ADMIN'] };
  handler = (path) => path === '/api/auth/me' ? response(second) : response(token('second', second));
  await value.login('b@example.invalid', 'password');
  assert.equal(value.getSession().account.userId, second.userId);
  assert.equal(value.queryClient.getQueryData(['private']), undefined);
  mock.timers.reset();
});

for (const bad of [null, { ...token(), expiresIn: 901 }, { ...token(), tokenType: 'basic' }, token('bad token'), token('test', { ...account, roles: ['ROLE_ADMIN'] })]) {
  test(`malformed token DTO fails closed: ${JSON.stringify(bad)}`, async () => {
    const value = client(); handler = () => response(bad);
    await assert.rejects(value.restore(), { code: 'AUTH_RESPONSE_INVALID' });
    assert.equal(value.getSession().account, undefined);
  });
}

test('malformed /me or mismatched subject never grants token response ADMIN roles', async () => {
  const value = client();
  for (const me of [{ ...account, roles: ['ROOT'] }, { ...account, userId: 'different' }, { ...account, roles: [] }]) {
    handler = (path) => path === '/api/auth/me' ? response(me) : response(token());
    await assert.rejects(value.restore(), { code: 'AUTH_RESPONSE_INVALID' });
    assert.equal(value.getSession().account, undefined);
  }
});

test('register preserves password, strips injected identity fields and does not log in', async () => {
  const value = client(); handler = () => response(account, 201);
  assert.deepEqual(await value.register('a@example.invalid', '  password unchanged  '), account);
  assert.deepEqual(JSON.parse(calls[0].init.body), { email: account.email, password: '  password unchanged  ' });
  assert.equal(value.getSession().status, 'idle');
  assert.equal(calls.length, 1);
});

test('external/normalized escape/Auth paths and identity/Origin headers are rejected before fetch', async () => {
  const value = client();
  for (const path of ['https://evil.invalid/api/me', '//evil.invalid/api/me', '/internal/x', '/api/../../x', '/api/%2e%2e/x', '/api/../internal/x', '/api/one\\..\\x', '/api/auth/refresh', '/api/auth/logout', '/api/auth/login', '/api/users/me#x']) {
    await assert.rejects(value.request(path), TypeError);
  }
  for (const name of ['Origin', 'Authorization', 'X-User-Id', 'X-Internal-Token', 'Cookie']) {
    await assert.rejects(value.request('/api/users/me', { headers: { [name]: 'spoof' } }), TypeError);
  }
  assert.equal(calls.length, 0);
});

test('missing cross-tab coordination support fails closed without rotating cookie', async () => {
  mock.getter(globalThis, 'navigator', () => ({}));
  const value = client();
  await assert.rejects(value.restore(), { status: 503, code: 'AUTH_COORDINATION_UNAVAILABLE' });
  assert.equal(calls.length, 0);
});

test('all clients serialize cookie mutations using the same lock', async () => {
  const a = client(), b = client(); let active = 0, maximum = 0;
  handler = async (path) => {
    if (path === '/api/auth/refresh') {
      maximum = Math.max(maximum, ++active);
      await new Promise((done) => setImmediate(done)); active--;
      return response(token());
    }
    return response(account);
  };
  await Promise.all([a.restore(), b.restore()]);
  assert.equal(maximum, 1);
});

for (const status of [401, 503]) {
  test(`delayed stale 401 cannot start another refresh after refresh failed ${status}`, async () => {
    const value = client(); await value.restore(); calls = [];
    const late = deferred();
    handler = async (path) => {
      if (path === '/api/auth/refresh') return fail(status, status === 401 ? 'AUTH_REFRESH_INVALID' : 'AUTHORIZATION_DEPENDENCY_UNAVAILABLE');
      if (path === '/api/users/me?late') await late.promise;
      return fail(401, 'AUTH_ACCESS_INVALID');
    };
    const slow = value.request('/api/users/me?late');
    await assert.rejects(value.request('/api/users/me'), { status });
    const slowCheck = assert.rejects(slow, { code: 'AUTH_SESSION_CHANGED' });
    late.resolve(); await slowCheck;
    assert.equal(calls.filter((call) => call.path === '/api/auth/refresh').length, 1);
  });
}

test('in-flight old actor response is rejected after account switch', async () => {
  const value = client(); await value.restore();
  const late = deferred();
  handler = async (path) => {
    if (path === '/api/users/me') { await late.promise; return response({ private: 'old actor' }); }
    return path === '/api/auth/me' ? response(account) : response(token('new-session'));
  };
  const old = value.request('/api/users/me');
  await value.login('other@example.invalid', 'password');
  const oldCheck = assert.rejects(old, { code: 'AUTH_SESSION_CHANGED' });
  late.resolve(); await oldCheck;
});

test('aborted caller is not refreshed or mapped to incorrect credentials', async () => {
  const value = client(); const controller = new AbortController(); controller.abort();
  handler = () => { throw controller.signal.reason; };
  await assert.rejects(value.request('/api/users/me', { signal: controller.signal }), { name: 'AbortError' });
  assert.equal(calls.length, 1);
});

test('late 401 from the previous access token reuses completed rotation instead of refreshing again', async () => {
  const value = client(); await value.restore(); calls = [];
  const late = deferred(); let delayed = true;
  handler = async (path, init) => {
    if (path === '/api/auth/refresh') return response(token('rotated'));
    if (path === '/api/auth/me') return response(account);
    if (new Headers(init.headers).get('Authorization') === 'Bearer rotated') return response({ ok: true });
    if (path.endsWith('?late') && delayed) { delayed = false; await late.promise; }
    return fail(401, 'AUTH_ACCESS_INVALID');
  };
  const slow = value.request('/api/users/me?late');
  await value.request('/api/users/me');
  late.resolve(); await slow;
  assert.equal(calls.filter((call) => call.path === '/api/auth/refresh').length, 1);
});
