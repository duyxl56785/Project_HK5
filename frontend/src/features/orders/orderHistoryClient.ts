export interface OrderSummary {
  orderId: string; status: string; total: string; createdAt: string; sellerOrderCount: number;
}
export interface OrderPage {
  content: OrderSummary[]; page: number; size: number; totalElements: number; totalPages: number;
}
export interface OrderItem { productId: string; quantity: number; unitPriceSnapshot: string; lineTotal: string }
export interface StoreOrder { sellerOrderId: string; storeId: string; gross: string; items: OrderItem[] }
export interface OrderDetail { orderId: string; status: string; total: string; createdAt: string; sellerOrders: StoreOrder[] }
/** TV1 host supplies its authenticated same-origin transport. No token storage, login or refresh here. */
export type OrderTransport = (path: string, init: RequestInit) => Promise<Response>;
export type HistoryErrorKind = 'unauthorized' | 'not-found' | 'unavailable';
export class OrderHistoryError extends Error {
  readonly kind: HistoryErrorKind;
  constructor(kind: HistoryErrorKind) { super(kind); this.kind = kind; }
}
type RecordValue = Record<string, unknown>;
function object(value: unknown): RecordValue {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new OrderHistoryError('unavailable');
  return value as RecordValue;
}
function text(value: unknown): string {
  if (typeof value !== 'string' || !value.trim()) throw new OrderHistoryError('unavailable');
  return value;
}
function uuid(value: unknown): string {
  const id = text(value);
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id)) throw new OrderHistoryError('unavailable');
  return id;
}
function money(value: unknown): string {
  const amount = text(value);
  if (!/^(0|[1-9][0-9]{0,16})\.[0-9]{2}$/.test(amount)) throw new OrderHistoryError('unavailable');
  return amount;
}
function integer(value: unknown, min = 0): number {
  if (!Number.isSafeInteger(value) || (value as number) < min) throw new OrderHistoryError('unavailable');
  return value as number;
}
function array<T>(value: unknown, map: (item: unknown) => T): T[] {
  if (!Array.isArray(value)) throw new OrderHistoryError('unavailable');
  return value.map(map);
}
function root(value: unknown) {
  const row = object(value); const createdAt = text(row.createdAt); const status = text(row.status);
  if (!/Z$/.test(createdAt) || !Number.isFinite(Date.parse(createdAt)) || !['PLACED', 'CANCELLED'].includes(status))
    throw new OrderHistoryError('unavailable');
  return { orderId: uuid(row.orderId), status, total: money(row.total), createdAt };
}
function distinct(ids: string[]) {
  if (new Set(ids).size !== ids.length) throw new OrderHistoryError('unavailable');
}
export function createOrderHistoryClient(transport: OrderTransport) {
  async function read(path: string, signal?: AbortSignal, detail = false): Promise<unknown> {
    const response = await transport(path, { method: 'GET', cache: 'no-store', redirect: 'error', signal,
      headers: { Accept: 'application/json' } });
    if (response.status === 401 || response.status === 403) throw new OrderHistoryError('unauthorized');
    if (response.status === 404) throw new OrderHistoryError(detail ? 'not-found' : 'unavailable');
    if (response.status !== 200) throw new OrderHistoryError('unavailable');
    try { return await response.json(); } catch { throw new OrderHistoryError('unavailable'); }
  }
  return {
    async list(page = 0, size = 20, signal?: AbortSignal): Promise<OrderPage> {
      integer(page); integer(size, 1);
      if (size > 100) throw new OrderHistoryError('unavailable');
      const data = object(await read(`/api/orders?page=${page}&size=${size}`, signal));
      const content = array(data.content, value => {
        const row = object(value); return { ...root(row), sellerOrderCount: integer(row.sellerOrderCount) };
      });
      const totalElements = integer(data.totalElements); const totalPages = integer(data.totalPages);
      if (data.page !== page || data.size !== size || content.length > size || content.length > totalElements
          || totalPages !== Math.ceil(totalElements / size)) throw new OrderHistoryError('unavailable');
      distinct(content.map(row => row.orderId));
      return { content, page, size, totalElements, totalPages };
    },
    async detail(id: string, signal?: AbortSignal): Promise<OrderDetail> {
      uuid(id);
      const data = object(await read(`/api/orders/${id}`, signal, true)); const order = root(data);
      if (order.orderId !== id) throw new OrderHistoryError('unavailable');
      const sellerOrders = array(data.sellerOrders, value => {
        const store = object(value);
        return { sellerOrderId: uuid(store.sellerOrderId), storeId: text(store.storeId), gross: money(store.gross),
          items: array(store.items, item => {
            const row = object(item);
            return { productId: text(row.productId), quantity: integer(row.quantity, 1),
              unitPriceSnapshot: money(row.unitPriceSnapshot), lineTotal: money(row.lineTotal) };
          }) };
      });
      distinct(sellerOrders.map(row => row.sellerOrderId)); distinct(sellerOrders.map(row => row.storeId));
      return { ...order, sellerOrders };
    },
  };
}
export type OrderHistoryClient = ReturnType<typeof createOrderHistoryClient>;
