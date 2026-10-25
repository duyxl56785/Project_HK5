import { useRef, useState } from 'react';
import { projectCart } from '../cart/cartModel.ts';
import { canonicalCheckoutKey, createCheckoutOnce, prepareCheckout } from './checkoutModel.ts';
import type { CheckoutCodViewProps, CheckoutResponse } from './checkoutTypes.ts';
import './CheckoutCodView.css';

const errorText = {
  CART_CHANGED: 'Giỏ hàng đã thay đổi. Vui lòng tải lại dữ liệu trước khi thử thao tác mới.',
  PRICE_CHANGED: 'Giá sản phẩm đã thay đổi. Vui lòng kiểm tra lại giỏ hàng.',
  OUT_OF_STOCK: 'Có sản phẩm không đủ tồn kho. Vui lòng kiểm tra lại giỏ hàng.',
  UNAUTHORIZED: 'Bạn không có quyền đặt đơn hàng này.',
  STORE_UNAVAILABLE: 'Có cửa hàng hoặc sản phẩm chưa sẵn sàng để đặt.',
} as const;

type Display = 'idle' | 'sending' | 'unknown' | 'confirmed' | 'rejected';

export function CheckoutCodView({ state, onConfirm }: CheckoutCodViewProps) {
  const gate = useRef(createCheckoutOnce());
  const [display, setDisplay] = useState<Display>('idle');
  const [orderId, setOrderId] = useState<string | null>(null);
  const [reason, setReason] = useState<keyof typeof errorText | null>(null);

  if (state.status !== 'ready') {
    const messages = {
      loading: 'Đang tải dữ liệu Checkout',
      unauthorized: 'Vui lòng đăng nhập bằng tài khoản Customer hợp lệ',
      unavailable: 'Checkout hiện chưa sẵn sàng kết nối',
      empty: 'Giỏ hàng trống, không thể đặt hàng',
      unknown: 'Chưa xác định kết quả đặt hàng. Không gửi lại bằng mã yêu cầu mới; cần đối soát trạng thái đơn.',
    };
    return <section className="tv3-checkout" aria-busy={state.status === 'loading'}>
      <h2>Thanh toán khi nhận hàng</h2>
      <p role={state.status === 'unknown' || state.status === 'unavailable' ? 'alert' : undefined}>
        {messages[state.status]}
      </p>
    </section>;
  }

  const cart = projectCart(state.cart);
  const ready = cart && cart.itemCount > 0 && !cart.claimed
    && canonicalCheckoutKey(state.idempotencyKey);
  const request = ready ? prepareCheckout(state.cart, state.idempotencyKey) : null;
  if (!cart) return <section className="tv3-checkout" role="alert">
    <h2>Thanh toán khi nhận hàng</h2><p>Dữ liệu giỏ hàng không hợp lệ. Đã chặn gửi yêu cầu.</p>
  </section>;

  async function submit() {
    if (!request || !onConfirm) return;
    setDisplay('sending');
    const outcome = await gate.current.send(async () => {
      const response: CheckoutResponse = await onConfirm(request);
      if (response?.status === 'confirmed'
        && /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(response.orderId)) {
        setOrderId(response.orderId);
        return 'confirmed';
      }
      if (response?.status === 'rejected' && Object.hasOwn(errorText, response.reason)) {
        setReason(response.reason);
        return 'rejected';
      }
      return 'unknown';
    });
    if (outcome !== 'blocked') setDisplay(outcome);
  }

  return <section className="tv3-checkout" aria-label="Xác nhận đơn COD">
    <header><span className="tv3-checkout__eyebrow">TV3 · COD</span>
      <h2>Thanh toán khi nhận hàng</h2>
      <p>Chỉ thanh toán khi nhận hàng. Không tích hợp cổng thanh toán trực tuyến.</p>
    </header>
    <div className="tv3-checkout__stores">
      {cart.groups.map((store) => <article key={store.storeId}>
        <h3>Cửa hàng: {store.storeId}</h3>
        <ul>{store.lines.map((line) => <li key={line.productId}>
          <span>{line.title} × {line.quantity}</span><strong>{line.lineSubtotal}</strong>
        </li>)}</ul>
        <p>Tạm tính: {store.storeSubtotal}</p>
      </article>)}
    </div>
    <div className="tv3-checkout__summary">
      <span>Tổng tiền sản phẩm theo snapshot</span><strong>{cart.total}</strong>
      <small>Đơn vị tiền tệ/phí/giao hàng chưa được quyết định tại giao diện; cần xác nhận từ backend/M01.</small>
    </div>
    {cart.claimed && <p role="alert">Giỏ đang được Checkout khác giữ. Không gửi lặp.</p>}
    {!canonicalCheckoutKey(state.idempotencyKey) && <p role="alert">Thiếu mã yêu cầu đặt hàng hợp lệ.</p>}
    {!onConfirm && <p role="status">Chưa nối bộ xử lý đặt hàng đã xác thực. Chế độ xem trước chỉ hiển thị dữ liệu.</p>}
    {display === 'unknown' && <p role="alert">Kết quả chưa xác định. Giữ nguyên Idempotency-Key; đối soát đơn trước khi thao tác tiếp.</p>}
    {display === 'rejected' && <p role="alert">{reason ? errorText[reason] : 'Đơn hàng bị từ chối.'}</p>}
    {display === 'confirmed' && orderId && <p role="status">Backend đã xác nhận đơn hàng: {orderId}</p>}
    <button type="button" disabled={!request || !onConfirm || display !== 'idle'}
      onClick={() => { void submit(); }}>
      {display === 'sending' ? 'Đang xác minh yêu cầu…' : 'Xác nhận đặt hàng COD'}
    </button>
    <p className="tv3-checkout__notice">Không tự tạo khóa đặt hàng mới hoặc gửi lại khi kết quả chưa rõ.</p>
  </section>;
}
