import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../../shared/api';
import { useAuth } from '../../shared/auth';
import { ErrorNote, useAction } from '../../shared/ui';
import { SellerStoreOverview } from './SellerStoreOverview';
import type { SellerStatus } from './sellerStoreTypes';

interface Seller { readonly id: string; readonly version: string; readonly status: SellerStatus; readonly reviewReason: string | null }
interface Store { readonly id: string; readonly sellerId: string; readonly name: string; readonly slug: string; readonly open: boolean; readonly version: string }

const orNull = <T,>(run: Promise<T>) => run.catch((error: unknown) => {
  if (error instanceof ApiError && error.status === 404) return null;
  throw error;
});

export const slugify = (name: string) => name.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[đĐ]/g, 'd').toLowerCase()
  .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 128);

function StoreForm({ store }: { readonly store: Store | null }) {
  const [name, setName] = useState(store?.name ?? '');
  const [slug, setSlug] = useState(store?.slug ?? '');
  const save = useAction(() => api(store ? `/api/stores/${store.id}` : '/api/stores', {
    method: store ? 'PUT' : 'POST',
    body: store ? { name, slug, expectedVersion: store.version } : { name, slug },
  }), ['seller']);
  function submit(event: FormEvent) { event.preventDefault(); save.mutate(undefined); }
  return <form className="panel stack" onSubmit={submit}>
    <h2>{store ? 'Thông tin cửa hàng' : 'Tạo cửa hàng'}</h2>
    <label className="field">Tên cửa hàng<input required maxLength={255} value={name}
      onChange={(e) => { const next = e.currentTarget.value; setName(next); if (!store) setSlug(slugify(next)); }} /></label>
    <label className="field">Đường dẫn<input required maxLength={128} pattern="[a-z0-9]+(-[a-z0-9]+)*" value={slug} onChange={(e) => setSlug(e.currentTarget.value)} /></label>
    <ErrorNote error={save.error} />
    <button className="btn btn--primary" disabled={save.isPending}>Lưu cửa hàng</button>
  </form>;
}

export function SellerHomePage() {
  const auth = useAuth();
  const seller = useQuery({ queryKey: ['seller', 'me'], queryFn: () => orNull(api<{ seller: Seller }>('/api/sellers/me')) });
  // Store routes need the SELLER role, which an applicant does not have yet.
  const canManage = auth.has('SELLER');
  const store = useQuery({ queryKey: ['seller', 'store'], queryFn: () => orNull(api<Store>('/api/stores/me')), enabled: !!seller.data && canManage });
  const myStore = canManage ? store.data : null;
  const register = useAction(() => api('/api/sellers', { method: 'POST', body: {} }), ['seller']);
  const resubmit = useAction((expectedVersion: string) => api('/api/sellers/me/resubmit', { method: 'POST', body: { expectedVersion } }), ['seller']);
  const setOpen = useAction((s: Store) => api(`/api/stores/${s.id}/open`, { method: 'PATCH', body: { open: !s.open, expectedVersion: s.version } }), ['seller']);

  const profile = seller.data?.seller;
  const approved = profile?.status === 'APPROVED';
  const stale = profile !== undefined && approved !== auth.has('SELLER');
  // The role change reaches the token only on refresh, and the relay applies it asynchronously.
  useEffect(() => {
    if (!stale) return;
    void auth.reload();
    const timer = setInterval(() => void auth.reload(), 5000);
    return () => clearInterval(timer);
  }, [stale, auth.reload]);

  if (seller.error) return <SellerStoreOverview state={{ status: 'error', onRetry: () => void seller.refetch() }} />;
  if (seller.isPending) return <SellerStoreOverview state={{ status: 'loading' }} />;
  if (!profile) return <div className="panel stack">
    <h1>Trở thành người bán</h1>
    <p>Gửi hồ sơ để quản trị viên xét duyệt. Sau khi được duyệt, bạn có thể tạo cửa hàng và đăng sản phẩm.</p>
    <ErrorNote error={register.error} />
    <button type="button" className="btn btn--primary" disabled={register.isPending} onClick={() => register.mutate(undefined)}>Gửi hồ sơ người bán</button>
  </div>;

  return <div className="stack">
    {myStore !== undefined && <SellerStoreOverview state={{ status: 'ready', seller: profile, store: myStore }} />}
    {profile.reviewReason && <p className="note">Lý do từ quản trị viên: {profile.reviewReason}</p>}
    {profile.status === 'REJECTED' && <button type="button" className="btn btn--primary" disabled={resubmit.isPending} onClick={() => resubmit.mutate(profile.version)}>Gửi lại hồ sơ</button>}
    {approved && stale && <p className="note" role="status">Đang cập nhật quyền người bán cho tài khoản…</p>}
    {approved && canManage && myStore !== undefined && <StoreForm key={myStore?.version ?? 'new'} store={myStore} />}
    {myStore && approved && <div className="row row--wrap">
      <button type="button" className="btn" disabled={setOpen.isPending} onClick={() => setOpen.mutate(myStore)}>{myStore.open ? 'Đóng cửa hàng' : 'Mở cửa hàng'}</button>
      <Link className="btn" to="/seller/products">Sản phẩm</Link>
      <Link className="btn" to="/seller/orders">Đơn hàng</Link>
      <Link className="btn" to="/seller/finance">Tài chính</Link>
    </div>}
    <ErrorNote error={resubmit.error ?? setOpen.error ?? store.error} />
  </div>;
}
