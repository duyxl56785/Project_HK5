export type InventoryStatus = 'loading' | 'empty' | 'ready' | 'error';

/** Values are strings because the backend stores signed 64-bit quantities. */
export interface InventoryItem {
  readonly sku: string;
  readonly name?: string;
  readonly availableQuantity: string;
  readonly reservedQuantity: string;
}

export interface InventoryState {
  readonly status: InventoryStatus;
  readonly items?: readonly InventoryItem[];
  readonly onRetry?: () => void;
}
