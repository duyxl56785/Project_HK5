import type { InventoryState } from '../inventory';
import type { FulfillmentState } from '../fulfillment';

export type SellerOperationsView = 'inventory' | 'orders';

/**
 * Host-owned data boundary. TV4 deliberately does not create a second router,
 * authentication flow, or API client while TV1's shared client is pending.
 */
export interface SellerOperationsState {
  readonly inventory: InventoryState;
  readonly fulfillment: FulfillmentState;
}
