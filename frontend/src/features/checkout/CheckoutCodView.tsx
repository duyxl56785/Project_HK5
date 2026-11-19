import { useEffect, useRef, useState } from 'react';
import { projectCart } from '../cart/cartModel.ts';
import { formatExactVnd, voucherErrorMessage } from './checkoutApi.ts';
import { canonicalCheckoutKey, createCheckoutOnce, prepareCheckout } from './checkoutModel.ts';
import type { CheckoutCodViewProps, CheckoutResponse, RefusedItem, VoucherCode, VoucherQuote, VoucherSelection } from './checkoutTypes.ts';
import './CheckoutCodView.css';

const errorText = {
  CART_CHANGED: 'Giỏ hàng đã thay đổi. Vui lòng tải lại dữ liệu trước khi thử thao tác mới.',
  PRICE_CHANGED: 'Giá sản phẩm đã thay đổi. Vui lòng kiểm tra lại giỏ hàng.',
  OUT_OF_STOCK: 'Có sản phẩm không đủ tồn kho. Vui lòng kiểm tra lại giỏ hàng.',
  UNAUTHORIZED: 'Bạn không có quyền đặt đơn hàng này.',
  STORE_UNAVAILABLE: 'Có cửa hàng hoặc sản phẩm chưa sẵn sàng để đặt.',
  CHECKOUT_PAUSED: 'Sàn đang tạm dừng nhận đơn. Giỏ hàng của bạn vẫn được giữ nguyên.',
  ITEMS_REJECTED: 'Chưa đặt được đơn nào. Giỏ hàng của bạn vẫn được giữ nguyên; các món sau cần xem lại:',
  VOUCHER_REFUSED: 'Voucher không còn áp dụng được. Hãy kiểm tra lại mã.',
} as const;

const refusedText: Readonly<Record<string, string>> = {
  PRODUCT_UNAVAILABLE: 'đã ngừng bán',
  STORE_UNAVAILABLE: 'cửa hàng đang đóng hoặc tạm khóa',
  CATALOG_CHANGED: 'thông tin sản phẩm đã đổi',
  PRICE_CHANGED: 'giá đã đổi',
  OUT_OF_STOCK: 'không đủ hàng',
};

type Display = 'idle' | 'sending' | 'unknown' | 'confirmed' | 'rejected';

export function CheckoutCodView({ state, onConfirm, onQuote, onRefreshAfterRejection, trustedSessionScope, storeNames }: CheckoutCodViewProps) {
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
  return <CheckoutCodReady key={scope} cart={cart} storeNames={storeNames} request={request} checkoutKey={state.idempotencyKey}
    onConfirm={onConfirm} onQuote={onQuote} onRefreshAfterRejection={onRefreshAfterRejection} gateStatus={existingGate?.status ?? 'idle'}
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
  readonly storeNames?: CheckoutCodViewProps['storeNames'];
  readonly request: ReturnType<typeof prepareCheckout>;
  readonly checkoutKey: string;
  readonly onConfirm?: CheckoutCodViewProps['onConfirm'];
  readonly onQuote?: CheckoutCodViewProps['onQuote'];
  readonly onRefreshAfterRejection?: () => void;
  readonly gateStatus: ReturnType<typeof createCheckoutOnce>['status'];
  readonly sendWithGate: (run: () => Promise<AttemptResult>) => Promise<AttemptOutcome>;
};

function CheckoutCodReady({ cart, storeNames, request, checkoutKey, onConfirm, onQuote, onRefreshAfterRejection,
  gateStatus, sendWithGate }: CheckoutCodReadyProps) {
  const mounted = useRef(false);
  const [display, setDisplay] = useState<Display>('idle');
  const [orderId, setOrderId] = useState<string | null>(null);
  const [reason, setReason] = useState<keyof typeof errorText | null>(null);
  const [refused, setRefused] = useState<readonly RefusedItem[]>([]);
  const [codes, setCodes] = useState<Record<string, string>>({});
  const [quote, setQuote] = useState<VoucherQuote | null>(null);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [quoteStore, setQuoteStore] = useState<string | null>(null);
  const [voucherRefusal, setVoucherRefusal] = useState<string | null>(null);
  const quoteBusy = useRef(false);
  const applied = new Map(quote?.stores.map((store) => [store.storeId, store]));
  const hasUnapplied = Object.entries(codes).some(([store, code]) =>
    code.trim().toUpperCase() !== (applied.get(store)?.voucherCode ?? ''));
  const voucherEditable = Boolean(onQuote && request && gateStatus === 'idle' && display === 'idle');
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  async function changeVoucher(storeId: string, remove: boolean) {
    if (!onQuote || !request || !voucherEditable || quoteBusy.current) return;
    const code = remove ? '' : (codes[storeId] ?? '').trim().toUpperCase();
    if (!remove && !/^[A-Z0-9]{4,20}$/.test(code)) {
      setQuoteError('Nhập mã từ 4 đến 20 ký tự chữ hoặc số.');
      return;
    }
    const pending: VoucherCode[] = (quote?.stores ?? []).flatMap((store) =>
      store.storeId !== storeId && store.voucherCode ? [{ storeId: store.storeId, code: store.voucherCode }] : []);
    if (code) pending.push({ storeId, code });
    setQuoteError(null);
    if (!pending.length) {
      setQuote(null);
      setCodes((old) => ({ ...old, [storeId]: '' }));
      return;
    }
    quoteBusy.current = true;
    setQuoteStore(storeId);
    try {
      const priced = await onQuote(request, pending);
      if (!mounted.current) return;
      const expectedStores = new Set(cart.groups.map((group) => group.storeId));
      if (priced.stores.length !== expectedStores.size || priced.stores.some((store) => !expectedStores.has(store.storeId)))
        throw new Error('Quote Stores differ from the Cart');
      setQuote(priced);
      setCodes((old) => ({ ...old, [storeId]: code }));
    } catch (error) {
      if (mounted.current) setQuoteError(voucherErrorMessage(error));
    } finally {
      quoteBusy.current = false;
      if (mounted.current) setQuoteStore(null);
    }
  }

  async function submit() {
    if (!request || !onConfirm || gateStatus !== 'idle' || display !== 'idle' || quoteBusy.current || hasUnapplied) return;
    const selected: VoucherSelection[] = (quote?.stores ?? []).flatMap((store) =>
      store.voucherCode && store.quoteFingerprint
        ? [{ storeId: store.storeId, code: store.voucherCode, quoteFingerprint: store.quoteFingerprint }] : []);
    const submission = selected.length ? { ...request, vouchers: selected } : request;
    setDisplay('sending');
    let confirmedOrder: string | null = null;
    let rejectedReason: keyof typeof errorText | null = null;
    let refusedItems: readonly RefusedItem[] = [];
    let voucherMessage: string | null = null;
    const outcome = await sendWithGate(async () => {
      const response: CheckoutResponse = await onConfirm(submission);
      if (response?.status === 'confirmed'
        && /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(response.orderId)) {
        confirmedOrder = response.orderId;
        return 'confirmed';
      }
      if (response?.status === 'rejected' && Object.hasOwn(errorText, response.reason)) {
        rejectedReason = response.reason;
        refusedItems = response.items ?? [];
        voucherMessage = response.voucherMessage ?? null;
        return 'rejected';
      }
      return 'unknown';
    });
    // The old child was removed when access/cart/session scope changed.
    // Late success/rejection must not mutate another scope or show its Order ID.
    if (!mounted.current) return;
    if (outcome === 'confirmed' && confirmedOrder) setOrderId(confirmedOrder);
    if (outcome === 'rejected' && rejectedReason) {
      setReason(rejectedReason);
      setRefused(refusedItems);
      setVoucherRefusal(voucherMessage);
    }
    if (outcome !== 'blocked') setDisplay(outcome);
  }

  return <section className="tv3-checkout" aria-label="Xác nhận đơn COD">
    <header>
      <h2>Thanh toán khi nhận hàng</h2>
      <p>Chỉ thanh toán khi nhận hàng. Không tích hợp cổng thanh toán trực tuyến.</p>
    </header>
    <div className="tv3-checkout__stores">
      {cart.groups.map((store) => <article key={store.storeId}>
        <h3>Cửa hàng: {storeNames?.[store.storeId] ?? store.storeId}</h3>
        <ul>{store.lines.map((line) => <li key={line.productId}>
          <span>{line.title} × {line.quantity}</span><strong>{line.lineSubtotal}</strong>
        </li>)}</ul>
        <p>Tạm tính: {store.storeSubtotal}</p>
        {onQuote && <div className="tv3-checkout__voucher">
          <label>Mã voucher · {storeNames?.[store.storeId] ?? store.storeId}
            <input value={codes[store.storeId] ?? ''} maxLength={20} autoComplete="off"
              disabled={!voucherEditable || quoteStore !== null}
              onChange={(event) => setCodes({ ...codes, [store.storeId]: event.currentTarget.value })} />
          </label>
          <button type="button" disabled={!voucherEditable || quoteStore !== null || !codes[store.storeId]?.trim()}
            onClick={() => { void changeVoucher(store.storeId, false); }}>
            {quoteStore === store.storeId ? 'Đang kiểm tra mã…' : 'Áp dụng voucher'}
          </button>
          {(codes[store.storeId] || applied.get(store.storeId)?.voucherCode) && <button type="button"
            disabled={!voucherEditable || quoteStore !== null} onClick={() => { void changeVoucher(store.storeId, true); }}>
            Bỏ mã
          </button>}
          {applied.get(store.storeId)?.voucherCode && <dl aria-label="Báo giá voucher">
            <dt>Tiền hàng trước giảm</dt><dd>{formatExactVnd(applied.get(store.storeId)!.merchandiseAmount)}</dd>
            <dt>Voucher {applied.get(store.storeId)!.voucherCode}</dt><dd>−{formatExactVnd(applied.get(store.storeId)!.discountAmount)}</dd>
            <dt>Phải trả cửa hàng</dt><dd>{formatExactVnd(applied.get(store.storeId)!.gross)}</dd>
            <dt>Phí sàn do cửa hàng chịu</dt><dd>{formatExactVnd(applied.get(store.storeId)!.platformFee)}</dd>
          </dl>}
        </div>}
      </article>)}
    </div>
    <div className="tv3-checkout__summary">
      <span>Tổng tiền thanh toán khi nhận hàng</span><strong>{quote ? formatExactVnd(quote.total) : cart.total}</strong>
      <small>Đơn vị: VND. Tổng tiền chính thức do hệ thống xác nhận khi tạo đơn.</small>
    </div>
    {quoteError && <p role="alert">{quoteError}</p>}
    {cart.claimed && <p role="alert">Giỏ đang được Checkout khác giữ. Không gửi lặp.</p>}
    {!canonicalCheckoutKey(checkoutKey) && <p role="alert">Thiếu mã yêu cầu đặt hàng hợp lệ.</p>}
    {gateStatus !== 'idle' && display === 'idle' && <p role="alert">Mã yêu cầu đã được dùng ở lần Checkout trước. Không gửi lại hoặc đổi mã để thử lại; cần đối soát trạng thái.</p>}
    {display === 'unknown' && <p role="alert">Kết quả chưa xác định. Giữ nguyên Idempotency-Key; đối soát đơn trước khi thao tác tiếp.</p>}
    {display === 'rejected' && <div role="alert">
      <p>{voucherRefusal ?? (reason ? errorText[reason] : 'Đơn hàng bị từ chối.')}</p>
      {refused.length > 0 && <ul>{refused.map((item) => <li key={item.productId}>
        {cart.groups.flatMap((store) => store.lines).find((line) => line.productId === item.productId)?.title ?? 'Sản phẩm'}: {refusedText[item.reason] ?? 'không đặt được'}
      </li>)}</ul>}
      {onQuote && onRefreshAfterRejection && <button type="button" onClick={onRefreshAfterRejection}>Tải lại báo giá</button>}
    </div>}
    {display === 'confirmed' && orderId && <p role="status">Backend đã xác nhận đơn hàng: {orderId}</p>}
    <button type="button" disabled={!request || !onConfirm || gateStatus !== 'idle' || display !== 'idle'
      || quoteStore !== null || hasUnapplied}
      onClick={() => { void submit(); }}>
      {display === 'sending' ? 'Đang xác minh yêu cầu…' : 'Xác nhận đặt hàng COD'}
    </button>
    <p className="tv3-checkout__notice">Không tự tạo khóa đặt hàng mới hoặc gửi lại khi kết quả chưa rõ.</p>
  </section>;
}
