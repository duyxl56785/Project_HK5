import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api';
import { text } from '../../shared/forms';
import { Badge, dateTime, ErrorNote, Loaded, money, shortId, StatusFilter, useAction } from '../../shared/ui';
import type { CategoryDto, CategoryStatus } from '../catalog/categoryTypes';
import { TotalsGrid } from '../finance/FinanceTable';
import type { StoreSummary, Totals } from '../finance/FinanceTable';
import type { StoreOrder } from '../orders/OrdersPage';
import type { Product } from '../seller/SellerProductsPage';

/** Reason box plus the buttons a moderation row offers; "reject"/"suspend" need the reason. */
function Moderate({ actions, needsReason, run, busy }: {
  readonly actions: readonly { action: string; label: string }[];
  readonly needsReason: readonly string[];
  readonly run: (action: string, reason: string) => void;
  readonly busy: boolean;
}) {
  const [reason, setReason] = useState('');
  if (actions.length === 0) return null;
  return <div className="row row--wrap">
    {actions.some((a) => needsReason.includes(a.action)) && <input aria-label="Lý do" placeholder="Lý do" maxLength={500} value={reason} onChange={(e) => setReason(e.currentTarget.value)} />}
    {actions.map((a) => <button key={a.action} type="button" className="btn" disabled={busy || (needsReason.includes(a.action) && !reason.trim())}
      onClick={() => run(a.action, reason)}>{a.label}</button>)}
  </div>;
}

interface SellerRow {
  readonly id: string; readonly ownerReference: string; readonly status: string; readonly reviewReason: string | null; readonly version: string;
  readonly contact: { readonly representativeName: string | null; readonly phone: string | null; readonly pickupAddress: string | null };
  readonly store: { readonly name: string } | null;
}
interface HistoryRow {
  readonly fromStatus: string | null; readonly toStatus: string; readonly reason: string | null;
  readonly reviewedBy: string | null; readonly at: string;
}

function SellerHistory({ id }: { readonly id: string }) {
  const [open, setOpen] = useState(false);
  const rows = useQuery({ queryKey: ['admin-sellers', id, 'history'], enabled: open, queryFn: () => api<HistoryRow[]>(`/api/admin/sellers/${id}/history`) });
  return <>
    <button type="button" className="link" aria-expanded={open} onClick={() => setOpen(!open)}>Lịch sử</button>
    {open && <Loaded query={rows} empty={(data) => data.length === 0}>{(data) => <ol className="list">{data.map((h) =>
      <li key={`${h.toStatus}-${h.at}`}><small className="muted">{dateTime(h.at)}</small> {h.fromStatus && <><Badge value={h.fromStatus} /> → </>}<Badge value={h.toStatus} />
        {h.reason && <small className="muted"> {h.reason}</small>}
        <br /><small className="muted">{h.reviewedBy ? `Admin ${h.reviewedBy}` : 'Người bán gửi hồ sơ'}</small></li>)}</ol>}</Loaded>}
  </>;
}

const SELLER_ACTIONS: Record<string, { action: string; label: string }[]> = {
  PENDING_REVIEW: [{ action: 'approve', label: 'Duyệt' }, { action: 'reject', label: 'Từ chối' }],
  APPROVED: [{ action: 'suspend', label: 'Tạm ngừng' }],
  SUSPENDED: [{ action: 'reinstate', label: 'Khôi phục' }],
};

export function AdminSellersPage() {
  const [status, setStatus] = useState('PENDING_REVIEW');
  const list = useQuery({ queryKey: ['admin-sellers', status], queryFn: () => api<SellerRow[]>(`/api/admin/sellers${status && `?status=${status}`}`) });
  const moderate = useAction(({ row, action, reason }: { row: SellerRow; action: string; reason: string }) =>
    api(`/api/admin/sellers/${row.id}/${action}`, { method: 'POST',
      body: ['reject', 'suspend'].includes(action) ? { expectedVersion: row.version, reason } : { expectedVersion: row.version } }), ['admin-sellers']);
  return <div className="stack">
    <h1>Duyệt người bán</h1>
    <StatusFilter value={status} options={['PENDING_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED']} onChange={setStatus} allLabel="Tất cả" />
    <ErrorNote error={moderate.error} />
    <Loaded query={list} empty={(rows) => rows.length === 0}>{(rows) => <table className="table">
      <thead><tr><th>Tài khoản</th><th>Hồ sơ</th><th>Cửa hàng</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{rows.map((row) => <tr key={row.id}>
        <td><code>{row.ownerReference}</code></td>
        <td>{row.contact.representativeName ?? '—'}<br /><small className="muted">{row.contact.phone} · {row.contact.pickupAddress}</small></td>
        <td>{row.store?.name ?? '—'}</td>
        <td><Badge value={row.status} />{row.reviewReason && <small className="muted"> {row.reviewReason}</small>}</td>
        <td><Moderate actions={SELLER_ACTIONS[row.status] ?? []} needsReason={['reject', 'suspend']} busy={moderate.isPending}
          run={(action, reason) => moderate.mutate({ row, action, reason })} /><SellerHistory id={row.id} /></td>
      </tr>)}</tbody></table>}</Loaded>
  </div>;
}

interface ProductRow { readonly product: Product; readonly storeName: string; readonly reviewReason: string | null }
const PRODUCT_ACTIONS: Record<string, { action: string; label: string }[]> = {
  PENDING_REVIEW: [{ action: 'approve', label: 'Duyệt' }, { action: 'reject', label: 'Từ chối' }],
  ACTIVE: [{ action: 'suspend', label: 'Đình chỉ' }],
};

export function AdminProductsPage() {
  const [status, setStatus] = useState('PENDING_REVIEW');
  const list = useQuery({ queryKey: ['admin-products', status], queryFn: () => api<ProductRow[]>(`/api/admin/products?status=${status}`) });
  const moderate = useAction(({ product, action, reason }: { product: Product; action: string; reason: string }) =>
    api(`/api/admin/products/${product.id}/${action}`, { method: 'POST', body: { expectedVersion: product.version, reason } }), ['admin-products']);
  return <div className="stack">
    <h1>Duyệt sản phẩm</h1>
    <StatusFilter value={status} options={['PENDING_REVIEW', 'ACTIVE', 'REJECTED', 'SUSPENDED', 'INACTIVE', 'DRAFT']} onChange={setStatus} />
    <ErrorNote error={moderate.error} />
    <Loaded query={list} empty={(rows) => rows.length === 0}>{(rows) => <table className="table">
      <thead><tr><th>Sản phẩm</th><th>Cửa hàng</th><th>Giá</th><th>Thao tác</th></tr></thead>
      <tbody>{rows.map(({ product, storeName, reviewReason }) => <tr key={product.id}>
        <td>{product.name} <small className="muted">{product.sku}</small>{reviewReason && <small className="muted"> — {reviewReason}</small>}</td>
        <td>{storeName}</td><td>{money(product.unitPrice)}</td>
        <td><Moderate actions={PRODUCT_ACTIONS[product.status] ?? []} needsReason={['reject', 'suspend']} busy={moderate.isPending}
          run={(action, reason) => moderate.mutate({ product, action, reason })} /></td>
      </tr>)}</tbody></table>}</Loaded>
  </div>;
}

const categorySchema = z.object({ name: text('Tên danh mục', 255), parentId: z.string() });
type CategoryInput = z.infer<typeof categorySchema>;

export function AdminCategoriesPage() {
  const categories = useQuery({ queryKey: ['categories', 'all'], queryFn: () => api<CategoryDto[]>('/api/categories/all') });
  const form = useForm<CategoryInput>({ resolver: zodResolver(categorySchema), defaultValues: { name: '', parentId: '' } });
  const create = useAction(({ name, parentId }: CategoryInput) => api('/api/categories', { method: 'POST', body: { name, parentId: parentId || null } }), ['categories'], () => form.resetField('name'));
  const rename = useAction(({ id, next, parent, status }: { id: string; next: string; parent: string | null; status?: CategoryStatus }) =>
    api(`/api/categories/${id}`, { method: 'PUT', body: { name: next, parentId: parent, status } }), ['categories']);
  const remove = useAction((id: string) => api(`/api/categories/${id}`, { method: 'DELETE' }), ['categories']);
  const nameOf = (id: string | null) => categories.data?.find((c) => c.id === id)?.name ?? '—';
  return <div className="stack">
    <h1>Danh mục</h1>
    <form className="panel row row--wrap" noValidate onSubmit={form.handleSubmit((value) => create.mutate(value))}>
      <input aria-label="Tên danh mục" className="grow" placeholder="Tên danh mục" {...form.register('name')} />
      <select aria-label="Danh mục cha" {...form.register('parentId')}>
        <option value="">Danh mục gốc</option>
        {categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select>
      <button className="btn btn--primary" disabled={create.isPending}>Thêm</button>
      {form.formState.errors.name && <small className="field__error" role="alert">{form.formState.errors.name.message}</small>}
    </form>
    <ErrorNote error={create.error ?? rename.error ?? remove.error} />
    <Loaded query={categories} empty={(rows) => rows.length === 0}>{(rows) => <table className="table">
      <thead><tr><th>Tên</th><th>Slug</th><th>Danh mục cha</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{rows.map((c) => <tr key={c.id} className={c.status === 'HIDDEN' ? 'muted' : ''}>
        <td><input aria-label={`Tên ${c.name}`} defaultValue={c.name} maxLength={255} onBlur={(e) => {
          const next = e.currentTarget.value.trim();
          if (next && next !== c.name) rename.mutate({ id: c.id, next, parent: c.parentId });
        }} /></td>
        <td>{c.slug}</td>
        <td>{nameOf(c.parentId)}</td>
        <td><select aria-label={`Trạng thái ${c.name}`} value={c.status} disabled={rename.isPending}
          onChange={(e) => rename.mutate({ id: c.id, next: c.name, parent: c.parentId, status: e.currentTarget.value as CategoryStatus })}>
          <option value="ACTIVE">Đang hiện</option><option value="HIDDEN">Đã ẩn</option></select></td>
        <td><button type="button" className="btn" disabled={remove.isPending} onClick={() => remove.mutate(c.id)}>Xóa</button></td>
      </tr>)}</tbody></table>}</Loaded>
  </div>;
}

interface ReviewRow {
  readonly id: string; readonly productName: string | null; readonly rating: number; readonly comment: string | null;
  readonly hidden: boolean; readonly createdAt: string;
}

export function AdminReviewsPage() {
  const list = useQuery({ queryKey: ['admin-reviews'], queryFn: () => api<ReviewRow[]>('/api/admin/reviews') });
  const toggle = useAction((row: ReviewRow) => api(`/api/admin/reviews/${row.id}/${row.hidden ? 'show' : 'hide'}`, { method: 'POST' }), ['admin-reviews']);
  return <div className="stack">
    <h1>Đánh giá</h1>
    <ErrorNote error={toggle.error} />
    <Loaded query={list} empty={(rows) => rows.length === 0}>{(rows) => <table className="table">
      <thead><tr><th>Sản phẩm</th><th>Sao</th><th>Nội dung</th><th>Thời gian</th><th>Thao tác</th></tr></thead>
      <tbody>{rows.map((r) => <tr key={r.id} className={r.hidden ? 'muted' : ''}>
        <td>{r.productName}</td><td>{r.rating}</td><td>{r.comment}</td><td>{dateTime(r.createdAt)}</td>
        <td><button type="button" className="btn" disabled={toggle.isPending} onClick={() => toggle.mutate(r)}>{r.hidden ? 'Hiện' : 'Ẩn'}</button></td>
      </tr>)}</tbody></table>}</Loaded>
  </div>;
}

interface AccountRow {
  readonly id: string; readonly email: string; readonly roles: readonly string[]; readonly status: string; readonly createdAt: string;
}

export function AdminAccountsPage() {
  const [term, setTerm] = useState('');
  const list = useQuery({ queryKey: ['admin-accounts', term], queryFn: () => api<AccountRow[]>(`/api/admin/accounts?term=${encodeURIComponent(term)}`) });
  const act = useAction(({ id, action }: { id: string; action: string }) => api(`/api/admin/accounts/${id}/${action}`, { method: 'POST' }), ['admin-accounts']);
  const lock = (row: AccountRow) => {
    if (window.confirm(`Khóa ${row.email}? Mọi phiên đăng nhập của tài khoản này sẽ kết thúc.`)) act.mutate({ id: row.id, action: 'disable' });
  };
  return <div className="stack">
    <h1>Tài khoản</h1>
    <input aria-label="Tìm theo email" placeholder="Tìm theo email" maxLength={254} value={term} onChange={(e) => setTerm(e.target.value)} />
    <ErrorNote error={act.error} />
    <Loaded query={list} empty={(rows) => rows.length === 0}>{(rows) => <table className="table">
      <thead><tr><th>Email</th><th>Vai trò</th><th>Trạng thái</th><th>Tạo lúc</th><th>Thao tác</th></tr></thead>
      <tbody>{rows.map((r) => <tr key={r.id} className={r.status === 'ACTIVE' ? '' : 'muted'}>
        <td>{r.email}</td><td>{r.roles.join(', ')}</td><td>{r.status === 'ACTIVE' ? 'Hoạt động' : 'Đã khóa'}</td><td>{dateTime(r.createdAt)}</td>
        <td>{!r.roles.includes('ADMIN') && (r.status === 'ACTIVE'
          ? <button type="button" className="btn" disabled={act.isPending} onClick={() => lock(r)}>Khóa</button>
          : <button type="button" className="btn" disabled={act.isPending} onClick={() => act.mutate({ id: r.id, action: 'enable' })}>Mở khóa</button>)}</td>
      </tr>)}</tbody></table>}</Loaded>
  </div>;
}

export function AdminOrdersPage() {
  const [cod, setCod] = useState('');
  const list = useQuery({ queryKey: ['admin-orders', cod], queryFn: () => api<StoreOrder[]>(`/api/admin/store-orders${cod && `?cod=${cod}`}`) });
  const act = useAction(({ id, action }: { id: string; action: string }) => api(`/api/admin/store-orders/${id}/${action}`, { method: 'POST' }), ['admin-orders', 'admin-finance']);
  return <div className="stack">
    <h1>Đối soát COD</h1>
    <StatusFilter value={cod} options={['PENDING', 'COLLECTED', 'VOID']} onChange={setCod} allLabel="Tất cả" />
    <ErrorNote error={act.error} />
    <Loaded query={list} empty={(rows) => rows.length === 0}>{(rows) => <table className="table">
      <thead><tr><th>Đơn</th><th>Cửa hàng</th><th>Trạng thái</th><th>COD</th><th>Số tiền</th><th>Thao tác</th></tr></thead>
      <tbody>{rows.map((o) => <tr key={o.id}>
        <td>#{shortId(o.orderId)}</td><td>{o.storeName ?? o.storeId}</td><td><Badge value={o.status} /></td><td><Badge value={o.codStatus} /></td><td>{money(o.gross)}</td>
        <td>
          {o.status === 'DELIVERED' && o.codStatus === 'PENDING' && <button type="button" className="btn" disabled={act.isPending} onClick={() => act.mutate({ id: o.id, action: 'cod-collected' })}>Xác nhận đã thu</button>}
          {o.codStatus === 'COLLECTED' && !o.settledAt && <button type="button" className="btn btn--primary" disabled={act.isPending} onClick={() => act.mutate({ id: o.id, action: 'settle' })}>Thanh toán {money(o.sellerNet)}</button>}
          {o.settledAt && <small className="muted">Đã thanh toán {dateTime(o.settledAt)}</small>}
        </td>
      </tr>)}</tbody></table>}</Loaded>
  </div>;
}

interface Dead { readonly id: string; readonly topic: string; readonly attempts: number; readonly lastError: string | null; readonly createdAt: string }

export function AdminFinancePage() {
  const report = useQuery({ queryKey: ['admin-finance'], queryFn: () => api<{ totals: Totals; stores: StoreSummary[] }>('/api/admin/finance') });
  const dead = useQuery({ queryKey: ['admin-dead'], queryFn: () => api<Dead[]>('/api/admin/outbox/dead') });
  return <div className="stack">
    <h1>Tài chính sàn</h1>
    <Loaded query={report}>{(data) => <>
      <TotalsGrid totals={data.totals} />
      {data.stores.length > 0 && <table className="table">
        <thead><tr><th>Cửa hàng</th><th>Đơn</th><th>Doanh thu</th><th>Phí sàn</th><th>COD chờ thu</th><th>Còn phải trả</th></tr></thead>
        <tbody>{data.stores.map((s) => <tr key={s.storeId}>
          <td>{s.storeName ?? s.storeId}</td><td>{s.totals.deliveredOrders}</td><td>{money(s.totals.gross)}</td>
          <td>{money(s.totals.platformFee)}</td><td>{money(s.totals.codPending)}</td><td>{money(s.totals.payableNet)}</td>
        </tr>)}</tbody></table>}
    </>}</Loaded>
    {(dead.data?.length ?? 0) > 0 && <section className="panel stack">
      <h2>Tác vụ nền cần xử lý tay</h2>
      <ul className="list">{dead.data?.map((d) => <li key={d.id}><strong>{d.topic}</strong> · {d.attempts} lần · {dateTime(d.createdAt)}<br /><small className="muted">{d.lastError}</small></li>)}</ul>
    </section>}
  </div>;
}
