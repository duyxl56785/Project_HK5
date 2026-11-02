import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api';
import { useAuth } from '../../shared/auth';
import { internationalPhone, optionalText, text } from '../../shared/forms';
import { ErrorNote, Field, Loaded, useAction } from '../../shared/ui';

interface Profile { readonly userId: string; readonly displayName: string | null; readonly phone: string | null }
export interface Address {
  readonly id: string; readonly recipientName: string; readonly phone: string;
  readonly line: string; readonly city: string; readonly isDefault: boolean;
}
const profileSchema = z.object({
  displayName: optionalText('Tên hiển thị', 100),
  phone: internationalPhone.or(z.literal('')),
});
const addressSchema = z.object({
  recipientName: text('Người nhận', 100), phone: internationalPhone,
  line: text('Địa chỉ', 255), city: text('Tỉnh/Thành phố', 100), isDefault: z.boolean(),
});
type ProfileInput = z.infer<typeof profileSchema>;
type AddressInput = z.infer<typeof addressSchema>;

const blank: AddressInput = { recipientName: '', phone: '', line: '', city: '', isDefault: false };

function ProfileForm({ profile }: { readonly profile: Profile }) {
  const form = useForm<ProfileInput>({ resolver: zodResolver(profileSchema), defaultValues: { displayName: profile.displayName ?? '', phone: profile.phone ?? '' } });
  const { errors } = form.formState;
  const save = useAction(({ displayName, phone }: ProfileInput) => api('/api/users/me', { method: 'PATCH', body: { displayName, phone: phone || null } }), ['profile']);
  return <form className="panel stack" noValidate onSubmit={form.handleSubmit((value) => save.mutate(value))}>
    <h2>Hồ sơ</h2>
    <Field label="Tên hiển thị" error={errors.displayName}><input {...form.register('displayName')} /></Field>
    <Field label="Số điện thoại (+84…)" error={errors.phone}><input {...form.register('phone')} /></Field>
    <ErrorNote error={save.error} />
    {save.isSuccess && <p className="note" role="status">Đã lưu.</p>}
    <button className="btn btn--primary" disabled={save.isPending}>Lưu hồ sơ</button>
  </form>;
}

function AddressForm({ initial, id, onDone }: { readonly initial: AddressInput; readonly id?: string; readonly onDone: () => void }) {
  const form = useForm<AddressInput>({
    resolver: zodResolver(addressSchema),
    defaultValues: { recipientName: initial.recipientName, phone: initial.phone, line: initial.line, city: initial.city, isDefault: initial.isDefault },
  });
  const { errors } = form.formState;
  const save = useAction((value: AddressInput) => api(id ? `/api/users/me/addresses/${id}` : '/api/users/me/addresses',
    { method: id ? 'PUT' : 'POST', body: value }), ['addresses'], onDone);
  return <form className="stack" noValidate onSubmit={form.handleSubmit((value) => save.mutate(value))}>
    <Field label="Người nhận" error={errors.recipientName}><input {...form.register('recipientName')} /></Field>
    <Field label="Số điện thoại (+84…)" error={errors.phone}><input {...form.register('phone')} /></Field>
    <Field label="Địa chỉ" error={errors.line}><input {...form.register('line')} /></Field>
    <Field label="Tỉnh/Thành phố" error={errors.city}><input {...form.register('city')} /></Field>
    <label className="check"><input type="checkbox" {...form.register('isDefault')} /> Đặt làm mặc định</label>
    <ErrorNote error={save.error} />
    <div className="row"><button className="btn btn--primary" disabled={save.isPending}>Lưu địa chỉ</button>
      <button type="button" className="btn" onClick={onDone}>Hủy</button></div>
  </form>;
}

export function AccountPage() {
  const { account } = useAuth();
  const profile = useQuery({ queryKey: ['profile'], queryFn: () => api<Profile>('/api/users/me') });
  const addresses = useQuery({ queryKey: ['addresses'], queryFn: () => api<Address[]>('/api/users/me/addresses') });
  const [editing, setEditing] = useState<string | null>(null);
  const remove = useAction((id: string) => api(`/api/users/me/addresses/${id}`, { method: 'DELETE' }), ['addresses']);
  return <div className="stack">
    <h1>Tài khoản</h1>
    <p className="muted">{account?.email}</p>
    <Loaded query={profile}>{(data) => <ProfileForm profile={data} />}</Loaded>
    <section className="panel stack">
      <div className="row row--between"><h2>Địa chỉ giao hàng</h2>
        <button type="button" className="btn" onClick={() => setEditing('new')}>Thêm địa chỉ</button></div>
      {editing === 'new' && <AddressForm initial={blank} onDone={() => setEditing(null)} />}
      <ErrorNote error={remove.error} />
      <Loaded query={addresses}>{(rows) => <ul className="list">{rows.map((a) => <li key={a.id}>
        {editing === a.id ? <AddressForm id={a.id} initial={a} onDone={() => setEditing(null)} /> : <div className="row row--between">
          <div><strong>{a.recipientName}</strong> · {a.phone}{a.isDefault && <span className="badge">Mặc định</span>}
            <br /><span className="muted">{a.line}, {a.city}</span></div>
          <div className="row">
            <button type="button" className="btn" onClick={() => setEditing(a.id)}>Sửa</button>
            <button type="button" className="btn" disabled={remove.isPending} onClick={() => remove.mutate(a.id)}>Xóa</button>
          </div>
        </div>}
      </li>)}</ul>}</Loaded>
    </section>
  </div>;
}
