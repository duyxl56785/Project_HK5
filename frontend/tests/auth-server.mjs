// Auth Contract v1.0 HTTP fixture only; never a signed JWT/Gateway/DB implementation.
import { createServer } from 'vite';

let state;
function reset(options = {}) {
  state = { roles: ['CUSTOMER'], tokenRoles: ['CUSTOMER'], refreshStatus: 200, meStatus: 200,
    logoutStatus: 204, delay: 0, cookie: 'seed', sequence: 0, tokens: new Map(), calls: [], active: 0, maxActive: 0, ...options };
}
reset();
const server = await createServer({ server: { host: '127.0.0.1', port: 5188, strictPort: true }, plugins: [{
  name: 'test-only-auth-contract',
  configureServer(vite) {
    vite.middlewares.use(async (req, res, next) => {
      const path = req.url?.split('?')[0];
      const json = (status, value) => { res.statusCode = status; res.setHeader('Content-Type', 'application/json'); res.end(status === 204 ? undefined : JSON.stringify(value)); };
      const error = (status, code) => json(status, { code, message: 'Auth contract test fixture' });
      if (path === '/__test/state') return json(200, { ...state, tokens: undefined });
      if (path === '/__test/reset') {
        let body = ''; for await (const chunk of req) body += chunk;
        reset(JSON.parse(body || '{}')); return json(200, {});
      }
      if (path?.startsWith('/api/')) {
        const current = state;
        current.calls.push({ path, method: req.method, origin: req.headers.origin,
          csrf: req.headers['x-csrf-protection'], bearer: Boolean(req.headers.authorization), cookie: Boolean(req.headers.cookie) });
        res.setHeader('Cache-Control', 'no-store');
        const account = (roles = current.roles) => ({ userId: 'fixture-account', email: 'test@example.invalid', roles });
        if (path === '/api/auth/me') {
          if (current.meStatus !== 200) return error(current.meStatus, 'AUTH_ACCESS_INVALID');
          const user = current.tokens.get(req.headers.authorization);
          return user ? json(200, user) : error(401, 'AUTH_ACCESS_REQUIRED');
        }
        if (req.method !== 'POST' || !['/api/auth/login', '/api/auth/register', '/api/auth/refresh', '/api/auth/logout'].includes(path)) return error(501, 'TEST_ENDPOINT_NOT_DEFINED');
        if (req.headers.origin !== 'http://127.0.0.1:5188' || req.headers['x-csrf-protection'] !== '1') return error(403, 'AUTH_CSRF_REJECTED');
        if (path === '/api/auth/register') return json(201, account());
        if (path === '/api/auth/logout') {
          if (current.logoutStatus !== 204) return error(current.logoutStatus, 'AUTHORIZATION_DEPENDENCY_UNAVAILABLE');
          current.cookie = null;
          res.setHeader('Set-Cookie', 'refresh_token=; HttpOnly; SameSite=Lax; Path=/api/auth; Max-Age=0');
          return json(204);
        }
        if (path === '/api/auth/refresh') {
          current.maxActive = Math.max(current.maxActive, ++current.active);
          await new Promise((done) => setTimeout(done, current.delay));
          current.active--;
          if (current.refreshStatus !== 200) {
            if (current.refreshStatus === 429) res.setHeader('Retry-After', '11');
            return error(current.refreshStatus, current.refreshStatus === 401 ? 'AUTH_REFRESH_INVALID' : 'AUTHORIZATION_DEPENDENCY_UNAVAILABLE');
          }
          if (!current.cookie || !req.headers.cookie?.split('; ').includes(`refresh_token=${current.cookie}`)) return error(401, 'AUTH_REFRESH_INVALID');
        }
        const id = ++current.sequence;
        current.cookie = `cookie-${id}`;
        current.tokens.set(`Bearer fixture-access-${id}`, account());
        // Local HTTP fixture explicitly omits Secure; production Secure/CORS require real Gateway TLS evidence.
        res.setHeader('Set-Cookie', `refresh_token=${current.cookie}; HttpOnly; SameSite=Lax; Path=/api/auth; Max-Age=600`);
        return json(200, { accessToken: `fixture-access-${id}`, tokenType: 'Bearer', expiresIn: 900, account: account(current.tokenRoles) });
      }
      if (['/', '/login', '/register', '/customer', '/seller', '/admin', '/unknown', '/catalog/categories'].includes(path)) req.url = '/tests/index.html';
      next();
    });
  },
}] });
await server.listen();
console.log('Test-only Auth HTTP fixture listening on 127.0.0.1:5188');
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, async () => { await server.close(); process.exit(0); });
