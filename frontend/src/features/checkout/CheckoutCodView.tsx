import { useEffect, useRef, useState } from 'react';
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

export function CheckoutCodView({ state, onConfirm, trustedSessionScope }: CheckoutCodViewProps) {
  // Keep the result of every submitted key for this mounted host lifetime.
  // Unmounting/replacing a visible Cart scope must NOT make the same key retryable.
  const attempts = useRef(new Map<string, ReturnType<typeof createCheckoutOnce>>());

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
  if (!cart) return <section className="tv3-checkout" role="alert">
    <h2>Thanh toán khi nhận hàng</h2><p>Dữ liệu giỏ hàng không hợp lệ. Đã chặn gửi yêu cầu.</p>
  </section>;

  const ready = cart.itemCount > 0 && !cart.claimed
    && canonicalCheckoutKey(state.idempotencyKey);
  const request = ready ? prepareCheckout(state.cart, state.idempotencyKey) : null;

  // A keyed child is destroyed on unauthorized/loading or ANY Cart/session/attempt
  // scope switch. React will never render order A's local private state in scope B.
  // The optional trusted session marker must be supplied by the future TV1 host,
  // never inferred from user-controlled Cart data or treated as authorization.
  const scope = JSON.stringify([
    trustedSessionScope ?? null, state.idempotencyKey, cart.revision, cart.claimed,
    cart.groups.map(group => [
      group.storeId, group.sellerId,
      group.lines.map(line => [
        line.productId, line.title, line.quantity, line.unitPriceSnapshot,
      ]),
    ]),
  ]);
  const existingGate = attempts.current.get(state.idempotencyKey);
  return <CheckoutCodReady key={scope} cart={cart} request={request} checkoutKey={state.idempotencyKey}
    onConfirm={onConfirm} gateStatus={existingGate?.status ?? 'idle'}
    sendWithGate={async run => {
      let gate = attempts.current.get(state.idempotencyKey);
      if (!gate) {
        gate = createCheckoutOnce();
        attempts.current.set(state.idempotencyKey, gate);
      }
      return gate.send(run);
    }}/>;
}

type AttemptResult = 'confirmed' | 'unknown' | 'rejected';
type AttemptOutcome = AttemptResult | 'blocked';
type CheckoutCodReadyProps = {
  readonly cart: NonNullable<ReturnType<typeof projectCart>>;
  readonly request: ReturnType<typeof prepareCheckout>;
  readonly checkoutKey: string;
  readonly onConfirm?: CheckoutCodViewProps['onConfirm'];
  readonly gateStatus: ReturnType<typeof createCheckoutOnce>['status'];
  readonly sendWithGate: (run: () => Promise<AttemptResult>) => Promise<AttemptOutcome>;
};

function CheckoutCodReady({ cart, request, checkoutKey, onConfirm, gateStatus, sendWithGate }: CheckoutCodReadyProps) {
  const mounted = useRef(false);
  const [display, setDisplay] = useState<Display>('idle');
  const [orderId, setOrderId] = useState<string | null>(null);
  const [reason, setReason] = useState<keyof typeof errorText | null>(null);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  async function submit() {
    if (!request || !onConfirm || gateStatus !== 'idle' || display !== 'idle') return;
    setDisplay('sending');
    let confirmedOrder: string | null = null;
    let rejectedReason: keyof typeof errorText | null = null;
    const outcome = await sendWithGate(async () => {
      const response: CheckoutResponse = await onConfirm(request);
      if (response?.status === 'confirmed'
        && /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(response.orderId)) {
        confirmedOrder = response.orderId;
        return 'confirmed';
      }
      if (response?.status === 'rejected' && Object.hasOwn(errorText, response.reason)) {
        rejectedReason = response.reason;
        return 'rejected';
      }
      return 'unknown';
    });
    // The old child was removed when access/cart/session scope changed.
    // Late success/rejection must not mutate another scope or show its Order ID.
    if (!mounted.current) return;
    if (outcome === 'confirmed' && confirmedOrder) setOrderId(confirmedOrder);
    if (outcome === 'rejected' && rejectedReason) setReason(rejectedReason);
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
    {!canonicalCheckoutKey(checkoutKey) && <p role="alert">Thiếu mã yêu cầu đặt hàng hợp lệ.</p>}
    {!onConfirm && <p role="status">Chưa nối bộ xử lý đặt hàng đã xác thực. Chế độ xem trước chỉ hiển thị dữ liệu.</p>}
    {gateStatus !== 'idle' && display === 'idle' && <p role="alert">Mã yêu cầu đã được dùng ở lần Checkout trước. Không gửi lại hoặc đổi mã để thử lại; cần đối soát trạng thái.</p>}
    {display === 'unknown' && <p role="alert">Kết quả chưa xác định. Giữ nguyên Idempotency-Key; đối soát đơn trước khi thao tác tiếp.</p>}
    {display === 'rejected' && <p role="alert">{reason ? errorText[reason] : 'Đơn hàng bị từ chối.'}</p>}
    {display === 'confirmed' && orderId && <p role="status">Backend đã xác nhận đơn hàng: {orderId}</p>}
    <button type="button" disabled={!request || !onConfirm || gateStatus !== 'idle' || display !== 'idle'}
      onClick={() => { void submit(); }}>
      {display === 'sending' ? 'Đang xác minh yêu cầu…' : 'Xác nhận đặt hàng COD'}
    </button>
    <p className="tv3-checkout__notice">Không tự tạo khóa đặt hàng mới hoặc gửi lại khi kết quả chưa rõ.</p>
  </section>;
}
