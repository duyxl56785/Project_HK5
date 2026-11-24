import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Link, useParams } from 'react-router-dom';
import { z } from 'zod';
import { api } from '../../shared/api';
import { optionalText } from '../../shared/forms';
import { Badge, dateTime, ErrorNote, Loaded, money, shortId, useAction } from '../../shared/ui';
import { CancelStoreOrder } from './CancelStoreOrder';
import { ReturnRequestPanel } from './ReturnRequestPanel';
import type { ReturnView } from './ReturnRequestPanel';
import { formatExactVnd } from '../checkout/checkoutApi';
import { orderPayable } from './orderPayable';

export interface OrderItem {
  readonly productId: string; readonly name: string | null; readonly sku: string | null; readonly quantity: number;
  readonly unitPrice: number; readonly lineTotal: number;
}
export interface Shipping { readonly name: string; readonly phone: string; readonly address: string }
export interface StoreOrder {
  readonly id: string; readonly orderId: string; readonly storeId: string; readonly storeName: string | null;
  readonly status: string; readonly codStatus: string; readonly statusReason: string | null;
  readonly gross: number; readonly platformFee: number | null; readonly sellerNet: number | null;
  readonly createdAt: string; readonly deliveredAt: string | null; readonly codCollectedAt: string | null;
  readonly settledAt: string | null; readonly shipping: Shipping | null; readonly items: readonly OrderItem[];
  readonly merchandiseAmount?: string; readonly voucherDiscount?: string; readonly voucherCode?: string | null;
  readonly grossExact?: string;
}
interface CustomerOrder {
  readonly id: string; readonly status: string; readonly overallStatus: string; readonly total: number; readonly createdAt: string;
  readonly shipping: Shipping | null; readonly storeOrders: readonly StoreOrder[];
}
const reviewSchema = z.object({ rating: z.number().int().min(1).max(5), comment: optionalText('Nhận xét', 1000) });
type ReviewInput = z.infer<typeof reviewSchema>;
interface MyReview { readonly orderId: string; readonly productId: string }

export function OrdersPage() {
  const orders = useQuery({ queryKey: ['orders'], queryFn: () => api<CustomerOrder[]>('/api/orders?size=50') });
  return <div className="stack">
    <h1>Đơn hàng của tôi</h1>
    <Loaded query={orders} empty={(rows) => rows.length === 0}>{(rows) => <ul className="list">{rows.map((o) => <li key={o.id} className="row row--between">
      <div><Link to={`/orders/${o.id}`}><strong>#{shortId(o.id)}</strong></Link> <Badge value={o.overallStatus} /> <small className="muted">{dateTime(o.createdAt)}</small>
        <div className="row row--wrap">{o.storeOrders.map((s) => <span key={s.id}>{s.storeName ?? 'Cửa hàng'} <Badge value={s.status} /></span>)}</div></div>
      <strong>{orderPayable(o.storeOrders)}</strong>
    </li>)}</ul>}</Loaded>
  </div>;
}

function ReviewForm({ orderId, productId }: { readonly orderId: string; readonly productId: string }) {
  const form = useForm<ReviewInput>({ resolver: zodResolver(reviewSchema), defaultValues: { rating: 5, comment: '' } });
  const { errors } = form.formState;
  const send = useAction((value: ReviewInput) => api('/api/reviews', { method: 'POST', body: { orderId, productId, ...value } }), ['my-reviews', 'product-reviews', 'product']);
  return <form className="row row--wrap" noValidate onSubmit={form.handleSubmit((value) => send.mutate(value))}>
    <label className="field field--inline">Đánh giá
      <select {...form.register('rating', { valueAsNumber: true })}>
        {[5, 4, 3, 2, 1].map((n) => <option key={n} value={n}>{n} sao</option>)}</select></label>
    <input aria-label="Nhận xét" className="grow" placeholder="Nhận xét của bạn" {...form.register('comment')} />
    <button className="btn" disabled={send.isPending}>Gửi</button>
    {errors.comment && <small className="field__error" role="alert">{errors.comment.message}</small>}
    <ErrorNote error={send.error} />
  </form>;
}

function StoreOrderCard({ order, reviewed, existingReturn, returnsLoading, returnsError }: {
  readonly order: StoreOrder; readonly reviewed: ReadonlySet<string>;
  readonly existingReturn: ReturnView | undefined; readonly returnsLoading: boolean; readonly returnsError: unknown;
}) {
  const cancel = useAction((reason: string) => api(`/api/orders/${order.orderId}/store-orders/${order.id}/cancel`, { method: 'POST', body: { reason } }), ['orders', 'order']);
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
    {order.voucherCode && order.merchandiseAmount && order.voucherDiscount && <>
      <p>Tiền hàng trước giảm: {formatExactVnd(order.merchandiseAmount)}</p>
      <p>Voucher {order.voucherCode}: −{formatExactVnd(order.voucherDiscount)}</p>
    </>}
    <p>Tạm tính: <strong>{order.grossExact ? formatExactVnd(order.grossExact) : money(order.gross)}</strong></p>
    {order.status === 'PLACED' && <CancelStoreOrder pending={cancel.isPending} onCancel={(reason) => cancel.mutate(reason)} />}
    <ErrorNote error={cancel.error} />
    <ReturnRequestPanel key={`${order.id}:${existingReturn?.requestId ?? 'none'}:${existingReturn?.status ?? 'none'}`}
      orderId={order.orderId} storeOrderId={order.id}
      status={order.status} deliveredAt={order.deliveredAt} settledAt={order.settledAt}
      existing={existingReturn} loading={returnsLoading} loadError={returnsError} />
  </article>;
}

export function OrderDetailPage() {
  const { id = '' } = useParams();
  const order = useQuery({ queryKey: ['order', id], queryFn: () => api<CustomerOrder>(`/api/orders/${id}`) });
  const mine = useQuery({ queryKey: ['my-reviews'], queryFn: () => api<MyReview[]>('/api/reviews/mine') });
  // Exactly one scoped GET for all Store Return states; no cross-buyer data is surfaced.
  const returns = useQuery({ queryKey: ['order-returns', id],
    queryFn: () => api<ReturnView[]>(`/api/orders/${id}/returns`), enabled: Boolean(id) });
  const reviewed = new Set(mine.data?.map((r) => `${r.orderId}:${r.productId}`));
  return <Loaded query={order}>{(o) => <div className="stack">
    <Link to="/orders">← Đơn hàng của tôi</Link>
    <div className="row row--between"><h1>Đơn #{shortId(o.id)}</h1><Badge value={o.overallStatus} /></div>
    <p className="muted">Đặt lúc {dateTime(o.createdAt)} · Thanh toán khi nhận hàng (COD)</p>
    {o.shipping && <p>Giao đến: <strong>{o.shipping.name}</strong> · {o.shipping.phone}<br />{o.shipping.address}</p>}
    {o.storeOrders.map((s) => <StoreOrderCard key={s.id} order={s} reviewed={reviewed}
      existingReturn={returns.data?.find((item) => item.storeOrderId === s.id)}
      returnsLoading={returns.isPending} returnsError={returns.error} />)}
    <p className="price price--large">Tổng cộng: {orderPayable(o.storeOrders)}</p>
  </div>}</Loaded>;
}
