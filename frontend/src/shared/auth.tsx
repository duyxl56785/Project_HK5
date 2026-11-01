import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { api, CSRF, mayHaveSession, refreshSession, setAccessToken, setSessionLostHandler } from './api';
import type { Account, TokenResponse } from './api';

interface Auth {
  readonly account: Account | null;
  readonly ready: boolean;
  has(role: string): boolean;
  login(email: string, password: string): Promise<void>;
  register(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
  /** Re-reads roles after an approval changed them on the server. */
  reload(): Promise<void>;
}

const Context = createContext<Auth | null>(null);

export function AuthProvider({ children }: { readonly children: ReactNode }) {
  const [account, setAccount] = useState<Account | null>(null);
  const [ready, setReady] = useState(false);
  const client = useQueryClient();

  useEffect(() => {
    setSessionLostHandler(() => { setAccount(null); client.clear(); });
    if (mayHaveSession()) refreshSession().then(setAccount).finally(() => setReady(true));
    else setReady(true);
  }, [client]);

  const value = useMemo<Auth>(() => {
    async function login(email: string, password: string) {
      const session = await api<TokenResponse>('/api/auth/login', { method: 'POST', headers: CSRF, body: { email, password } });
      setAccessToken(session.accessToken);
      client.clear();
      setAccount(session.account);
    }
    return {
      account, ready, login,
      has: (role) => account?.roles.includes(role) ?? false,
      async register(email, password) {
        await api('/api/auth/register', { method: 'POST', headers: CSRF, body: { email, password } });
        await login(email, password);
      },
      async logout() {
        try {
          await api('/api/auth/logout', { method: 'POST', headers: CSRF });
        } finally {
          setAccessToken(null);
          setAccount(null);
          client.clear();
        }
      },
      async reload() { setAccount(await refreshSession()); },
    };
  }, [account, ready, client]);

  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useAuth() {
  const auth = useContext(Context);
  if (!auth) throw new Error('useAuth requires AuthProvider');
  return auth;
}

/** UI gate only; every API still authorizes the verified JWT on the server. */
export function Guard({ role, children }: { readonly role?: string; readonly children: ReactNode }) {
  const { account, ready, has } = useAuth();
  const location = useLocation();
  if (!ready) return <p role="status">Đang tải phiên đăng nhập…</p>;
  if (!account) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />;
  if (role && !has(role)) return <p role="alert">Tài khoản của bạn không có quyền truy cập trang này.</p>;
  return <>{children}</>;
}
