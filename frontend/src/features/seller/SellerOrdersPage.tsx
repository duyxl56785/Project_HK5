import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { Badge, dateTime, ErrorNote, Loaded, money, shortId, StatusFilter, useAction } from '../../shared/ui';
import type { StoreOrder } from '../orders/OrdersPage';
import { FinanceTable } from '../finance/FinanceTable';
import type { StoreSummary } from '../finance/FinanceTable';

const STATUSES = ['PLACED', 'ACCEPTED', 'PREPARING', 'READY_FOR_HANDOFF', 'DISPATCHED', 'DELIVERED', 'CANCELLED', 'REJECTED'];
const NEXT: Record<string, { action: string; label: string }> = {
  PLACED: { action: 'accept', label: 'Xác nhận đơn' },
  ACCEPTED: { action: 'prepare', label: 'Bắt đầu chuẩn bị' },
  PREPARING: { action: 'ready', label: 'Sẵn sàng bàn giao' },
  READY_FOR_HANDOFF: { action: 'dispatch', label: 'Giao cho vận chuyển' },
  DISPATCHED: { action: 'deliver', label: 'Xác nhận đã giao' },
};

function Row({ order }: { readonly order: StoreOrder }) {
  const [reason, setReason] = useState('');
  const act = useAction((action: string) => api(`/api/seller/orders/${order.id}/${action}`, { method: 'POST', body: { reason } }), ['seller-orders']);
  const next = NEXT[order.status];
  return <article className="panel stack">
    <div className="row row--between"><strong>Đơn #{shortId(order.orderId)}</strong><Badge value={order.status} /></div>
    <small className="muted">{dateTime(order.createdAt)}</small>
    {order.shipping && <p>{order.shipping.name} · {order.shipping.phone}<br />{order.shipping.address}</p>}
    <ul className="list">{order.items.map((i) => <li key={i.productId} className="row row--between">
      <span>{i.name ?? i.sku} × {i.quantity}</span><span>{money(i.lineTotal)}</span></li>)}</ul>
    <p>Tổng COD: <strong>{money(order.gross)}</strong> · Phí sàn {money(order.platformFee)} · Thực nhận {money(order.sellerNet)}</p>
    {order.statusReason && <p className="muted">Lý do: {order.statusReason}</p>}
    <div className="row row--wrap">
      {next && <button type="button" className="btn btn--primary" disabled={act.isPending} onClick={() => act.mutate(next.action)}>{next.label}</button>}
      {order.status === 'PLACED' && <>
        <input aria-label="Lý do từ chối" className="grow" placeholder="Lý do từ chối" maxLength={500} value={reason} onChange={(e) => setReason(e.currentTarget.value)} />
        <button type="button" className="btn btn--danger" disabled={act.isPending || !reason.trim()} onClick={() => act.mutate('reject')}>Từ chối</button>
      </>}
    </div>
    <ErrorNote error={act.error} />
  </article>;
}

export function SellerOrdersPage() {
  const [status, setStatus] = useState('');
  const list = useQuery({
    queryKey: ['seller-orders', status],
    queryFn: () => api<StoreOrder[]>(`/api/seller/orders${status && `?status=${status}`}`),
  });
  return <div className="stack">
    <h1>Đơn hàng của cửa hàng</h1>
    <StatusFilter value={status} options={STATUSES} onChange={setStatus} allLabel="Tất cả" />
    <Loaded query={list} empty={(rows) => rows.length === 0}>{(rows) => rows.map((o) => <Row key={o.id} order={o} />)}</Loaded>
  </div>;
}

export function SellerFinancePage() {
  const report = useQuery({ queryKey: ['seller-finance'], queryFn: () => api<StoreSummary>('/api/seller/finance') });
  return <div className="stack">
    <h1>Tài chính cửa hàng</h1>
    <Loaded query={report}>{(data) => <FinanceTable summary={data} />}</Loaded>
  </div>;
}
