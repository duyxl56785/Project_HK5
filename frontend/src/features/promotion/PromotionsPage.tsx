import { useState } from 'react';
import type { FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { useAuth } from '../../shared/auth';
import { dateTime, ErrorNote, Loaded, money, useAction } from '../../shared/ui';

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
  return <form className="panel stack" onSubmit={submit}>
    <label>Tên chương trình <input required maxLength={120} name="name" /></label>
    {admin ? <>
      <label>Loại <select name="kind"><option value="FLASH_SALE">Flash Sale</option><option value="SUPER_SALE">Super Sale</option></select></label>
      <label>Ngày chạy (giờ Việt Nam) <input required type="date" name="date" /></label>
    </> : <>
      <label>Bắt đầu (giờ Việt Nam) <input required type="datetime-local" name="startsAt" /></label>
      <label>Kết thúc (giờ Việt Nam) <input required type="datetime-local" name="endsAt" /></label>
    </>}
    <button disabled={save.isPending}>Tạo chương trình</button><ErrorNote error={save.error} />
  </form>;
}

function CampaignPanel({ campaign, admin, canRegister, path }: { readonly campaign: Campaign; readonly admin: boolean; readonly canRegister: boolean; readonly path: string }) {
  const id = path + '/' + campaign.id;
  const products = useQuery({
    queryKey: ['promotions', id, 'products'],
    queryFn: () => api<Registration[]>(id + '/products'),
  });
  const [reason, setReason] = useState('');
  const [term, setTerm] = useState('');
  const [selected, setSelected] = useState('');
  const available = useQuery({
    queryKey: ['promotions', 'owned-products', term],
    queryFn: () => activeProducts(term),
    enabled: !admin && canRegister && campaign.status !== 'CANCELLED' && Date.now() < Date.parse(campaign.startsAt),
  });
  const change = useAction(({ suffix, body, method = 'POST' }: { suffix: string; body: object; method?: string }) =>
    api(id + suffix, { method, body }), ['promotions']);
  const now = Date.now();
  const before = now < Date.parse(campaign.startsAt);
  const ongoing = now < Date.parse(campaign.endsAt) && campaign.status !== 'CANCELLED';
  const label = campaign.status === 'APPROVED'
    ? !ongoing ? 'Đã kết thúc' : before ? 'Sắp diễn ra' : 'Đang diễn ra'
    : statusLabels[campaign.status];
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
    {admin && ongoing && <div className="row">
      {before && campaign.status === 'DRAFT' && <button onClick={() => action('approve')}>Duyệt chương trình</button>}
      {campaign.status === 'APPROVED' && <button onClick={() => action('pause')}>Tạm dừng</button>}
      {campaign.status === 'PAUSED' && <button onClick={() => action('resume')}>Tiếp tục</button>}
      {before && <button onClick={() => action('cancel')}>Hủy chương trình</button>}
    </div>}
    {admin && ongoing && before && campaign.status === 'DRAFT' && campaign.kind !== 'SELLER_CLEARANCE' &&
      <form className="row" onSubmit={event => {
        event.preventDefault();
        const date = new FormData(event.currentTarget).get('date');
        change.mutate({ suffix: '/schedule', method: 'PATCH', body: { expectedVersion, date } });
      }}>
        <label>Ngày chạy mới (giờ Việt Nam) <input required type="date" name="date" /></label>
        <button disabled={change.isPending}>Đổi ngày</button>
      </form>}
    {!admin && canRegister && ongoing && before && campaign.kind === 'SELLER_CLEARANCE' && !campaign.submittedAt &&
      <button onClick={() => change.mutate({ suffix: '/submit', body: { expectedVersion } })}>Gửi duyệt xả kho</button>}
    {!admin && canRegister && ongoing && before && <form className="row" onSubmit={register}>
      <label>Tìm sản phẩm <input value={term} maxLength={100} onChange={event => {
        setTerm(event.currentTarget.value); setSelected('');
      }} /></label>
      <label>Sản phẩm <select required name="productId" value={selected} onChange={event => setSelected(event.currentTarget.value)}>
        <option value="">Chọn sản phẩm</option>
        {available.data?.map(product => <option key={product.id} value={product.id}>
          {product.name} · {money(product.unitPrice)}
        </option>)}
      </select></label>
      <ErrorNote error={available.error} />
      <label>Giá sale (VND) <input required inputMode="numeric" pattern="[1-9][0-9]*" name="price" /></label>
      <button disabled={change.isPending}>Đăng ký sản phẩm</button>
    </form>}
    {admin && <label>Lý do từ chối <input value={reason} maxLength={500} onChange={e => setReason(e.currentTarget.value)} /></label>}
    <Loaded query={products}>{rows => <ul>{rows.map(row => <li key={row.productId}>
      <strong>{row.productName}</strong>
      {admin && <span> · Cửa hàng {row.storeName ?? row.storeId}</span>}
      <span> · Giá gốc {money(row.baseUnitPrice)} · Giá sale {money(row.promotionalUnitPrice)} · {statusLabels[row.status]}</span>
      {row.reason && ' · ' + row.reason}
      {ongoing && !admin && ['PENDING','APPROVED'].includes(row.status) && <button onClick={() =>
        change.mutate({ suffix: '/products/' + row.productId + '/withdraw', body: { expectedVersion: String(row.version) } })}>
        Rút đăng ký
      </button>}
      {ongoing && before && admin && row.status === 'PENDING' && ['approve','reject'].map(decision =>
        <button key={decision} disabled={change.isPending || (decision==='reject' && !reason.trim())}
          onClick={() => change.mutate({ suffix: '/products/' + row.productId + '/decision', body: {
            decision, expectedVersion: String(row.version), ...(decision==='reject' ? { reason: reason.trim() } : {}),
          } })}>{decision==='approve' ? 'Duyệt sản phẩm' : 'Từ chối sản phẩm'}</button>)}
    </li>)}</ul>}</Loaded>
    <ErrorNote error={change.error} />
  </article>;
}

export function PromotionsPage({ admin = false }: { readonly admin?: boolean }) {
  const auth = useAuth();
  const canRegister = auth.has('SELLER');
  const path = admin ? '/api/admin/promotions' : '/api/seller/promotions';
  const campaigns = useQuery({ queryKey: ['promotions', path], queryFn: () => api<Campaign[]>(path) });
  return <div className="stack">
    <h1>{admin ? 'Quản lý chương trình khuyến mãi' : 'Khuyến mãi cửa hàng'}</h1>
    {(admin || canRegister) && <CampaignForm admin={admin} path={path} />}
    <Loaded query={campaigns}>{rows => <>{rows.map(campaign =>
      <CampaignPanel key={campaign.id} campaign={campaign} admin={admin} canRegister={canRegister} path={path} />)}</>}</Loaded>
  </div>;
}

export function AdminPromotionsPage() { return <PromotionsPage admin />; }
