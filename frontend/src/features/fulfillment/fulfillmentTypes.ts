/**
 * UI-only projection for a SellerOrder. It is intentionally independent of a
 * future Order HTTP DTO and does not establish authorization or lifecycle
 * policy. The host must build it from already-authorized data.
 */
export type SellerOrderStatus =
  | 'PLACED'
  | 'ACCEPTED'
  | 'PREPARING'
  | 'READY_FOR_HANDOFF'
  | 'DISPATCHED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REJECTED';

export type CodPaymentStatus = 'UNRECORDED' | 'PAID';

export interface FulfillmentAction {
  /** Opaque host command; this component never maps it to a state change. */
  readonly id: string;
  readonly label: string;
  readonly tone?: 'primary' | 'secondary';
}

export interface SellerOrderSummary {
  readonly id: string;
  readonly parentOrderId: string;
  readonly storeId: string;
  readonly status: SellerOrderStatus;
  readonly codStatus: CodPaymentStatus;
  /** ISO-8601 instant supplied by the authorized host. */
  readonly createdAt: string;
  /** Only commands the authorized host allows for this order at render time. */
  readonly actions?: readonly FulfillmentAction[];
}

export interface FulfillmentBoardData {
  readonly storeId: string;
  readonly orders: readonly SellerOrderSummary[];
}

export type FulfillmentBoardState =
  | { readonly status: 'loading' }
  | { readonly status: 'empty' }
  | { readonly status: 'unauthorized' }
  | { readonly status: 'unavailable' }
  | { readonly status: 'error'; readonly onRetry: () => void }
  | ({ readonly status: 'ready'; readonly onAction?: (orderId: string, actionId: string) => void; readonly pendingOrderId?: string } & FulfillmentBoardData);
