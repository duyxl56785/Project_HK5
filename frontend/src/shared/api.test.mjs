import assert from 'node:assert/strict';
import { after, before, beforeEach, test } from 'node:test';
import { createServer } from 'vite';

let vite, api, mayHaveSession, refreshSession, setAccessToken;
before(async () => {
  vite = await createServer({ server: { middlewareMode: true, hmr: false, watch: null }, optimizeDeps: { noDiscovery: true }, logLevel: 'silent' });
  ({ api, mayHaveSession, refreshSession, setAccessToken } = await vite.ssrLoadModule('/src/shared/api.ts'));
});
after(() => { delete globalThis.localStorage; return vite?.close(); });

beforeEach(() => {
  const stored = new Map();
  globalThis.localStorage = {
    getItem: (key) => stored.get(key) ?? null,
    setItem: (key, value) => stored.set(key, value),
    removeItem: (key) => stored.delete(key),
  };
});

const refreshAnswers = (response) => { globalThis.fetch = async () => response; };

test('a visitor who never signed in has no session to restore', () => {
  assert.equal(mayHaveSession(), false);
});

test('signing in and out sets and clears the flag', () => {
  setAccessToken('token');
  assert.equal(mayHaveSession(), true);
  setAccessToken(null);
  assert.equal(mayHaveSession(), false);
});

test('a failed refresh clears the flag and a successful one keeps it', async () => {
  setAccessToken('token');
  refreshAnswers(new Response(null, { status: 401 }));
  assert.equal(await refreshSession(), null);
  assert.equal(mayHaveSession(), false);

  refreshAnswers(Response.json({ accessToken: 'next', expiresIn: 900, account: { userId: 'u', email: 'e', roles: [] } }));
  assert.equal((await refreshSession()).userId, 'u');
  assert.equal(mayHaveSession(), true);
});

for (const status of [503, 403, 500]) {
  test(`a ${status} refresh proves nothing about the cookie, so the next load tries again`, async () => {
    setAccessToken('token');
    refreshAnswers(new Response(null, { status }));
    assert.equal(await refreshSession(), null);
    assert.equal(mayHaveSession(), true);
  });
}

test('blocked storage falls back to trying a refresh', () => {
  globalThis.localStorage = { getItem() { throw new Error('blocked'); }, setItem() { throw new Error('blocked'); }, removeItem() { throw new Error('blocked'); } };
  assert.doesNotThrow(() => setAccessToken('token'));
  assert.equal(mayHaveSession(), true);
});

test('rate limit shows a Vietnamese message with the wait from Retry-After', async () => {
  const limited = (headers) => { globalThis.fetch = async () => new Response(
    JSON.stringify({ code: 'AUTH_RATE_LIMITED', message: 'Too many attempts, try again later' }),
    { status: 429, headers: { 'Content-Type': 'application/json', ...headers } }); };
  limited({ 'Retry-After': '61' });
  await assert.rejects(api('/api/auth/register', { method: 'POST', body: {} }), (error) => {
    assert.equal(error.status, 429);
    assert.equal(error.code, 'AUTH_RATE_LIMITED');
    assert.equal(error.message, 'Bạn thao tác quá nhiều lần. Vui lòng thử lại sau 2 phút.');
    return true;
  });
  limited({});
  await assert.rejects(api('/api/auth/register', { method: 'POST', body: {} }),
    { message: 'Bạn thao tác quá nhiều lần. Vui lòng thử lại sau ít phút.' });
});
