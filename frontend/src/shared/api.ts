/** Same-origin API client: access token in memory only, refresh via the HttpOnly cookie. */
export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) {
    super(message);
  }
}

export interface Account { readonly userId: string; readonly email: string; readonly roles: readonly string[] }
export interface TokenResponse { readonly accessToken: string; readonly expiresIn: number; readonly account: Account }

let accessToken: string | null = null;
let refreshing: Promise<Account | null> | null = null;
let onSessionLost: () => void = () => {};

export function setAccessToken(token: string | null) { accessToken = token; }
export function setSessionLostHandler(handler: () => void) { onSessionLost = handler; }

/** Mutating auth calls need the CSRF header; the server also checks Origin. */
export const CSRF = { 'X-CSRF-Protection': '1' };

async function failure(response: Response): Promise<ApiError> {
  const body = await response.json().catch(() => null) as { code?: string; message?: string } | null;
  return new ApiError(response.status, body?.code ?? 'REQUEST_FAILED', body?.message ?? 'Yêu cầu không thành công');
}

const postRefresh = () => fetch('/api/auth/refresh', { method: 'POST', headers: CSRF, credentials: 'same-origin' });

/**
 * Single-flight: rotation revokes the whole family if one refresh token is sent twice.
 * The Web Lock extends that across tabs, which share the cookie: each reads the rotated one in turn.
 */
export function refreshSession(): Promise<Account | null> {
  refreshing ??= (navigator.locks ? navigator.locks.request('hk5-auth-refresh', postRefresh) : postRefresh())
    .then(async (response) => {
      if (!response.ok) { accessToken = null; return null; }
      const body = await response.json() as TokenResponse;
      accessToken = body.accessToken;
      return body.account;
    })
    .finally(() => { refreshing = null; });
  return refreshing;
}

interface Options {
  readonly method?: string;
  readonly body?: unknown;
  readonly form?: FormData;
  readonly headers?: Record<string, string>;
}

function send(path: string, { method = 'GET', body, form, headers }: Options) {
  return fetch(path, {
    method,
    credentials: 'same-origin',
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...headers,
    },
    body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
  });
}

export async function api<T>(path: string, options: Options = {}): Promise<T> {
  let response = await send(path, options);
  if (response.status === 401 && accessToken) {
    if (await refreshSession()) response = await send(path, options);
    else onSessionLost();
  }
  if (!response.ok) throw await failure(response);
  if (response.status === 202 || response.status === 204) return undefined as T;
  return await response.json() as T;
}
