import type { CartSnapshot } from '../cart/cartTypes.ts';

/** Isolated consumer request proposal, NOT an approved API wire/DTO. */
export interface CheckoutSubmission {
  readonly idempotencyKey: string;
  readonly expectedRevision: string;
  readonly expectedItems: readonly { readonly productId: string; readonly quantity: number }[];
}
export type CheckoutResponse =
  | { readonly status: 'confirmed'; readonly orderId: string }
  | { readonly status: 'unknown' }
  | { readonly status: 'rejected'; readonly reason: 'CART_CHANGED' | 'PRICE_CHANGED' | 'OUT_OF_STOCK' | 'UNAUTHORIZED' | 'STORE_UNAVAILABLE' };

export type CheckoutUiState =
  | { readonly status: 'loading' | 'unauthorized' | 'unavailable' | 'empty' | 'unknown' }
  | { readonly status: 'ready'; readonly cart: CartSnapshot; readonly idempotencyKey: string };

export interface CheckoutCodViewProps {
  readonly state: CheckoutUiState;
  /** UI-isolation marker from a trusted future Auth host, not a Cart value or authorization grant. */
  readonly trustedSessionScope?: string;
  /** Must be bound by TV1/TV3 to an approved authenticated backend adapter. */
  readonly onConfirm?: (request: CheckoutSubmission) => Promise<CheckoutResponse>;
}
