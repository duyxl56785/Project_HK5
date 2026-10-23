/** Presentation-only TV3 types. Not an approved wire DTO or source of authority. */
export interface CartLine {
  readonly productId: string;
  readonly storeId: string;
  readonly sellerId: string;
  readonly title: string;
  readonly quantity: number;
  /** Exact decimal string; never a JavaScript number. */
  readonly unitPriceSnapshot: string;
}

export interface CartSnapshot {
  /** String to preserve version precision; adapter mapping requires TV1 contract review. */
  readonly revision: string;
  readonly claimed: boolean;
  readonly items: readonly CartLine[];
}

export type CartUiState =
  | { readonly status: 'loading' | 'empty' | 'unauthorized' | 'unavailable' | 'error' }
  | { readonly status: 'ready'; readonly snapshot: CartSnapshot };

export interface ProjectedLine extends CartLine {
  readonly lineSubtotal: string;
}
export interface StoreGroup {
  readonly storeId: string;
  readonly sellerId: string;
  readonly lines: readonly ProjectedLine[];
  readonly storeSubtotal: string;
}
export interface CartProjection {
  readonly revision: string;
  readonly claimed: boolean;
  readonly groups: readonly StoreGroup[];
  readonly total: string;
  readonly itemCount: number;
}
