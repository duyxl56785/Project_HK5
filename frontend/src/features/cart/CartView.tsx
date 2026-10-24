import type { CartUiState } from './cartTypes.ts';
import { projectCart } from './cartModel.ts';
import './CartView.css';

export interface CartViewProps {
  readonly state: CartUiState;
  readonly onQuantityChange?: (productId: string, quantity: number, expectedRevision: string) => void;
  readonly onRemove?: (productId: string, expectedRevision: string) => void;
  readonly onRetry?: () => void;
}

const messages = {
  loading: 'Đang tải giỏ hàng',
  empty: 'Giỏ hàng hiện đang trống',
  unauthorized: 'Bạn cần đăng nhập để xem giỏ hàng',
  unavailable: 'Dịch vụ giỏ hàng chưa sẵn sàng',
  error: 'Không thể tải giỏ hàng',
} as const;

/** Presentation only. Parent owns trusted Cart API, actor identity and revision control. */
export function CartView({ state, onQuantityChange, onRemove, onRetry }: CartViewProps) {
  if (state.status !== 'ready') {
    return <section className="tv3-cart" aria-busy={state.status === 'loading'}>
      <h2>Giỏ hàng</h2>
      <p role={state.status === 'error' || state.status === 'unavailable' ? 'alert' : undefined}>
        {messages[state.status]}
      </p>
      {state.status === 'error' && onRetry && <button type="button" onClick={onRetry}>Thử lại</button>}
    </section>;
  }

  const cart = projectCart(state.snapshot);
  if (!cart) return <section className="tv3-cart" role="alert">
    <h2>Giỏ hàng</h2><p>Dữ liệu giỏ hàng không nhất quán. Không thể tiếp tục.</p>
  </section>;
  if (cart.itemCount === 0) return <section className="tv3-cart">
    <h2>Giỏ hàng</h2><p>Giỏ hàng hiện đang trống</p>
  </section>;

  return <section className="tv3-cart" aria-label="Giỏ hàng theo cửa hàng">
    <header className="tv3-cart__header">
      <div><span className="tv3-cart__eyebrow">TV3 · GIỎ HÀNG</span><h2>Giỏ hàng của bạn</h2></div>
      <p>{cart.itemCount} sản phẩm · phiên bản {cart.revision}</p>
    </header>
    {cart.claimed && <p className="tv3-cart__warning" role="status">
      Giỏ hàng đang được giữ cho Checkout. Không thể chỉnh sửa cho đến khi được xác nhận.
    </p>}
    <div className="tv3-cart__stores">
      {cart.groups.map((store) => <article className="tv3-cart__store" key={store.storeId}>
        <header><h3>Cửa hàng: {store.storeId}</h3><span>{store.lines.length} sản phẩm</span></header>
        <ul>{store.lines.map((line) => <li className="tv3-cart__line" key={line.productId}>
          <div>
            <strong>{line.title}</strong>
            <small>Mã sản phẩm: {line.productId}</small>
            <small>Đơn giá: {line.unitPriceSnapshot}</small>
          </div>
          <div className="tv3-cart__actions">
            <label htmlFor={'qty-' + line.productId}>Số lượng</label>
            <input id={'qty-' + line.productId} type="number" min={1} max={1000} step={1}
              value={line.quantity} disabled={cart.claimed || !onQuantityChange}
              onChange={(event) => {
                const number = Number(event.currentTarget.value);
                if (Number.isInteger(number) && number >= 1 && number <= 1000 && !cart.claimed)
                  onQuantityChange?.(line.productId, number, cart.revision);
              }} />
            <button type="button" disabled={cart.claimed || !onRemove}
              onClick={() => onRemove?.(line.productId, cart.revision)}>Xóa</button>
          </div>
          <strong className="tv3-cart__price">{line.lineSubtotal}</strong>
        </li>)}</ul>
        <footer>Tạm tính cửa hàng <strong>{store.storeSubtotal}</strong></footer>
      </article>)}
    </div>
    <footer className="tv3-cart__total">
      <div><strong>Tổng giá trị sản phẩm</strong><strong>{cart.total}</strong></div>
      <small>Giá trị hiển thị từ snapshot. Đơn vị tiền tệ, phí và giao hàng cần được backend xác nhận theo M01.</small>
    </footer>
  </section>;
}
