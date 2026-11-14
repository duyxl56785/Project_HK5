import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { Badge, ErrorNote, Field, Loaded, useAction } from '../../shared/ui';

type VoucherState = 'DRAFT' | 'SCHEDULED' | 'ACTIVE' | 'PAUSED' | 'EXPIRED' | 'CANCELLED';
type DiscountType = 'FIXED' | 'PERCENT';
interface Voucher {
  id: string; code: string; name: string; discountType: DiscountType; discountValue: number;
  maxDiscount: number | null; minOrderAmount: number; usageLimit: number;
  startsAt: string; endsAt: string; status: VoucherState; version: string;
}
interface Limit { used: number; limit: number; defaultLimit: number; grant: null | { grantedLimit: number; validUntil: string } }
interface LimitRequest {
  id: string; storeId: string; requestedLimit: number; reason: string; requestedFrom: string; requestedUntil: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'; version: string;
  approvedLimit: number | null; approvedFrom: string | null; approvedUntil: string | null;
  decisionReason: string | null; createdAt: string;
}

const VOUCHERS = '/api/stores/me/vouchers';
const LIMIT_REQUESTS = '/api/stores/me/voucher-limit-requests';
const ADMIN_REQUESTS = '/api/admin/voucher-limit-requests';
const refresh = ['soopi-vouchers', 'soopi-voucher-limit', 'soopi-voucher-limit-requests', 'soopi-admin-voucher-requests'];

/** Soopi campaigns use Vietnamese business time independent of the browser timezone. */
export function vnTime(local: string) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(local)) throw Error('Thời gian không hợp lệ');
  const iso = `${local}:00+07:00`;
  if (!Number.isFinite(new Date(iso).getTime())) throw Error('Thời gian không hợp lệ');
  return iso;
}
function dateTime(iso: string) {
  return new Intl.DateTimeFormat('vi-VN', {timeZone:'Asia/Ho_Chi_Minh',dateStyle:'short',timeStyle:'short'}).format(new Date(iso));
}
function localTime(iso: string) {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(new Date(iso));
  const get = (kind: string) => parts.find((p) => p.type === kind)?.value ?? '';
  return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`;
}
function voucherInput(data: FormData) {
  const take = (key: string) => String(data.get(key) ?? '').trim();
  const discountType = take('discountType') as DiscountType;
  const discountValue = Number(take('discountValue'));
  const minOrderAmount = Number(take('minOrderAmount'));
  const usageLimit = Number(take('usageLimit'));
  const maxDiscount = take('maxDiscount') === '' ? null : Number(take('maxDiscount'));
  if (!Number.isSafeInteger(discountValue) || !Number.isSafeInteger(minOrderAmount) || !Number.isSafeInteger(usageLimit)
      || (maxDiscount !== null && !Number.isSafeInteger(maxDiscount))) throw Error('Số tiền không hợp lệ');
  return { code: take('code'), name: take('name'), discountType, discountValue, minOrderAmount, usageLimit,
    ...(discountType === 'PERCENT' ? { maxDiscount } : {}),
    startsAt: vnTime(take('startsAt')), endsAt: vnTime(take('endsAt')) };
}

function VoucherForm({ current, onSaved }: { current?: Voucher; onSaved: () => void }) {
  const save = useAction((body: ReturnType<typeof voucherInput>) => api(current ? `${VOUCHERS}/${current.id}` : VOUCHERS, {
    method: current ? 'PUT' : 'POST', body: current ? { ...body, expectedVersion: current.version } : body,
  }), refresh, onSaved);
  const [kind, setKind] = useState<DiscountType>(current?.discountType ?? 'FIXED');
  const [localError, setLocalError] = useState('');
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    try {
      setLocalError(''); save.mutate(voucherInput(new FormData(event.currentTarget)));
    } catch { setLocalError('Kiểm tra lại số tiền và thời gian.'); }
  };
  return <form className="panel stack" onSubmit={submit}>
    <h2>{current ? 'Sửa Voucher bản nháp' : 'Tạo Voucher mới'}</h2>
    <Field label="Mã Voucher"><input name="code" required minLength={4} maxLength={20} pattern="[A-Za-z0-9]{4,20}" defaultValue={current?.code} /></Field>
    <Field label="Tên chương trình"><input name="name" required maxLength={120} defaultValue={current?.name} /></Field>
    <Field label="Loại giảm giá"><select name="discountType" value={kind} onChange={(e) => setKind(e.currentTarget.value as DiscountType)}>
      <option value="FIXED">Giảm tiền cố định</option><option value="PERCENT">Giảm theo phần trăm</option></select></Field>
    <Field label={kind === 'FIXED' ? 'Giảm (VND)' : 'Giảm (%)'}><input name="discountValue" required type="number" min={1} max={kind === 'PERCENT' ? 90 : 999999999} step={1} defaultValue={current?.discountValue ?? ''} /></Field>
    {kind === 'PERCENT' && <Field label="Số tiền giảm tối đa (VND)"><input name="maxDiscount" type="number" min={1} max={999999999} step={1} required defaultValue={current?.maxDiscount ?? ''} /></Field>}
    <Field label="Giá trị đơn tối thiểu (VND)"><input name="minOrderAmount" type="number" min={0} max={999999999} step={1} required defaultValue={current?.minOrderAmount ?? 0} /></Field>
    <Field label="Số lượt sử dụng dự kiến"><input name="usageLimit" type="number" min={1} max={100000} step={1} required defaultValue={current?.usageLimit ?? 100} /></Field>
    <Field label="Bắt đầu (giờ Việt Nam)"><input name="startsAt" type="datetime-local" required defaultValue={current ? localTime(current.startsAt) : ''} /></Field>
    <Field label="Kết thúc (giờ Việt Nam)"><input name="endsAt" type="datetime-local" required defaultValue={current ? localTime(current.endsAt) : ''} /></Field>
    {localError && <p className="note note--error" role="alert">{localError}</p>}
    <ErrorNote error={save.error} />
    <button className="btn btn--primary" disabled={save.isPending}>{current ? 'Lưu bản nháp' : 'Tạo bản nháp'}</button>
  </form>;
}

export function SellerVoucherPage() {
  const [edit, setEdit] = useState<Voucher | undefined>();
  const vouchers = useQuery({ queryKey: ['soopi-vouchers'], queryFn: () => api<Voucher[]>(`${VOUCHERS}?page=0&size=50`) });
  const quota = useQuery({ queryKey: ['soopi-voucher-limit'], queryFn: () => api<Limit>('/api/stores/me/voucher-limit') });
  const requests = useQuery({ queryKey: ['soopi-voucher-limit-requests'], queryFn: () => api<LimitRequest[]>(`${LIMIT_REQUESTS}?page=0&size=50`) });
  const act = useAction(({ id, action, version }: { id: string; action: string; version: string }) =>
    api(`${VOUCHERS}/${id}/${action}`, { method: 'POST', body: { expectedVersion: version } }), refresh);
  const [requestError, setRequestError] = useState('');
  const requestKey = useRef(crypto.randomUUID());
  const request = useAction((body: { requestedLimit: number; reason: string; requestedFrom: string; requestedUntil: string }) =>
    api(LIMIT_REQUESTS, { method: 'POST', headers: { 'Idempotency-Key': requestKey.current }, body }), refresh, () => { requestKey.current = crypto.randomUUID(); });
  const cancelRequest = useAction((r: LimitRequest) => api(`${LIMIT_REQUESTS}/${r.id}/cancel`, { method: 'POST', body: { expectedVersion: r.version } }), refresh);
  const apply = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault(); const d = new FormData(e.currentTarget);
    try { setRequestError(''); request.mutate({ requestedLimit: Number(d.get('requestedLimit')), reason: String(d.get('reason') ?? '').trim(),
      requestedFrom: vnTime(String(d.get('requestedFrom'))), requestedUntil: vnTime(String(d.get('requestedUntil'))) }); }
    catch { setRequestError('Khoảng thời gian yêu cầu không hợp lệ.'); }
  };
  return <div className="stack">
    <h1>Voucher cửa hàng</h1>
    <p className="muted">Giai đoạn C1: quản lý voucher và hạn mức. Khách hàng chưa sử dụng voucher để giảm tiền khi Checkout.</p>
    <section className="panel stack"><h2>Hạn mức hiện tại</h2>
      <Loaded query={quota}>{(limit) => <><p>Đã sử dụng <strong>{limit.used}/{limit.limit}</strong> chương trình</p>
        <p className="muted">Mặc định 20 · Grant tối đa 50{limit.grant ? ` · Grant đến ${dateTime(limit.grant.validUntil)}` : ''}</p></>}</Loaded>
    </section>
    <VoucherForm key={edit?.id ?? 'new'} current={edit} onSaved={() => setEdit(undefined)} />
    <section className="panel stack"><h2>Danh sách Voucher</h2><ErrorNote error={act.error} />
      <Loaded query={vouchers} empty={(rows) => rows.length === 0}>{(rows) => <table className="table">
        <thead><tr><th>Mã</th><th>Loại</th><th>Hiệu lực</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
        <tbody>{rows.map((v) => <tr key={v.id}>
          <td>{v.code}<small className="muted"> {v.name}</small></td><td>{v.discountType === 'PERCENT' ? `${v.discountValue}%` : `${v.discountValue} đ`}</td>
          <td>{dateTime(v.startsAt)} – {dateTime(v.endsAt)}</td><td><Badge value={v.status} /></td>
          <td><div className="row row--wrap">
            {v.status === 'DRAFT' && <><button type="button" className="btn" onClick={() => setEdit(v)}>Sửa</button>
              <button type="button" className="btn btn--primary" disabled={act.isPending} onClick={() => act.mutate({id:v.id, action:'publish', version:v.version})}>Đăng</button></>}
            {['ACTIVE','SCHEDULED'].includes(v.status) && <button type="button" className="btn" disabled={act.isPending} onClick={() => act.mutate({id:v.id,action:'pause',version:v.version})}>Tạm dừng</button>}
            {v.status === 'PAUSED' && <button type="button" className="btn" disabled={act.isPending} onClick={() => act.mutate({id:v.id,action:'resume',version:v.version})}>Tiếp tục</button>}
            {!['CANCELLED','EXPIRED'].includes(v.status) && <button type="button" className="btn" disabled={act.isPending} onClick={() => act.mutate({id:v.id,action:'cancel',version:v.version})}>Hủy</button>}
          </div></td>
        </tr>)}</tbody></table>}</Loaded>
    </section>
    <form className="panel stack" onSubmit={apply}><h2>Yêu cầu tăng hạn mức voucher</h2>
      <Field label="Tổng số slot mong muốn (21–50)"><input type="number" name="requestedLimit" min={21} max={50} defaultValue={30} required /></Field>
      <Field label="Lý do (tối đa 500 ký tự)"><textarea name="reason" required maxLength={500} /></Field>
      <Field label="Từ thời điểm (giờ Việt Nam)"><input name="requestedFrom" type="datetime-local" required /></Field>
      <Field label="Đến thời điểm (giờ Việt Nam, tối đa 30 ngày)"><input name="requestedUntil" type="datetime-local" required /></Field>
      {requestError && <p className="note note--error" role="alert">{requestError}</p>}
      <ErrorNote error={request.error} />
      <button className="btn btn--primary" disabled={request.isPending}>Gửi yêu cầu Admin</button>
    </form>
    <section className="panel stack"><h2>Lịch sử yêu cầu</h2><ErrorNote error={cancelRequest.error} />
      <Loaded query={requests} empty={(rows)=>rows.length===0}>{(rows)=><table className="table"><thead><tr><th>Hạn mức</th><th>Lý do</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
        <tbody>{rows.map((r)=><tr key={r.id}><td>{r.requestedLimit}</td><td>{r.reason}{r.decisionReason && <p className="muted">Admin: {r.decisionReason}</p>}</td>
          <td><Badge value={r.status} /></td><td>{r.status==='PENDING' && <button type="button" className="btn" disabled={cancelRequest.isPending} onClick={()=>cancelRequest.mutate(r)}>Hủy yêu cầu</button>}</td></tr>)}</tbody></table>}</Loaded>
    </section>
  </div>;
}

export function AdminVoucherLimitPage() {
  const queue = useQuery({ queryKey: ['soopi-admin-voucher-requests'], queryFn: () => api<LimitRequest[]>(`${ADMIN_REQUESTS}?status=PENDING&size=50`) });
  const [draft, setDraft] = useState<{ id: string; approvedLimit: number; approvedFrom: string; approvedUntil: string; reason: string } | null>(null);
  const moderation = useAction(({ row, action, payload }: { row: LimitRequest; action: 'approve' | 'reject'; payload: object }) =>
    api(`${ADMIN_REQUESTS}/${row.id}/${action}`, { method: 'POST', body: { expectedVersion: row.version, ...payload } }), refresh);
  return <div className="stack"><h1>Duyệt hạn mức Voucher</h1>
    <p className="muted">Quyết định cấp quyền tạo thêm chương trình, không cấp tiền hay mã voucher cho Seller.</p>
    <ErrorNote error={moderation.error} />
    <Loaded query={queue} empty={(rows) => rows.length === 0}>{(rows) => <div className="stack">{rows.map((r) => <section className="panel stack" key={r.id}>
      <div className="row row--between"><h2>Store {r.storeId ?? ''}</h2><Badge value={r.status} /></div>
      <p>Xin {r.requestedLimit} slot · {dateTime(r.requestedFrom)} — {dateTime(r.requestedUntil)}</p>
      <p>Lý do: {r.reason}</p>
      <div className="row row--wrap"><button type="button" className="btn btn--primary" onClick={() => setDraft({id:r.id,approvedLimit:r.requestedLimit,
        approvedFrom:localTime(r.requestedFrom), approvedUntil:localTime(r.requestedUntil),reason:''})}>Xét duyệt</button>
        <button type="button" className="btn" disabled={moderation.isPending}
          onClick={() => {const reason=window.prompt('Lý do từ chối (bắt buộc, ≤500 ký tự):'); if(reason?.trim()) moderation.mutate({row:r,action:'reject',payload:{reason:reason.trim()}});}}>Từ chối</button></div>
      {draft?.id === r.id && <form className="stack" onSubmit={(e) => {e.preventDefault();
        try { moderation.mutate({row:r,action:'approve',payload:{approvedLimit:draft.approvedLimit,
          approvedFrom:vnTime(draft.approvedFrom),approvedUntil:vnTime(draft.approvedUntil),...(draft.reason.trim()?{reason:draft.reason.trim()}: {})}}); }
        catch { /* Invalid local time cannot be submitted. */ }
      }}>
        <Field label="Hạn mức được duyệt"><input type="number" min={21} max={r.requestedLimit} value={draft.approvedLimit} onChange={(e)=>setDraft({...draft,approvedLimit:Number(e.currentTarget.value)})} /></Field>
        <Field label="Bắt đầu (giờ Việt Nam)"><input type="datetime-local" value={draft.approvedFrom} onChange={(e)=>setDraft({...draft,approvedFrom:e.currentTarget.value})} /></Field>
        <Field label="Kết thúc (giờ Việt Nam)"><input type="datetime-local" value={draft.approvedUntil} onChange={(e)=>setDraft({...draft,approvedUntil:e.currentTarget.value})} /></Field>
        <Field label="Lý do duyệt một phần/rút ngắn thời hạn"><textarea maxLength={500} value={draft.reason} onChange={(e)=>setDraft({...draft,reason:e.currentTarget.value})} /></Field>
        <button className="btn btn--primary" disabled={moderation.isPending}>Xác nhận cấp hạn mức</button>
      </form>}
    </section>)}</div>}</Loaded>
  </div>;
}
