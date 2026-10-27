import { QueryClient } from '@tanstack/react-query';

export type Role = 'CUSTOMER' | 'SELLER' | 'ADMIN';
export type Account = Readonly<{ userId: string; email: string; roles: readonly Role[] }>;
export type Session = Readonly<{
  status: 'idle' | 'pending' | 'authenticated' | 'anonymous' | 'expired' | 'error';
  account?: Account;
  sessionKey: number;
  error?: ApiError;
}>;

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly retryAfter: string | null;
  constructor(status: number, code: string, retryAfter: string | null = null) {
    super(code);
    this.status = status;
    this.code = code;
    this.retryAfter = retryAfter;
  }
}

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function accountDto(value: unknown): Account {
  if (!record(value) || typeof value.userId !== 'string' || !value.userId.trim()
    || typeof value.email !== 'string' || !value.email.trim()
    || !Array.isArray(value.roles) || !value.roles.length
    || !value.roles.every((role) => ['CUSTOMER', 'SELLER', 'ADMIN'].includes(role))) {
    throw new ApiError(502, 'AUTH_RESPONSE_INVALID');
  }
  return Object.freeze({ userId: value.userId, email: value.email,
    roles: Object.freeze([...value.roles]) as readonly Role[] });
}

type RequestOptions = Pick<RequestInit, 'method' | 'headers' | 'body' | 'signal'>;

// Create once per mounted shared app; never persist or broadcast credentials.
export function createApiClient() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  let session: Session = { status: 'idle', sessionKey: 0 };
  let accessToken: string | undefined;
  let tokenVersion = 0;
  let epoch = 0;
  let expiryTimer: ReturnType<typeof setTimeout> | undefined;
  let flight: Promise<void> | undefined;
  let disposed = false;
  const listeners = new Set<() => void>();
  const channel = typeof BroadcastChannel === 'undefined' ? undefined : new BroadcastChannel('hk5-auth-session');

  function publish(status: Session['status'], account?: Account, error?: ApiError) {
    session = Object.freeze({ status, account, error, sessionKey: session.sessionKey + 1 });
    void queryClient.cancelQueries();
    queryClient.clear();
    listeners.forEach((listener) => listener());
  }

  function clear(status: Session['status'], error?: ApiError) {
    accessToken = undefined;
    clearTimeout(expiryTimer);
    tokenVersion++;
    publish(status, undefined, error);
  }

  function invalidate() {
    epoch++;
    clear('anonymous');
  }
  if (channel) channel.onmessage = (event: MessageEvent<unknown>) => {
    if (event.data === 'invalidate') invalidate();
  };

  function assertCurrent(start: number) {
    if (disposed || start !== epoch) throw new ApiError(401, 'AUTH_SESSION_CHANGED');
  }

  async function transport(path: string, init: RequestInit): Promise<Response> {
    try {
      return await fetch(path, { ...init, mode: 'same-origin', redirect: 'error',
        signal: init.signal ? AbortSignal.any([init.signal, AbortSignal.timeout(15_000)]) : AbortSignal.timeout(15_000) });
    } catch (error) {
      if (init.signal?.aborted) throw error;
      throw new ApiError(503, 'NETWORK_UNAVAILABLE');
    }
  }

  async function checked(response: Response): Promise<Response> {
    if (response.ok) return response;
    const body: unknown = await response.json().catch(() => null);
    throw new ApiError(response.status, record(body) && typeof body.code === 'string' ? body.code : 'HTTP_ERROR',
      response.headers.get('Retry-After'));
  }

  async function json(response: Response): Promise<unknown> {
    try { return await response.json(); }
    catch { throw new ApiError(502, 'AUTH_RESPONSE_INVALID'); }
  }

  async function cookieOperation(operation: string, body?: { email: string; password: string }) {
    return checked(await transport(`/api/auth/${operation}`, {
      method: 'POST', credentials: 'include', headers: {
        'X-CSRF-Protection': '1', ...(body ? { 'Content-Type': 'application/json' } : {}),
      }, ...(body ? { body: JSON.stringify({ email: body.email, password: body.password }) } : {}),
    }));
  }

  async function withCookieLock<T>(work: () => Promise<T>): Promise<T> {
    if (typeof navigator === 'undefined' || !navigator.locks || !channel) {
      throw new ApiError(503, 'AUTH_COORDINATION_UNAVAILABLE');
    }
    // All cookie mutations share this origin-wide lock, including login/logout.
    return navigator.locks.request('hk5-auth-cookie', work);
  }

  async function acceptToken(response: Response, start: number) {
    const dto = await json(response);
    assertCurrent(start);
    if (!record(dto) || typeof dto.accessToken !== 'string' || !dto.accessToken
      || /\s/.test(dto.accessToken) || dto.tokenType !== 'Bearer' || dto.expiresIn !== 900) {
      throw new ApiError(502, 'AUTH_RESPONSE_INVALID');
    }
    const issuedAccount = accountDto(dto.account);
    accessToken = dto.accessToken;
    tokenVersion++;
    clearTimeout(expiryTimer);
    expiryTimer = setTimeout(() => clear('expired'), dto.expiresIn * 1000);
    const me = accountDto(await json(await checked(await transport('/api/auth/me', {
      credentials: 'omit', headers: { Authorization: `Bearer ${accessToken}` },
    }))));
    assertCurrent(start);
    if (me.userId !== issuedAccount.userId) throw new ApiError(502, 'AUTH_RESPONSE_INVALID');
    publish('authenticated', me);
  }

  function authFailure(error: unknown, start: number): never {
    const failure = error instanceof ApiError ? error : new ApiError(503, 'NETWORK_UNAVAILABLE');
    if (start === epoch && !disposed) {
      epoch++;
      clear(failure.status === 401 ? 'anonymous' : 'error', failure);
    }
    throw failure;
  }

  function restore(): Promise<void> {
    if (flight) return flight;
    const start = epoch;
    clear('pending');
    flight = withCookieLock(async () => {
      assertCurrent(start);
      await acceptToken(await cookieOperation('refresh'), start);
    }).catch((error: unknown) => authFailure(error, start)).finally(() => { flight = undefined; });
    return flight;
  }

  async function login(email: string, password: string) {
    invalidate();
    channel?.postMessage('invalidate');
    const start = epoch;
    publish('pending');
    try {
      await withCookieLock(async () => {
        assertCurrent(start);
        await acceptToken(await cookieOperation('login', { email, password }), start);
      });
    } catch (error) { authFailure(error, start); }
  }

  async function register(email: string, password: string): Promise<Account> {
    return accountDto(await json(await cookieOperation('register', { email, password })));
  }

  async function logout() {
    invalidate();
    channel?.postMessage('invalidate');
    const start = epoch;
    try {
      await withCookieLock(async () => {
        assertCurrent(start);
        await cookieOperation('logout');
      });
    } catch (error) { authFailure(error, start); }
  }

  async function request(path: string, init: RequestOptions = {}): Promise<Response> {
    const url = new URL(path, 'https://gateway.invalid');
    if (!path.startsWith('/api/') || path.includes('\\') || /%2f|%5c|%2e/i.test(path)
      || url.origin !== 'https://gateway.invalid' || !url.pathname.startsWith('/api/') || url.hash
      || (url.pathname.startsWith('/api/auth/') && url.pathname !== '/api/auth/me')) {
      throw new TypeError('Expected a relative Gateway API resource path');
    }
    const method = (init.method ?? 'GET').toUpperCase();
    if (!['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE'].includes(method)
      || (url.pathname === '/api/auth/me' && method !== 'GET')) throw new TypeError('Unsupported method');
    const headers = new Headers(init.headers);
    for (const name of headers.keys()) {
      if (!['accept', 'content-type', 'idempotency-key'].includes(name)) throw new TypeError('Unsupported API header');
    }
    const start = epoch;
    const version = tokenVersion;
    async function send() {
      assertCurrent(start);
      if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
      else headers.delete('Authorization');
      const response = await transport(path, { method, headers, body: init.body, signal: init.signal, credentials: 'omit' });
      assertCurrent(start);
      return checked(response);
    }
    try { return await send(); }
    catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401
        || !['AUTH_ACCESS_REQUIRED', 'AUTH_ACCESS_INVALID'].includes(error.code)) throw error;
      assertCurrent(start);
      // Writes are never replayed, including checkout until TV3 approves its wire contract.
      if (!['GET', 'HEAD'].includes(method)) {
        epoch++;
        clear('anonymous', error);
        throw error;
      }
      if (version === tokenVersion || !accessToken) await restore();
      assertCurrent(start);
      try { return await send(); }
      catch (retryError) {
        if (retryError instanceof ApiError && retryError.status === 401) {
          epoch++;
          clear('anonymous', retryError);
        }
        throw retryError;
      }
    }
  }

  return {
    queryClient, request, login, register, logout, restore,
    getSession: () => session,
    subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; },
    dispose: () => { disposed = true; invalidate(); channel?.close(); listeners.clear(); },
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
