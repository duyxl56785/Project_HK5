import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../shared/api';
import { Badge, dateTime, ErrorNote, Loaded, money, shortId, useAction } from '../../shared/ui';

export interface OrderItem {
  readonly productId: string; readonly name: string | null; readonly sku: string | null; readonly quantity: number;
  readonly unitPrice: number; readonly lineTotal: number;
}
export interface Shipping { readonly name: string; readonly phone: string; readonly address: string }
export interface StoreOrder {
  readonly id: string; readonly orderId: string; readonly storeId: string; readonly storeName: string | null;
  readonly status: string; readonly codStatus: string; readonly statusReason: string | null;
  readonly gross: number; readonly platformFee: number; readonly sellerNet: number;
  readonly createdAt: string; readonly deliveredAt: string | null; readonly codCollectedAt: string | null;
  readonly settledAt: string | null; readonly shipping: Shipping | null; readonly items: readonly OrderItem[];
}
interface CustomerOrder {
  readonly id: string; readonly status: string; readonly total: number; readonly createdAt: string;
  readonly shipping: Shipping | null; readonly storeOrders: readonly StoreOrder[];
}
interface MyReview { readonly orderId: string; readonly productId: string }

/** Refused store orders are never collected, so they leave the amount the customer owes. */
const payable = (order: CustomerOrder) => order.storeOrders
  .filter((s) => s.status !== 'CANCELLED' && s.status !== 'REJECTED')
  .reduce((sum, s) => sum + Number(s.gross), 0);

export function OrdersPage() {
  const orders = useQuery({ queryKey: ['orders'], queryFn: () => api<CustomerOrder[]>('/api/orders?size=50') });
  return <div className="stack">
    <h1>Đơn hàng của tôi</h1>
    <Loaded query={orders} empty={(rows) => rows.length === 0}>{(rows) => <ul className="list">{rows.map((o) => <li key={o.id} className="row row--between">
      <div><Link to={`/orders/${o.id}`}><strong>#{shortId(o.id)}</strong></Link> <small className="muted">{dateTime(o.createdAt)}</small>
        <div className="row row--wrap">{o.storeOrders.map((s) => <span key={s.id}>{s.storeName ?? 'Cửa hàng'} <Badge value={s.status} /></span>)}</div></div>
      <strong>{money(payable(o))}</strong>
    </li>)}</ul>}</Loaded>
  </div>;
}

function ReviewForm({ orderId, productId }: { readonly orderId: string; readonly productId: string }) {
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');
  const send = useAction(() => api('/api/reviews', { method: 'POST', body: { orderId, productId, rating, comment } }), ['my-reviews', 'product-reviews', 'product']);
  return <form className="row row--wrap" onSubmit={(e) => { e.preventDefault(); send.mutate(undefined); }}>
    <label className="field field--inline">Đánh giá
      <select value={rating} onChange={(e) => setRating(Number(e.currentTarget.value))}>
        {[5, 4, 3, 2, 1].map((n) => <option key={n} value={n}>{n} sao</option>)}</select></label>
    <input aria-label="Nhận xét" className="grow" placeholder="Nhận xét của bạn" maxLength={2000} value={comment} onChange={(e) => setComment(e.currentTarget.value)} />
    <button className="btn" disabled={send.isPending}>Gửi</button>
    <ErrorNote error={send.error} />
  </form>;
}

function StoreOrderCard({ order, reviewed }: { readonly order: StoreOrder; readonly reviewed: ReadonlySet<string> }) {
  const [reason, setReason] = useState('');
  const cancel = useAction(() => api(`/api/orders/${order.orderId}/store-orders/${order.id}/cancel`, { method: 'POST', body: { reason } }), ['orders', 'order']);
  return <article className="panel stack">
    <div className="row row--between"><h2>{order.storeName ?? 'Cửa hàng'}</h2><Badge value={order.status} /></div>
    {order.statusReason && <p className="muted">Lý do: {order.statusReason}</p>}
    <ul className="list">{order.items.map((item) => <li key={item.productId} className="stack">
      <div className="row row--between">
        <span><Link to={`/products/${item.productId}`}>{item.name ?? item.sku ?? item.productId}</Link> × {item.quantity}</span>
        <span>{money(item.lineTotal)}</span>
      </div>
      {order.status === 'DELIVERED' && (reviewed.has(`${order.orderId}:${item.productId}`)
        ? <small className="muted">Đã đánh giá</small> : <ReviewForm orderId={order.orderId} productId={item.productId} />)}
    </li>)}</ul>
    <p>Tạm tính: <strong>{money(order.gross)}</strong></p>
    {order.status === 'PLACED' && <div className="row row--wrap">
      <input aria-label="Lý do hủy" className="grow" placeholder="Lý do hủy (không bắt buộc)" maxLength={500} value={reason} onChange={(e) => setReason(e.currentTarget.value)} />
      <button type="button" className="btn btn--danger" disabled={cancel.isPending} onClick={() => cancel.mutate(undefined)}>Hủy đơn của cửa hàng này</button>
    </div>}
    <ErrorNote error={cancel.error} />
  </article>;
}

export function OrderDetailPage() {
  const { id = '' } = useParams();
  const order = useQuery({ queryKey: ['order', id], queryFn: () => api<CustomerOrder>(`/api/orders/${id}`) });
  const mine = useQuery({ queryKey: ['my-reviews'], queryFn: () => api<MyReview[]>('/api/reviews/mine') });
  const reviewed = new Set(mine.data?.map((r) => `${r.orderId}:${r.productId}`));
  return <Loaded query={order}>{(o) => <div className="stack">
    <Link to="/orders">← Đơn hàng của tôi</Link>
    <h1>Đơn #{shortId(o.id)}</h1>
    <p className="muted">Đặt lúc {dateTime(o.createdAt)} · Thanh toán khi nhận hàng (COD)</p>
    {o.shipping && <p>Giao đến: <strong>{o.shipping.name}</strong> · {o.shipping.phone}<br />{o.shipping.address}</p>}
    {o.storeOrders.map((s) => <StoreOrderCard key={s.id} order={s} reviewed={reviewed} />)}
    <p className="price price--large">Tổng cộng: {money(payable(o))}</p>
  </div>}</Loaded>;
}
