import { useEffect, useMemo, useState } from 'react';
import { createOrderHistoryClient, OrderHistoryError } from './orderHistoryClient';
import type { OrderDetail, OrderHistoryClient, OrderPage, OrderTransport } from './orderHistoryClient';
import './OrderHistory.css';

export interface OrderHistoryProps {
  /** Trusted host marker changes on principal/session change. Correlation only; server authorizes. */
  sessionScope: string | null;
  transport: OrderTransport;
}
type Loaded<T> = { status: 'loading' } | { status: 'ready'; data: T }
  | { status: 'unauthorized' | 'not-found' | 'unavailable' };

function useRead<T>(load: (signal: AbortSignal) => Promise<T>, retry: number): Loaded<T> {
  const [state, setState] = useState<Loaded<T>>({ status: 'loading' });
  useEffect(() => {
    let active = true; const abort = new AbortController();
    setState({ status: 'loading' });
    Promise.resolve().then(() => load(abort.signal)).then(data => {
      if (active) setState({ status: 'ready', data });
    }, error => {
      if (active) setState({ status: error instanceof OrderHistoryError ? error.kind : 'unavailable' });
    });
    return () => { active = false; abort.abort(); };
  }, [load, retry]);
  return state;
}

function Notice({ status, retry }: { status: Exclude<Loaded<unknown>['status'], 'ready'>; retry(): void }) {
  const labels = { loading: 'Đang tải đơn hàng', unauthorized: 'Bạn cần đăng nhập bằng tài khoản Customer',
    'not-found': 'Không tìm thấy đơn hàng', unavailable: 'Không thể tải đơn hàng' };
  return <div role={status === 'loading' ? 'status' : 'alert'}>
    <p>{labels[status]}</p>{status === 'unavailable' && <button onClick={retry}>Thử lại</button>}
  </div>;
}

/** Feature-only integration surface: does not edit TV1 router, session, Gateway or shared client. */
export function OrderHistory({ sessionScope, transport }: OrderHistoryProps) {
  if (!sessionScope?.trim()) return <section className="tv3-history" aria-label="Lịch sử đơn hàng">
    <p role="alert">Bạn cần đăng nhập bằng tài khoản Customer</p>
  </section>;
  // Unmount private data synchronously on session changes, including A -> B -> A.
  return <HistorySession key={sessionScope} transport={transport} />;
}

function HistorySession({ transport }: { transport: OrderTransport }) {
  const client = useMemo(() => createOrderHistoryClient(transport), [transport]);
  const [view, setView] = useState<{ page: number; id?: string }>({ page: 0 });
  return <section className="tv3-history" aria-label="Lịch sử đơn hàng">
    <h1>Lịch sử đơn hàng</h1>
    {view.id ? <Detail key={view.id} client={client} id={view.id} back={() => setView({ page: view.page })} />
      : <HistoryList key={view.page} client={client} page={view.page}
          move={page => setView({ page })} select={id => setView({ page: view.page, id })} />}
  </section>;
}

function HistoryList({ client, page, move, select }: {
  client: OrderHistoryClient; page: number; move(page: number): void; select(id: string): void;
}) {
  const [retry, setRetry] = useState(0);
  const load = useMemo(() => (signal: AbortSignal) => client.list(page, 20, signal), [client, page]);
  const state = useRead<OrderPage>(load, retry);
  if (state.status !== 'ready') return <Notice status={state.status} retry={() => setRetry(retry + 1)} />;
  const data = state.data;
  return <>
    {data.content.length ? <table><caption>Đơn hàng của bạn</caption>
      <thead><tr><th>Mã đơn</th><th>Trạng thái</th><th>Tổng đã lưu</th><th>Ngày tạo</th><th>Số cửa hàng</th><th>Chi tiết</th></tr></thead>
      <tbody>{data.content.map(order => <tr key={order.orderId}>
        <td>{order.orderId}</td><td>{order.status}</td><td>{order.total}</td>
        <td><time dateTime={order.createdAt}>{order.createdAt}</time></td><td>{order.sellerOrderCount}</td>
        <td><button onClick={() => select(order.orderId)} aria-label={`Xem đơn ${order.orderId}`}>Xem đơn</button></td>
      </tr>)}</tbody></table> : <p>Chưa có đơn hàng ở trang này</p>}
    <nav aria-label="Phân trang đơn hàng">
      <button disabled={page === 0} onClick={() => move(page - 1)}>Trang trước</button>
      <span>Trang {page + 1} · {data.totalElements} đơn hàng</span>
      <button disabled={page + 1 >= data.totalPages} onClick={() => move(page + 1)}>Trang sau</button>
    </nav>
  </>;
}

function Detail({ client, id, back }: { client: OrderHistoryClient; id: string; back(): void }) {
  const [retry, setRetry] = useState(0);
  const load = useMemo(() => (signal: AbortSignal) => client.detail(id, signal), [client, id]);
  const state = useRead<OrderDetail>(load, retry);
  return <><button onClick={back}>Về danh sách</button>
    {state.status !== 'ready' ? <Notice status={state.status} retry={() => setRetry(retry + 1)} /> : <article>
      <h2>Đơn {state.data.orderId}</h2><p>{state.data.status} · Tổng đã lưu: {state.data.total}</p>
      <time dateTime={state.data.createdAt}>{state.data.createdAt}</time>
      {state.data.sellerOrders.map(store => <section key={store.sellerOrderId} aria-label={`Cửa hàng ${store.storeId}`}>
        <h3>Cửa hàng {store.storeId}</h3><p>Tổng mặt hàng đã lưu: {store.gross}</p>
        <ul>{store.items.map((item, index) => <li key={`${item.productId}-${index}`}>
          {item.productId} · Số lượng: {item.quantity} · Đơn giá: {item.unitPriceSnapshot} · Thành tiền: {item.lineTotal}
        </li>)}</ul>
      </section>)}
    </article>}
  </>;
}
