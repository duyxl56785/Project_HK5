export type SellerOrderStatus = 'PLACED' | 'ACCEPTED' | 'PREPARING' | 'READY_FOR_HANDOFF' | 'DISPATCHED' | 'DELIVERED' | 'CANCELLED' | 'REJECTED';
export type FulfillmentAction = 'ACCEPT' | 'START_PREPARING' | 'READY_FOR_HANDOFF' | 'DISPATCH' | 'DELIVER' | 'CANCEL' | 'REJECT';

export interface SellerOrderSummary {
  readonly id: string;
  readonly storeId: string;
  readonly status: SellerOrderStatus;
  readonly totalLabel: string;
  readonly codStatus?: 'UNPAID' | 'PAID' | 'NOT_APPLICABLE';
}

export interface FulfillmentState {
  readonly status: 'loading' | 'empty' | 'ready' | 'unauthorized' | 'error';
  readonly storeId?: string;
  readonly orders?: readonly SellerOrderSummary[];
  readonly pending?: { readonly orderId: string; readonly action: FulfillmentAction };
  readonly onAction?: (orderId: string, action: FulfillmentAction) => void;
  readonly onRetry?: () => void;
}
