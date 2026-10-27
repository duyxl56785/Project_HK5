import { createContext, useContext, useEffect, useState, useSyncExternalStore } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../shared/apiClient';
import type { ApiClient, Role } from '../shared/apiClient';

const ClientContext = createContext<ApiClient | null>(null);

function useClient() {
  const client = useContext(ClientContext);
  if (!client) throw new Error('Shared auth provider required');
  return client;
}

export function useSession() {
  const client = useClient();
  return useSyncExternalStore(client.subscribe, client.getSession, client.getSession);
}

export function ApiErrorMessage({ error }: { error: unknown }) {
  const status = error instanceof ApiError ? error.status : 503;
  const messages: Record<number, string> = {
    401: 'Phiên không hợp lệ. Vui lòng đăng nhập lại.',
    403: 'Bạn không có quyền thực hiện thao tác này.',
    429: 'Quá nhiều yêu cầu. Vui lòng chờ trước khi thử lại.',
    503: 'Dịch vụ tạm thời không khả dụng. Vui lòng thử lại sau.',
  };
  return <p role="alert">{messages[status] ?? 'Yêu cầu không thành công.'}
    {error instanceof ApiError && status === 429 && error.retryAfter
      ? ` Retry-After: ${error.retryAfter}.` : ''}</p>;
}

function SessionProvider({ client, children }: { client: ApiClient; children: ReactNode }) {
  useEffect(() => {
    if (client.getSession().status === 'idle') void client.restore().catch(() => {});
  }, [client]);
  return <ClientContext.Provider value={client}>
    <QueryClientProvider client={client.queryClient}>{children}</QueryClientProvider>
  </ClientContext.Provider>;
}

function Guard({ role }: { role: Role }) {
  const session = useSession();
  const client = useClient();
  const location = useLocation();
  if (session.status === 'idle' || session.status === 'pending') return <p role="status">Đang xác minh phiên…</p>;
  if (session.status === 'error' || session.status === 'expired') return <>
    {session.error ? <ApiErrorMessage error={session.error} /> : <p role="status">Phiên đã hết hạn.</p>}
    <button onClick={() => { void client.restore().catch(() => {}); }}>Khôi phục phiên</button>
  </>;
  if (session.status !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (!session.account?.roles.includes(role)) return <ApiErrorMessage error={new ApiError(403, 'AUTH_FORBIDDEN')} />;
  return <Outlet />;
}

function Shell({ title }: { title: string }) {
  return <section aria-label={title}><h1>{title}</h1>
    <p>Khung giao diện đang chờ duyệt tích hợp. Chưa kết nối chức năng nghiệp vụ.</p>
  </section>;
}

function CredentialsForm({ register = false }: { register?: boolean }) {
  const client = useClient();
  const navigate = useNavigate();
  const location = useLocation();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  const [created, setCreated] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const email = String(data.get('email'));
    const password = String(data.get('password'));
    setPending(true); setError(undefined); setCreated(false);
    try {
      if (register) { await client.register(email, password); setCreated(true); }
      else {
        await client.login(email, password);
        const from: unknown = location.state?.from;
        navigate(typeof from === 'string' && ['/customer', '/seller', '/admin'].includes(from) ? from : '/customer', { replace: true });
      }
      form.reset();
    } catch (failure) { setError(failure); }
    finally { setPending(false); }
  }
  return <section><h1>{register ? 'Đăng ký' : 'Đăng nhập'}</h1>
    <form onSubmit={(event) => { void submit(event); }} aria-busy={pending}>
      <p><label>Email <input name="email" type="email" required maxLength={254} autoComplete="username" /></label></p>
      <p><label>Mật khẩu <input name="password" type="password" required minLength={register ? 12 : 1}
        maxLength={128} autoComplete={register ? 'new-password' : 'current-password'} /></label></p>
      <button disabled={pending}>{pending ? 'Đang gửi…' : register ? 'Tạo tài khoản' : 'Đăng nhập'}</button>
    </form>
    {error !== undefined ? <ApiErrorMessage error={error} /> : null}
    {created ? <p role="status">Đã tạo tài khoản. <Link to="/login">Đăng nhập</Link> để tiếp tục.</p> : null}
  </section>;
}

function Layout() {
  const client = useClient();
  const session = useSession();
  const location = useLocation();
  const [logoutError, setLogoutError] = useState<unknown>();
  const [signingOut, setSigningOut] = useState(false);
  useEffect(() => { document.getElementById('shared-content')?.focus(); }, [location.pathname]);
  async function logout() {
    setSigningOut(true); setLogoutError(undefined);
    try { await client.logout(); }
    catch (error) { setLogoutError(error); }
    finally { setSigningOut(false); }
  }
  return <>
    <a href="#shared-content">Đến nội dung chính</a>
    <nav aria-label="Điều hướng chính">
      <Link to="/">Trang chủ</Link>{' · '}<Link to="/login">Đăng nhập</Link>{' · '}
      <Link to="/register">Đăng ký</Link>{' · '}<Link to="/customer">Customer</Link>{' · '}
      <Link to="/seller">Seller</Link>{' · '}<Link to="/admin">Admin</Link>
    </nav>
    {session.status === 'authenticated' || signingOut || logoutError ? <button disabled={signingOut}
      onClick={() => { void logout(); }}>Đăng xuất</button> : null}
    {logoutError ? <><ApiErrorMessage error={logoutError} /><p>Chưa xác nhận thu hồi phiên trên máy chủ.</p></> : null}
    <main id="shared-content" className="container" tabIndex={-1}><Outlet /></main>
  </>;
}

// Prepared entry point only: production App/main stay unchanged until #102 is approved.
export function SharedRouter({ client }: { client: ApiClient }) {
  return <SessionProvider client={client}><BrowserRouter><Routes><Route element={<Layout />}>
    <Route index element={<section><h1>Marketplace</h1><p>Frontend shared — đang chờ duyệt tích hợp.</p></section>} />
    <Route path="login" element={<CredentialsForm key="login" />} />
    <Route path="register" element={<CredentialsForm key="register" register />} />
    <Route element={<Guard role="CUSTOMER" />}><Route path="customer" element={<Shell title="Customer" />} /></Route>
    <Route element={<Guard role="SELLER" />}><Route path="seller" element={<Shell title="Seller" />} /></Route>
    <Route element={<Guard role="ADMIN" />}><Route path="admin" element={<Shell title="Admin" />} /></Route>
    <Route path="*" element={<section><h1>Không tìm thấy trang</h1><Link to="/">Trang chủ</Link></section>} />
  </Route></Routes></BrowserRouter></SessionProvider>;
}
