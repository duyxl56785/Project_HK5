import { useId } from 'react';
import type { ReactNode } from 'react';
import { readFulfillmentBoard } from './fulfillmentModel';
import type { CodPaymentStatus, FulfillmentBoardState, SellerOrderStatus } from './fulfillmentTypes';
import './FulfillmentBoard.css';

export interface FulfillmentBoardProps { readonly state: FulfillmentBoardState }

const statusLabels: Record<SellerOrderStatus, string> = {
  PLACED: 'Đã đặt', ACCEPTED: 'Đã nhận', PREPARING: 'Đang chuẩn bị',
  READY_FOR_HANDOFF: 'Sẵn sàng bàn giao', DISPATCHED: 'Đã bàn giao', DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy', REJECTED: 'Đã từ chối',
};
const codLabels: Record<CodPaymentStatus, string> = {
  UNRECORDED: 'COD chưa được ghi nhận', PAID: 'COD đã được ghi nhận',
};
const messages = {
  loading: ['Đang tải đơn hàng…', 'Vui lòng đợi trong giây lát.'],
  empty: ['Chưa có đơn cần xử lý', 'Các SellerOrder được phép xem sẽ xuất hiện tại đây.'],
  unauthorized: ['Không có quyền xem đơn hàng', 'Vui lòng sử dụng tài khoản được phép truy cập cửa hàng này.'],
  unavailable: ['Chức năng chưa sẵn sàng', 'Hiện chưa thể kết nối dữ liệu đơn hàng. Vui lòng quay lại sau.'],
} as const;

function Notice({ title, detail, alert = false, children }: { title: string; detail: string; alert?: boolean; children?: ReactNode }) {
  return <div className="tv4-fulfillment__notice" role={alert ? 'alert' : 'status'}><h3>{title}</h3><p>{detail}</p>{children}</div>;
}

export function FulfillmentBoard({ state }: FulfillmentBoardProps) {
  const id = useId();
  let content: ReactNode;
  if (state.status === 'ready') {
    const data = readFulfillmentBoard(state);
    if (!data) {
      content = <Notice alert title="Dữ liệu đơn hàng không nhất quán" detail="Không thể hiển thị đơn hàng. Vui lòng tải lại sau." />;
    } else if (data.orders.length === 0) {
      content = <Notice title={messages.empty[0]} detail={messages.empty[1]} />;
    } else {
      content = <ul className="tv4-fulfillment__list" aria-label="Danh sách đơn hàng theo cửa hàng">
        {data.orders.map((order) => {
          const pending = state.pendingOrderId === order.id;
          const canSubmit = typeof state.onAction === 'function';
          const actions = order.actions ?? [];
          return <li key={order.id} className="tv4-fulfillment__order" aria-busy={pending || undefined}>
            <div className="tv4-fulfillment__order-head">
              <div><span className="tv4-fulfillment__eyebrow">SellerOrder</span><code>{order.id}</code></div>
              <span className={`tv4-fulfillment__status tv4-fulfillment__status--${order.status.toLowerCase()}`}>{statusLabels[order.status]}</span>
            </div>
            <dl className="tv4-fulfillment__details">
              <div><dt>Đơn tổng</dt><dd><code>{order.parentOrderId}</code></dd></div>
              <div><dt>Khởi tạo</dt><dd><time dateTime={order.createdAt}>{order.createdAt}</time></dd></div>
              <div><dt>COD</dt><dd>{codLabels[order.codStatus]}</dd></div>
            </dl>
            {actions.length > 0 && <div className="tv4-fulfillment__actions" aria-label={`Thao tác với đơn ${order.id}`}>
              {actions.map((action) => <button key={action.id} type="button" className={`tv4-fulfillment__action tv4-fulfillment__action--${action.tone ?? 'primary'}`}
                disabled={!canSubmit || pending} onClick={() => state.onAction?.(order.id, action.id)}>
                {pending ? 'Đang gửi yêu cầu…' : action.label}
              </button>)}
            </div>}
          </li>;
        })}
      </ul>;
    }
  } else if (state.status === 'error') {
    content = <Notice alert title="Không thể tải đơn hàng" detail="Vui lòng thử lại để lấy thông tin mới."><button className="tv4-fulfillment__retry" type="button" onClick={state.onRetry}>Thử lại</button></Notice>;
  } else {
    const message = messages[state.status];
    content = <Notice title={message[0]} detail={message[1]} />;
  }

  return <section className="tv4-fulfillment" aria-labelledby={`${id}-title`} aria-busy={state.status === 'loading'}>
    <header className="tv4-fulfillment__header"><span className="tv4-fulfillment__eyebrow">Góc vận hành cửa hàng</span>
      <h2 id={`${id}-title`}>Đơn giao hàng &amp; COD</h2>
      <p>Chỉ hiển thị dữ liệu và thao tác được host đã xác thực cung cấp. Giao diện không tự thay đổi trạng thái đơn hoặc ghi nhận COD.</p>
    </header>
    {content}
  </section>;
}
