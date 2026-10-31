import { useEffect } from 'react';
import type { ChangeEvent } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import type { z } from 'zod';
import { api, ApiError } from '../../shared/api';
import { useAuth } from '../../shared/auth';
import { contactSchema, storeSchema } from '../../shared/forms';
import { ErrorNote, Field, useAction } from '../../shared/ui';
import { SellerStoreOverview } from './SellerStoreOverview';
import type { SellerStatus } from './sellerStoreTypes';

interface Seller { readonly id: string; readonly version: string; readonly status: SellerStatus; readonly reviewReason: string | null }
interface Contact { readonly representativeName: string | null; readonly phone: string | null; readonly pickupAddress: string | null }
interface Store { readonly id: string; readonly sellerId: string; readonly name: string; readonly slug: string; readonly open: boolean; readonly version: string }

type ContactInput = z.infer<typeof contactSchema>;
type StoreInput = z.infer<typeof storeSchema>;

const orNull = <T,>(run: Promise<T>) => run.catch((error: unknown) => {
  if (error instanceof ApiError && error.status === 404) return null;
  throw error;
});

export const slugify = (name: string) => name.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[đĐ]/g, 'd').toLowerCase()
  .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 128);

function ContactForm({ contact, submitLabel, pending, onSubmit }: {
  readonly contact?: Contact; readonly submitLabel: string; readonly pending: boolean;
  readonly onSubmit: (contact: ContactInput) => void;
}) {
  const form = useForm<ContactInput>({
    resolver: zodResolver(contactSchema),
    defaultValues: { representativeName: contact?.representativeName ?? '', phone: contact?.phone ?? '', pickupAddress: contact?.pickupAddress ?? '' },
  });
  const { errors } = form.formState;
  return <form className="stack" noValidate onSubmit={form.handleSubmit(onSubmit)}>
    <Field label="Tên người đại diện" error={errors.representativeName}><input {...form.register('representativeName')} /></Field>
    <Field label="Số điện thoại" error={errors.phone}><input type="tel" {...form.register('phone')} /></Field>
    <Field label="Địa chỉ lấy hàng" error={errors.pickupAddress}><input {...form.register('pickupAddress')} /></Field>
    <button className="btn btn--primary" disabled={pending}>{submitLabel}</button>
  </form>;
}

function StoreForm({ store }: { readonly store: Store | null }) {
  const form = useForm<StoreInput>({ resolver: zodResolver(storeSchema), defaultValues: { name: store?.name ?? '', slug: store?.slug ?? '' } });
  const { errors } = form.formState;
  const save = useAction((value: StoreInput) => api(store ? `/api/stores/${store.id}` : '/api/stores', {
    method: store ? 'PUT' : 'POST',
    body: store ? { ...value, expectedVersion: store.version } : value,
  }), ['seller']);
  return <form className="panel stack" noValidate onSubmit={form.handleSubmit((value) => save.mutate(value))}>
    <h2>{store ? 'Thông tin cửa hàng' : 'Tạo cửa hàng'}</h2>
    <Field label="Tên cửa hàng" error={errors.name}><input {...form.register('name', {
      onChange: (e: ChangeEvent<HTMLInputElement>) => { if (!store) form.setValue('slug', slugify(e.target.value)); },
    })} /></Field>
    <Field label="Đường dẫn" error={errors.slug}><input {...form.register('slug')} /></Field>
    <ErrorNote error={save.error} />
    <button className="btn btn--primary" disabled={save.isPending}>Lưu cửa hàng</button>
  </form>;
}

export function SellerHomePage() {
  const auth = useAuth();
  const seller = useQuery({ queryKey: ['seller', 'me'], queryFn: () => orNull(api<{ seller: Seller; contact: Contact }>('/api/sellers/me')) });
  // Store routes need the SELLER role, which an applicant does not have yet.
  const canManage = auth.has('SELLER');
  const store = useQuery({ queryKey: ['seller', 'store'], queryFn: () => orNull(api<Store>('/api/stores/me')), enabled: !!seller.data && canManage });
  const myStore = canManage ? store.data : null;
  const register = useAction((contact: ContactInput) => api('/api/sellers', { method: 'POST', body: contact }), ['seller']);
  const resubmit = useAction((body: ContactInput & { expectedVersion: string }) => api('/api/sellers/me/resubmit', { method: 'POST', body }), ['seller']);
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
    <ContactForm submitLabel="Gửi hồ sơ người bán" pending={register.isPending} onSubmit={(contact) => register.mutate(contact)} />
  </div>;

  return <div className="stack">
    {myStore !== undefined && <SellerStoreOverview state={{ status: 'ready', seller: profile, store: myStore }} />}
    {profile.reviewReason && <p className="note">Lý do từ quản trị viên: {profile.reviewReason}</p>}
    {profile.status === 'REJECTED' && <div className="panel stack"><h2>Sửa và gửi lại hồ sơ</h2>
      <ContactForm contact={seller.data?.contact} submitLabel="Gửi lại hồ sơ" pending={resubmit.isPending}
        onSubmit={(contact) => resubmit.mutate({ ...contact, expectedVersion: profile.version })} />
    </div>}
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
