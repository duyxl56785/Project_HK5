export type SellerStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';

/** Display projection of SellerStoreResponses.SellerPrivate; deliberately omits reviewReason. */
export interface SellerSummary {
  readonly id: string;
  readonly version: string;
  readonly status: SellerStatus;
}

/** Display projection of StoreOwner; no new wire fields or public ownership API. */
export interface StoreSummary {
  readonly id: string;
  readonly sellerId: string;
  readonly name: string;
  readonly slug: string;
  readonly open: boolean;
  readonly version: string;
}

export interface OverviewData {
  readonly seller: SellerSummary;
  readonly store: StoreSummary | null;
}

/** Ready is a rendering state, never proof of authorization; the server/container must authorize first. */
export type SellerStoreState =
  | { readonly status: 'loading' }
  | { readonly status: 'error'; readonly onRetry: () => void }
  | { readonly status: 'empty' }
  | { readonly status: 'unauthorized' }
  | { readonly status: 'unavailable' }
  | ({ readonly status: 'ready' } & OverviewData);
