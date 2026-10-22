import type { OverviewData, SellerStatus, SellerSummary } from './sellerStoreTypes.ts';

export function isCanonicalVersion(value: unknown): value is string {
  return typeof value === 'string' && value.length <= 19 && /^(0|[1-9][0-9]*)$/.test(value)
    && (value.length < 19 || value <= '9223372036854775807');
}

function record(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}
function uuid(value: unknown): value is string {
  return typeof value === 'string' && /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(value);
}
function sellerStatus(value: unknown): value is SellerStatus {
  return value === 'PENDING_REVIEW' || value === 'APPROVED' || value === 'REJECTED' || value === 'SUSPENDED';
}

/** Consistency/privacy projection only; it cannot authenticate or authorize the caller. */
export function readOverview(sellerSource: unknown, storeSource: unknown): OverviewData | null {
  if (!record(sellerSource) || !uuid(sellerSource.id) || !isCanonicalVersion(sellerSource.version)
    || !sellerStatus(sellerSource.status)) return null;
  const seller: SellerSummary = { id: sellerSource.id, version: sellerSource.version, status: sellerSource.status };
  if (storeSource === null) return { seller, store: null };
  if (!record(storeSource) || !uuid(storeSource.id) || storeSource.sellerId !== seller.id
    || !isCanonicalVersion(storeSource.version) || typeof storeSource.open !== 'boolean'
    || typeof storeSource.name !== 'string' || !storeSource.name.trim() || storeSource.name.length > 255
    || typeof storeSource.slug !== 'string' || storeSource.slug.length > 128
    || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(storeSource.slug)) return null;
  return { seller, store: { id: storeSource.id, sellerId: seller.id, name: storeSource.name,
    slug: storeSource.slug, open: storeSource.open, version: storeSource.version } };
}
