import type { CartSnapshot } from '../cart/cartTypes.ts';

/** Isolated consumer request proposal, NOT an approved API wire/DTO. */
export interface CheckoutSubmission {
  readonly idempotencyKey: string;
  readonly expectedRevision: string;
  readonly expectedItems: readonly { readonly productId: string; readonly quantity: number }[];
  readonly vouchers?: readonly VoucherSelection[];
}
export interface VoucherCode { readonly storeId: string; readonly code: string }
export interface VoucherSelection extends VoucherCode { readonly quoteFingerprint: string }
export interface StorePrice {
  readonly storeId: string;
  readonly voucherCode: string | null;
  readonly quoteFingerprint: string | null;
  readonly merchandiseAmount: string;
  readonly discountAmount: string;
  readonly gross: string;
  readonly platformFee: string;
}
export interface VoucherQuote { readonly revision: number; readonly total: string; readonly stores: readonly StorePrice[] }
export interface RefusedItem { readonly productId: string; readonly reason: string }
export type CheckoutResponse =
  | { readonly status: 'confirmed'; readonly orderId: string }
  | { readonly status: 'unknown' }
  | { readonly status: 'rejected'; readonly reason: 'CART_CHANGED' | 'PRICE_CHANGED' | 'OUT_OF_STOCK' | 'UNAUTHORIZED' | 'STORE_UNAVAILABLE' | 'CHECKOUT_PAUSED' | 'ITEMS_REJECTED' | 'VOUCHER_REFUSED'; readonly items?: readonly RefusedItem[]; readonly voucherMessage?: string };

export type CheckoutUiState =
  | { readonly status: 'loading' | 'unauthorized' | 'unavailable' | 'empty' | 'unknown' }
  | { readonly status: 'ready'; readonly cart: CartSnapshot; readonly idempotencyKey: string };

export interface CheckoutCodViewProps {
  readonly state: CheckoutUiState;
  /** UI-isolation marker from a trusted future Auth host, not a Cart value or authorization grant. */
  readonly trustedSessionScope?: string;
  /** Display names by Store id; the id is shown when a name is unknown. */
  readonly storeNames?: Readonly<Record<string, string>>;
  /** Must be bound by TV1/TV3 to an approved authenticated backend adapter. */
  readonly onConfirm?: (request: CheckoutSubmission) => Promise<CheckoutResponse>;
  readonly onQuote?: (request: CheckoutSubmission, codes: readonly VoucherCode[]) => Promise<VoucherQuote>;
  readonly onRefreshAfterRejection?: () => void;
}
