import { useState } from 'react';
import type { FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { useAuth } from '../../shared/auth';
import { ErrorNote, Loaded, useAction } from '../../shared/ui';

interface Profile { readonly userId: string; readonly displayName: string | null; readonly phone: string | null }
export interface Address {
  readonly id: string; readonly recipientName: string; readonly phone: string;
  readonly line: string; readonly city: string; readonly isDefault: boolean;
}
type AddressInput = Omit<Address, 'id'>;

const blank: AddressInput = { recipientName: '', phone: '', line: '', city: '', isDefault: false };

function ProfileForm({ profile }: { readonly profile: Profile }) {
  const [displayName, setDisplayName] = useState(profile.displayName ?? '');
  const [phone, setPhone] = useState(profile.phone ?? '');
  const save = useAction(() => api('/api/users/me', { method: 'PATCH', body: { displayName, phone: phone || null } }), ['profile']);
  return <form className="panel stack" onSubmit={(e) => { e.preventDefault(); save.mutate(undefined); }}>
    <h2>Hồ sơ</h2>
    <label className="field">Tên hiển thị<input value={displayName} maxLength={100} onChange={(e) => setDisplayName(e.currentTarget.value)} /></label>
    <label className="field">Số điện thoại (+84…)<input value={phone} pattern="\+[1-9][0-9]{1,14}" onChange={(e) => setPhone(e.currentTarget.value)} /></label>
    <ErrorNote error={save.error} />
    {save.isSuccess && <p className="note" role="status">Đã lưu.</p>}
    <button className="btn btn--primary" disabled={save.isPending}>Lưu hồ sơ</button>
  </form>;
}

function AddressForm({ initial, id, onDone }: { readonly initial: AddressInput; readonly id?: string; readonly onDone: () => void }) {
  const [value, setValue] = useState(initial);
  const save = useAction(() => api(id ? `/api/users/me/addresses/${id}` : '/api/users/me/addresses',
    { method: id ? 'PUT' : 'POST', body: value }), ['addresses'], onDone);
  const field = (key: 'recipientName' | 'phone' | 'line' | 'city', label: string, extra = {}) =>
    <label className="field">{label}
      <input required value={value[key]} {...extra} onChange={(e) => { const next = e.currentTarget.value; setValue((v) => ({ ...v, [key]: next })); }} />
    </label>;
  function submit(event: FormEvent) { event.preventDefault(); save.mutate(undefined); }
  return <form className="stack" onSubmit={submit}>
    {field('recipientName', 'Người nhận', { maxLength: 100 })}
    {field('phone', 'Số điện thoại (+84…)', { pattern: '\\+[1-9][0-9]{1,14}' })}
    {field('line', 'Địa chỉ', { maxLength: 255 })}
    {field('city', 'Tỉnh/Thành phố', { maxLength: 100 })}
    <label className="check"><input type="checkbox" checked={value.isDefault}
      onChange={(e) => { const isDefault = e.currentTarget.checked; setValue((v) => ({ ...v, isDefault })); }} /> Đặt làm mặc định</label>
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
