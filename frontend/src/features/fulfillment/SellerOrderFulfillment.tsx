import { useId } from 'react';
import type { ReactNode } from 'react';
import type { FulfillmentState, SellerOrderStatus } from './sellerOrderTypes';
import { actionLabels, actionsFor, readStoreOrders } from './sellerOrderModel';
import './SellerOrderFulfillment.css';

export interface SellerOrderFulfillmentProps { readonly state: FulfillmentState }

const statusLabels: Record<SellerOrderStatus, string> = {
  PLACED: 'Mới đặt', ACCEPTED: 'Đã xác nhận', PREPARING: 'Đang chuẩn bị', READY_FOR_HANDOFF: 'Chờ bàn giao',
  DISPATCHED: 'Đang giao', DELIVERED: 'Đã giao', CANCELLED: 'Đã hủy', REJECTED: 'Đã từ chối',
};

export function SellerOrderFulfillment({ state }: SellerOrderFulfillmentProps) {
  const id = useId();
  let content: ReactNode;
  if (state.status === 'ready') {
    const orders = readStoreOrders(state.storeId, state.orders);
    if (!orders) {
      content = <div className="tv4-fulfillment__notice" role="alert"><h3>Dữ liệu đơn không hợp lệ</h3><p>Không thể hiển thị đơn chưa được xác thực theo Store.</p></div>;
    } else if (!orders.length) {
      content = <div className="tv4-fulfillment__notice" role="status"><h3>Chưa có đơn hàng</h3><p>Store này chưa có SellerOrder cần xử lý.</p></div>;
    } else {
      content = <div className="tv4-fulfillment__list">{orders.map((order) => {
        const actions = actionsFor(order.status);
        return <article className="tv4-fulfillment__order" key={order.id}>
          <div className="tv4-fulfillment__order-head"><div><span className="tv4-fulfillment__eyebrow">SellerOrder</span><h3><code>{order.id}</code></h3></div>
            <span className="tv4-fulfillment__status">{statusLabels[order.status]}</span></div>
          <dl><div><dt>Tổng tiền</dt><dd>{order.totalLabel}</dd></div><div><dt>COD</dt><dd>{order.codStatus === 'PAID' ? 'Đã thu' : order.codStatus === 'UNPAID' ? 'Chưa thu' : 'Chưa xác định'}</dd></div></dl>
          {actions.length ? <div className="tv4-fulfillment__actions">{actions.map((action) => {
            const busy = state.pending?.orderId === order.id;
            return <button key={action} type="button" disabled={busy} onClick={() => state.onAction?.(order.id, action)}>
              {busy && state.pending?.action === action ? 'Đang xử lý…' : actionLabels[action]}
            </button>;
          })}</div> : <p className="tv4-fulfillment__locked">Không còn thao tác fulfillment hợp lệ.</p>}
        </article>;
      })}</div>;
    }
  } else if (state.status === 'unauthorized') {
    content = <div className="tv4-fulfillment__notice" role="alert"><h3>Không có quyền xem đơn</h3><p>Store authority chưa cho phép truy cập SellerOrder này.</p></div>;
  } else if (state.status === 'error') {
    content = <div className="tv4-fulfillment__notice" role="alert"><h3>Không thể tải đơn</h3><button type="button" onClick={state.onRetry}>Thử lại</button></div>;
  } else if (state.status === 'loading') {
    content = <div className="tv4-fulfillment__notice" role="status"><h3>Đang tải đơn…</h3><p>Đang lấy SellerOrder mới nhất.</p></div>;
  } else {
    content = <div className="tv4-fulfillment__notice" role="status"><h3>Chưa có đơn hàng</h3><p>Store này chưa có SellerOrder cần xử lý.</p></div>;
  }
  return <section className="tv4-fulfillment" aria-labelledby={`${id}-title`} aria-busy={state.status === 'loading'}>
    <header><span className="tv4-fulfillment__eyebrow">TV4 · Fulfillment</span><h2 id={`${id}-title`}>Đơn hàng của Store</h2>
      <p>Chỉ hiển thị SellerOrder cùng Store đã được cấp quyền. Trạng thái và quyền cuối cùng vẫn do backend xác thực.</p></header>
    {content}
  </section>;
}
