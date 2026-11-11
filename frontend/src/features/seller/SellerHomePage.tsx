import { useEffect } from 'react';
import type { ChangeEvent } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import type { z } from 'zod';
import { api, ApiError } from '../../shared/api';
import { useAuth } from '../../shared/auth';
import { sellerApplicationSchema, storeSchema } from '../../shared/forms';
import { ErrorNote, Field, useAction } from '../../shared/ui';
import { BUSINESS_TYPES, TERMS_VERSION } from '../policy/terms';
import type { BusinessType } from '../policy/terms';
import { SellerStoreOverview } from './SellerStoreOverview';
import type { SellerStatus } from './sellerStoreTypes';

interface Seller { readonly id: string; readonly version: string; readonly status: SellerStatus; readonly reviewReason: string | null }
interface Contact {
  readonly representativeName: string | null; readonly phone: string | null; readonly pickupAddress: string | null;
  readonly businessType: BusinessType | null; readonly idNumber: string | null; readonly taxCode: string | null;
  readonly email: string | null; readonly bankName: string | null; readonly bankAccountNumber: string | null;
  readonly bankAccountHolder: string | null;
}
interface Store { readonly id: string; readonly sellerId: string; readonly name: string; readonly slug: string; readonly open: boolean; readonly version: string }

type FormInput = z.infer<typeof sellerApplicationSchema>;
type ContactInput = Omit<FormInput, 'acceptTerms'> & { readonly termsVersion: string };
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
  const form = useForm<FormInput>({
    resolver: zodResolver(sellerApplicationSchema),
    defaultValues: {
      businessType: contact?.businessType ?? 'INDIVIDUAL', representativeName: contact?.representativeName ?? '',
      idNumber: contact?.idNumber ?? '', taxCode: contact?.taxCode ?? '', phone: contact?.phone ?? '', email: contact?.email ?? '',
      pickupAddress: contact?.pickupAddress ?? '', bankName: contact?.bankName ?? '',
      bankAccountNumber: contact?.bankAccountNumber ?? '', bankAccountHolder: contact?.bankAccountHolder ?? '',
    },
  });
  const { errors } = form.formState;
  // Acceptance is re-asked on every submission and sent as the rules version the applicant saw.
  const submit = ({ acceptTerms: _accepted, ...value }: FormInput) => onSubmit({ ...value, termsVersion: TERMS_VERSION });
  return <form className="stack" noValidate onSubmit={form.handleSubmit(submit)}>
    <fieldset className="stack">
      <legend>Thông tin người bán</legend>
      <Field label="Loại hình kinh doanh" error={errors.businessType}><select {...form.register('businessType')}>
        {Object.entries(BUSINESS_TYPES).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></Field>
      <Field label="Tên người đại diện" error={errors.representativeName}><input {...form.register('representativeName')} /></Field>
      <Field label="Số CCCD" error={errors.idNumber}><input inputMode="numeric" maxLength={12} {...form.register('idNumber')} /></Field>
      <Field label="Mã số thuế" error={errors.taxCode}><input placeholder="Không bắt buộc với cá nhân" {...form.register('taxCode')} /></Field>
    </fieldset>
    <fieldset className="stack">
      <legend>Liên hệ và lấy hàng</legend>
      <Field label="Số điện thoại" error={errors.phone}><input type="tel" {...form.register('phone')} /></Field>
      <Field label="Email liên hệ" error={errors.email}><input type="email" {...form.register('email')} /></Field>
      <Field label="Địa chỉ lấy hàng" error={errors.pickupAddress}><input {...form.register('pickupAddress')} /></Field>
    </fieldset>
    <fieldset className="stack">
      <legend>Tài khoản nhận tiền</legend>
      <Field label="Tên ngân hàng" error={errors.bankName}><input {...form.register('bankName')} /></Field>
      <Field label="Số tài khoản" error={errors.bankAccountNumber}><input inputMode="numeric" maxLength={19} {...form.register('bankAccountNumber')} /></Field>
      <Field label="Tên chủ tài khoản" error={errors.bankAccountHolder}><input {...form.register('bankAccountHolder')} /></Field>
    </fieldset>
    <label className="check"><input type="checkbox" {...form.register('acceptTerms')} />
      <span>Tôi đã đọc và đồng ý <Link to="/quy-dinh" target="_blank" rel="noopener">Quy định sàn</Link></span></label>
    {errors.acceptTerms?.message && <small className="field__error" role="alert">{errors.acceptTerms.message}</small>}
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
    <p>Gửi hồ sơ để quản trị viên xét duyệt. Sau khi được duyệt, bạn có thể tạo cửa hàng và đăng sản phẩm.
      Xem <Link to="/quy-dinh#nguoi-ban">điều kiện trở thành người bán</Link>.</p>
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
