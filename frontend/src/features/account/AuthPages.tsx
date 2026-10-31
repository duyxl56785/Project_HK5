import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { z } from 'zod';
import { useAuth } from '../../shared/auth';
import { ErrorNote, Field } from '../../shared/ui';

const email = z.string().trim().pipe(z.email('Email không hợp lệ'));
const loginSchema = z.object({ email, password: z.string().min(1, 'Nhập mật khẩu') });
const registerSchema = z.object({ email, password: z.string().min(12, 'Mật khẩu tối thiểu 12 ký tự').max(128, 'Mật khẩu tối đa 128 ký tự') });
type Credentials = z.infer<typeof loginSchema>;

function AuthForm({ mode }: { readonly mode: 'login' | 'register' }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const login = mode === 'login';
  const form = useForm<Credentials>({ resolver: zodResolver(login ? loginSchema : registerSchema), defaultValues: { email: '', password: '' } });
  const { errors, isSubmitting } = form.formState;
  const [error, setError] = useState<unknown>(null);
  const raw = params.get('next') ?? '/';
  // Only same-site paths: "//host" would be an open redirect.
  const next = raw.startsWith('/') && !raw.startsWith('//') ? raw : '/';

  if (auth.account) return <Navigate to={next} replace />;

  async function submit({ email, password }: Credentials) {
    setError(null);
    try {
      await (login ? auth.login(email, password) : auth.register(email, password));
      navigate(next, { replace: true });
    } catch (failure) {
      setError(failure);
    }
  }

  return <form className="panel panel--narrow stack" noValidate onSubmit={form.handleSubmit(submit)}>
    <h1>{login ? 'Đăng nhập' : 'Tạo tài khoản'}</h1>
    <Field label="Email" error={errors.email}><input type="email" autoComplete="email" {...form.register('email')} /></Field>
    <Field label={`Mật khẩu${login ? '' : ' (tối thiểu 12 ký tự)'}`} error={errors.password}>
      <input type="password" autoComplete={login ? 'current-password' : 'new-password'} {...form.register('password')} />
    </Field>
    <ErrorNote error={error} />
    <button className="btn btn--primary" disabled={isSubmitting}>{isSubmitting ? 'Đang xử lý…' : login ? 'Đăng nhập' : 'Đăng ký'}</button>
    <p className="muted">{login
      ? <>Chưa có tài khoản? <Link to={`/register?next=${encodeURIComponent(next)}`}>Đăng ký</Link></>
      : <>Đã có tài khoản? <Link to={`/login?next=${encodeURIComponent(next)}`}>Đăng nhập</Link></>}</p>
  </form>;
}

export const LoginPage = () => <AuthForm mode="login" />;
export const RegisterPage = () => <AuthForm mode="register" />;
