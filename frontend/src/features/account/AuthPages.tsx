import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../shared/auth';
import { ErrorNote } from '../../shared/ui';

function AuthForm({ mode }: { readonly mode: 'login' | 'register' }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const raw = params.get('next') ?? '/';
  // Only same-site paths: "//host" would be an open redirect.
  const next = raw.startsWith('/') && !raw.startsWith('//') ? raw : '/';
  const login = mode === 'login';

  if (auth.account) return <Navigate to={next} replace />;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await (login ? auth.login(email, password) : auth.register(email, password));
      navigate(next, { replace: true });
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  }

  return <form className="panel panel--narrow stack" onSubmit={submit}>
    <h1>{login ? 'Đăng nhập' : 'Tạo tài khoản'}</h1>
    <label className="field">Email
      <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.currentTarget.value)} />
    </label>
    <label className="field">Mật khẩu{!login && ' (tối thiểu 12 ký tự)'}
      <input type="password" autoComplete={login ? 'current-password' : 'new-password'} required
        minLength={login ? 1 : 12} maxLength={128} value={password} onChange={(e) => setPassword(e.currentTarget.value)} />
    </label>
    <ErrorNote error={error} />
    <button className="btn btn--primary" disabled={busy}>{busy ? 'Đang xử lý…' : login ? 'Đăng nhập' : 'Đăng ký'}</button>
    <p className="muted">{login
      ? <>Chưa có tài khoản? <Link to={`/register?next=${encodeURIComponent(next)}`}>Đăng ký</Link></>
      : <>Đã có tài khoản? <Link to={`/login?next=${encodeURIComponent(next)}`}>Đăng nhập</Link></>}</p>
  </form>;
}

export const LoginPage = () => <AuthForm mode="login" />;
export const RegisterPage = () => <AuthForm mode="register" />;
