import type { CodPaymentStatus, FulfillmentAction, FulfillmentBoardData, SellerOrderStatus } from './fulfillmentTypes.ts';

const uuid = (value: unknown): value is string =>
  typeof value === 'string' && /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(value);

const record = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

const sellerOrderStatus = (value: unknown): value is SellerOrderStatus =>
  value === 'PLACED' || value === 'ACCEPTED' || value === 'PREPARING'
  || value === 'READY_FOR_HANDOFF' || value === 'DISPATCHED' || value === 'DELIVERED'
  || value === 'CANCELLED' || value === 'REJECTED';

const codStatus = (value: unknown): value is CodPaymentStatus =>
  value === 'UNRECORDED' || value === 'PAID';

function isIsoInstant(value: unknown): value is string {
  return typeof value === 'string' && value.length <= 40
    && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/.test(value)
    && !Number.isNaN(Date.parse(value));
}

function readActions(value: unknown): readonly FulfillmentAction[] | null {
  if (value === undefined) return [];
  if (!Array.isArray(value) || value.length > 5) return null;
  const ids = new Set<string>();
  const actions: FulfillmentAction[] = [];
  for (const candidate of value) {
    if (!record(candidate) || typeof candidate.id !== 'string' || !/^[A-Z][A-Z_]{0,63}$/.test(candidate.id)
      || typeof candidate.label !== 'string' || !candidate.label.trim() || candidate.label.length > 80
      || (candidate.tone !== undefined && candidate.tone !== 'primary' && candidate.tone !== 'secondary')
      || ids.has(candidate.id)) return null;
    ids.add(candidate.id);
    actions.push({ id: candidate.id, label: candidate.label, tone: candidate.tone });
  }
  return actions;
}

/**
 * Validates a complete Store-scoped display payload. A malformed or mixed-Store
 * collection is rejected as a whole, so the component cannot reveal another
 * Store's order beside an authorized one.
 */
export function readFulfillmentBoard(source: unknown): FulfillmentBoardData | null {
  if (!record(source) || !uuid(source.storeId) || !Array.isArray(source.orders)) return null;
  const seen = new Set<string>();
  const orders = [];
  for (const value of source.orders) {
    if (!record(value) || !uuid(value.id) || !uuid(value.parentOrderId) || value.storeId !== source.storeId
      || !sellerOrderStatus(value.status) || !codStatus(value.codStatus) || !isIsoInstant(value.createdAt)
      || seen.has(value.id)) return null;
    const actions = readActions(value.actions);
    if (actions === null) return null;
    seen.add(value.id);
    orders.push({ id: value.id, parentOrderId: value.parentOrderId, storeId: source.storeId,
      status: value.status, codStatus: value.codStatus, createdAt: value.createdAt, actions });
  }
  return { storeId: source.storeId, orders };
}
