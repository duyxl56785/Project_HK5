import type { FulfillmentAction, SellerOrderStatus, SellerOrderSummary } from './sellerOrderTypes';

const allowed: Record<SellerOrderStatus, readonly FulfillmentAction[]> = {
  PLACED: ['ACCEPT', 'CANCEL', 'REJECT'],
  ACCEPTED: ['START_PREPARING'],
  PREPARING: ['READY_FOR_HANDOFF'],
  READY_FOR_HANDOFF: ['DISPATCH'],
  DISPATCHED: ['DELIVER'],
  DELIVERED: [], CANCELLED: [], REJECTED: [],
};

export const actionLabels: Record<FulfillmentAction, string> = {
  ACCEPT: 'Xác nhận', START_PREPARING: 'Bắt đầu chuẩn bị', READY_FOR_HANDOFF: 'Sẵn sàng bàn giao',
  DISPATCH: 'Bàn giao', DELIVER: 'Xác nhận giao', CANCEL: 'Hủy đơn', REJECT: 'Từ chối',
};

export function actionsFor(status: SellerOrderStatus): readonly FulfillmentAction[] { return allowed[status]; }

/** Cross-store orders are discarded at the UI boundary; the API remains authoritative. */
export function readStoreOrders(storeId: string | undefined, orders: readonly SellerOrderSummary[] | undefined): readonly SellerOrderSummary[] | null {
  if (!storeId || !orders) return null;
  if (orders.some((order) => !order || !order.id || order.storeId !== storeId || !order.totalLabel)) return null;
  return orders;
}
