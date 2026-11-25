import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { useAuth } from '../../shared/auth';
import { dateTime, ErrorNote, Field, Loaded, money, useAction } from '../../shared/ui';

type Kind = 'FLASH_SALE' | 'SUPER_SALE' | 'SELLER_CLEARANCE';
interface Campaign {
  id: string; name: string; kind: Kind; status: string; startsAt: string; endsAt: string;
  version: string | number; submittedAt: string | null;
}
interface Registration {
  productId: string; productName: string; storeId: string; storeName: string | null;
  baseUnitPrice: number; promotionalUnitPrice: number; status: string; reason: string | null;
  version: string | number;
}

const kinds: Record<Kind, string> = {
  FLASH_SALE: 'Flash Sale', SUPER_SALE: 'Super Sale', SELLER_CLEARANCE: 'Xả kho',
};

interface SellerProduct { id: string; name: string; unitPrice: string }

const statusLabels: Record<string, string> = {
  DRAFT: 'Bản nháp', APPROVED: 'Đã duyệt', PAUSED: 'Đã tạm dừng', CANCELLED: 'Đã hủy',
  PENDING: 'Chờ duyệt', REJECTED: 'Bị từ chối', WITHDRAWN: 'Đã rút',
};

async function activeProducts(term: string): Promise<SellerProduct[]> {
  const params = new URLSearchParams({ status: 'ACTIVE', size: '100', term });
  const page = await api<{products: SellerProduct[]}>('/api/seller/products?' + params);
  return page.products;
}

function CampaignForm({ admin, path }: { readonly admin: boolean; readonly path: string }) {
  const save = useAction((body: object) => api(path, { method: 'POST', body }), ['promotions']);
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const name = String(data.get('name')).trim();
    save.mutate(admin ? { name, kind: data.get('kind'), date: data.get('date') } : {
      name, startsAt: String(data.get('startsAt')) + ':00+07:00',
      endsAt: String(data.get('endsAt')) + ':00+07:00',
    });
  }
  return <form className="panel stack" aria-label="Tạo chương trình khuyến mãi" onSubmit={submit}>
    <Field label="Tên chương trình"><input required maxLength={120} name="name" /></Field>
    <div className="row row--wrap">
      {admin ? <>
        <Field label="Loại">
          <select name="kind"><option value="FLASH_SALE">Flash Sale</option><option value="SUPER_SALE">Super Sale</option></select>
        </Field>
        <Field label="Ngày chạy (giờ Việt Nam)"><input required type="date" name="date" /></Field>
      </> : <>
        <Field label="Bắt đầu (giờ Việt Nam)"><input required type="datetime-local" name="startsAt" /></Field>
        <Field label="Kết thúc (giờ Việt Nam)"><input required type="datetime-local" name="endsAt" /></Field>
      </>}
    </div>
    {admin && <small className="muted">Flash Sale chạy thứ Bảy, 22:00–24:00. Super Sale chạy cả ngày đã chọn.</small>}
    <div className="row row--wrap">
      <button type="submit" className="btn btn--primary" disabled={save.isPending}>Tạo chương trình</button>
    </div>
    <ErrorNote error={save.error} />
  </form>;
}

function CampaignPanel({ campaign, admin, canRegister, path, now }: {
  readonly campaign: Campaign; readonly admin: boolean; readonly canRegister: boolean;
  readonly path: string; readonly now: number;
}) {
  const id = path + '/' + campaign.id;
  const products = useQuery({
    queryKey: ['promotions', id, 'products'],
    queryFn: () => api<Registration[]>(id + '/products'),
  });
  const [reason, setReason] = useState('');
  const [term, setTerm] = useState('');
  const [selected, setSelected] = useState('');
  const before = now < Date.parse(campaign.startsAt);
  const ongoing = now < Date.parse(campaign.endsAt) && campaign.status !== 'CANCELLED';
  const isClearance = campaign.kind === 'SELLER_CLEARANCE';
  const draft = campaign.status === 'DRAFT';
  const waitingForSubmission = isClearance && draft && !campaign.submittedAt;
  const campaignOverdue = draft && !before;
  const canRegisterNow = !admin && canRegister && ongoing && before;
  const canReviewProducts = admin && ongoing && before;
  const hasPendingProducts = products.data?.some(row => row.status === 'PENDING');
  const available = useQuery({
    queryKey: ['promotions', 'owned-products', term],
    queryFn: () => activeProducts(term),
    enabled: canRegisterNow,
  });
  const change = useAction(({ suffix, body, method = 'POST' }: { suffix: string; body: object; method?: string }) =>
    api(id + suffix, { method, body }), ['promotions']);
  let label = statusLabels[campaign.status];
  if (campaignOverdue) label = 'Đã quá hạn duyệt';
  else if (draft && isClearance && campaign.submittedAt) label = 'Chờ duyệt chương trình';
  else if (campaign.status === 'APPROVED') label = !ongoing ? 'Đã kết thúc' : before ? 'Sắp diễn ra' : 'Đang diễn ra';
  const expectedVersion = String(campaign.version);
  const action = (name: string) => change.mutate({ suffix: '/actions', body: { action: name, expectedVersion } });
  function register(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const productId = String(data.get('productId')).trim();
    if (!available.data?.some(item => item.id === productId)) return;
    const prior = products.data?.find(item => item.productId === productId);
    change.mutate({ suffix: '/products', body: {
      productId, promotionalUnitPrice: String(data.get('price')), expectedCampaignVersion: expectedVersion,
      ...(prior?.status === 'WITHDRAWN' ? { expectedVersion: String(prior.version) } : {}),
    } });
  }
  return <article className="panel stack">
    <h2>{campaign.name}</h2>
    <p>{kinds[campaign.kind]} · {label} · {dateTime(campaign.startsAt)} – {dateTime(campaign.endsAt)}</p>
    {campaignOverdue && <p className="note note--error">
      Chương trình đã qua giờ bắt đầu {campaign.submittedAt ? 'khi còn chờ duyệt.' : 'khi chưa được duyệt.'}
      {isClearance && ' Seller có thể tạo chương trình xả kho mới với giờ bắt đầu trong tương lai.'}
    </p>}
    {admin && ongoing && before && waitingForSubmission && <p className="note">
      Seller cần bấm Gửi duyệt xả kho trước khi Admin duyệt chương trình.
    </p>}
    {admin && ongoing && (before || campaign.status === 'APPROVED' || campaign.status === 'PAUSED') && <div className="row row--wrap">
      {before && draft && !waitingForSubmission && <button type="button" className="btn btn--primary" disabled={change.isPending}
        onClick={() => action('approve')}>Duyệt chương trình</button>}
      {campaign.status === 'APPROVED' && <button type="button" className="btn btn--soft" disabled={change.isPending}
        onClick={() => action('pause')}>Tạm dừng</button>}
      {campaign.status === 'PAUSED' && <button type="button" className="btn btn--primary" disabled={change.isPending}
        onClick={() => action('resume')}>Tiếp tục</button>}
      {before && <button type="button" className="btn btn--danger" disabled={change.isPending}
        onClick={() => action('cancel')}>Hủy chương trình</button>}
    </div>}
    {admin && ongoing && before && campaign.status === 'DRAFT' && campaign.kind !== 'SELLER_CLEARANCE' &&
      <form className="stack" onSubmit={event => {
        event.preventDefault();
        const date = new FormData(event.currentTarget).get('date');
        change.mutate({ suffix: '/schedule', method: 'PATCH', body: { expectedVersion, date } });
      }}>
        <Field label="Ngày chạy mới (giờ Việt Nam)"><input required type="date" name="date" /></Field>
        <div className="row row--wrap">
          <button type="submit" className="btn btn--soft" disabled={change.isPending}>Đổi ngày</button>
        </div>
      </form>}
    {canRegisterNow && waitingForSubmission &&
      <div className="row row--wrap">
        <button type="button" className="btn btn--primary" disabled={change.isPending}
          onClick={() => change.mutate({ suffix: '/submit', body: { expectedVersion } })}>Gửi duyệt xả kho</button>
      </div>}
    {canRegisterNow &&
      <form className="stack" aria-label="Biểu mẫu đăng ký sản phẩm" onSubmit={register}>
        <div className="row row--wrap">
          <Field label="Tìm sản phẩm"><input value={term} maxLength={100} onChange={event => {
            setTerm(event.currentTarget.value); setSelected('');
          }} /></Field>
          <label className="field min-w-0 max-w-full">Sản phẩm
            <select required name="productId" value={selected} onChange={event => setSelected(event.currentTarget.value)}>
              <option value="">Chọn sản phẩm</option>
              {available.data?.map(product => <option key={product.id} value={product.id}>
                {product.name} · {money(product.unitPrice)}
              </option>)}
            </select>
          </label>
          <Field label="Giá sale (VND)"><input required inputMode="numeric" pattern="[1-9][0-9]*" name="price" /></Field>
        </div>
        <ErrorNote error={available.error} />
        <div className="row row--wrap">
          <button type="submit" className="btn btn--primary" disabled={change.isPending}>Đăng ký sản phẩm</button>
        </div>
      </form>}
    {canReviewProducts && hasPendingProducts && <>
      {!waitingForSubmission && campaign.status !== 'APPROVED' && <p className="note">
        {campaign.status === 'PAUSED'
          ? 'Tiếp tục chương trình trước khi duyệt sản phẩm.'
          : 'Duyệt chương trình trước khi duyệt sản phẩm.'}
      </p>}
      <Field label="Lý do từ chối">
        <input value={reason} maxLength={500} onChange={e => setReason(e.currentTarget.value)} />
      </Field>
    </>}
    <Loaded query={products}>{rows => <ul className="list">{rows.map(row => {
      const canWithdraw = ongoing && !admin && ['PENDING', 'APPROVED'].includes(row.status);
      const canDecide = canReviewProducts && row.status === 'PENDING';
      const pendingCancelled = row.status === 'PENDING' && campaign.status === 'CANCELLED';
      const overdue = row.status === 'PENDING' && !before && !pendingCancelled;
      const rowLabel = pendingCancelled ? 'Chương trình đã hủy'
        : overdue ? 'Đã quá hạn duyệt' : statusLabels[row.status];
      const badge = pendingCancelled ? 'cancelled' : overdue ? 'rejected' : row.status.toLowerCase();
      return <li key={row.productId} className="row row--between">
        <div>
          <div className="row row--wrap">
            <strong>{row.productName}</strong>
            <span className={`badge badge--${badge}`}>{rowLabel}</span>
          </div>
          <p>{admin && <>Cửa hàng {row.storeName ?? row.storeId} · </>}
            Giá gốc {money(row.baseUnitPrice)} · Giá sale {money(row.promotionalUnitPrice)}
          </p>
          {overdue && !campaignOverdue && <p>Đăng ký chưa được duyệt trước giờ bắt đầu chương trình.</p>}
          {row.reason && <p>{row.reason}</p>}
        </div>
        {canWithdraw && <div className="row row--wrap">
          <button type="button" className="btn btn--danger" disabled={change.isPending} onClick={() =>
            change.mutate({ suffix: '/products/' + row.productId + '/withdraw', body: { expectedVersion: String(row.version) } })}>
            Rút đăng ký
          </button>
        </div>}
        {canDecide && <div className="row row--wrap">
          {(campaign.status === 'APPROVED' ? ['approve', 'reject'] : ['reject']).map(decision => <button key={decision} type="button"
            className={decision === 'approve' ? 'btn btn--primary' : 'btn btn--danger'}
            disabled={change.isPending || (decision === 'reject' && !reason.trim())}
            onClick={() => change.mutate({ suffix: '/products/' + row.productId + '/decision', body: {
              decision, expectedVersion: String(row.version), ...(decision === 'reject' ? { reason: reason.trim() } : {}),
            } })}>{decision === 'approve' ? 'Duyệt sản phẩm' : 'Từ chối sản phẩm'}</button>)}
        </div>}
      </li>;
    })}</ul>}</Loaded>
    <ErrorNote error={change.error} />
  </article>;
}

export function PromotionsPage({ admin = false }: { readonly admin?: boolean }) {
  const auth = useAuth();
  const canRegister = auth.has('SELLER');
  const path = admin ? '/api/admin/promotions' : '/api/seller/promotions';
  const campaigns = useQuery({ queryKey: ['promotions', path], queryFn: () => api<Campaign[]>(path) });
  const [, refreshTime] = useState(0);
  const now = Date.now();
  useEffect(() => {
    const upcoming = (campaigns.data ?? []).flatMap(campaign => [Date.parse(campaign.startsAt), Date.parse(campaign.endsAt)])
      .filter(time => time > now);
    const next = Math.min(...upcoming);
    if (!Number.isFinite(next)) return;
    // A single timer keeps all cards current at schedule boundaries, even while the page is idle.
    const timer = window.setTimeout(() => refreshTime(tick => tick + 1), Math.min(next - Date.now(), 2_147_483_647));
    return () => window.clearTimeout(timer);
  }, [campaigns.data, now]);
  return <div className="stack">
    <h1>{admin ? 'Quản lý chương trình khuyến mãi' : 'Khuyến mãi cửa hàng'}</h1>
    {(admin || canRegister) && <CampaignForm admin={admin} path={path} />}
    <Loaded query={campaigns}>{rows => <>{rows.map(campaign =>
      <CampaignPanel key={campaign.id} campaign={campaign} admin={admin} canRegister={canRegister} path={path} now={now} />)}</>}</Loaded>
  </div>;
}

export function AdminPromotionsPage() { return <PromotionsPage admin />; }
