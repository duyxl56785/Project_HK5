import type { CartProjection, ProjectedLine, StoreGroup } from './cartTypes.ts';

const MAX_MINOR = 9999999999999999999n; // PostgreSQL NUMERIC(19,2) representation bound.
const MAX_REVISION = '9223372036854775807';

function object(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}
function canonicalId(value: unknown): value is string {
  if (typeof value !== 'string' || value.length === 0 || value.length > 200 || value !== value.trim()) return false;
  const first = value.codePointAt(0);
  const last = Array.from(value).at(-1)?.codePointAt(0);
  return first !== undefined && last !== undefined
    && !/^\s$/u.test(String.fromCodePoint(first))
    && !/^\s$/u.test(String.fromCodePoint(last));
}
export function canonicalRevision(value: unknown): value is string {
  return typeof value === 'string' && /^(0|[1-9][0-9]*)$/.test(value)
    && (value.length < 19 || (value.length === 19 && value <= MAX_REVISION));
}

/** Parse exact NUMERIC(19,2)-compatible source text, including harmless zero padding. */
export function minorUnits(value: unknown): bigint | null {
  if (typeof value !== 'string') return null;
  const m = /^(0|[1-9][0-9]{0,16})(?:\.([0-9]{1,4}))?$/.exec(value);
  if (!m) return null;
  const fraction = m[2] ?? '';
  if (/[1-9]/.test(fraction.slice(2))) return null; // no silent rounding
  const minor = BigInt(m[1]) * 100n + BigInt((fraction + '00').slice(0, 2));
  return minor <= MAX_MINOR ? minor : null;
}

export function renderMinorUnits(minor: bigint): string {
  if (minor < 0n || minor > MAX_MINOR) throw new RangeError('Invalid monetary snapshot');
  const digits = minor.toString().padStart(3, '0');
  const integral = digits.slice(0, -2).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return integral + ',' + digits.slice(-2);
}

/** Fail closed on conflicting Cart identity, invalid money or overflow. No network calls. */
export function projectCart(source: unknown): CartProjection | null {
  if (!object(source) || !canonicalRevision(source.revision)
    || typeof source.claimed !== 'boolean' || !Array.isArray(source.items)) return null;
  const ids = new Set<string>();
  const sellerToStore = new Map<string, string>();
  const storeToSeller = new Map<string, string>();
  const groups = new Map<string, { sellerId: string; lines: ProjectedLine[]; amount: bigint }>();
  let total = 0n;
  for (const raw of source.items) {
    if (!object(raw) || !canonicalId(raw.productId) || !canonicalId(raw.storeId)
      || !canonicalId(raw.sellerId) || ids.has(raw.productId)
      || typeof raw.title !== 'string' || !raw.title.trim() || raw.title.length > 255
      || typeof raw.quantity !== 'number' || !Number.isInteger(raw.quantity)
      || raw.quantity < 1 || raw.quantity > 1000) return null;
    const unit = minorUnits(raw.unitPriceSnapshot);
    if (unit === null) return null;
    const previousStore = sellerToStore.get(raw.sellerId);
    const previousSeller = storeToSeller.get(raw.storeId);
    if ((previousStore !== undefined && previousStore !== raw.storeId)
      || (previousSeller !== undefined && previousSeller !== raw.sellerId)) return null;
    sellerToStore.set(raw.sellerId, raw.storeId);
    storeToSeller.set(raw.storeId, raw.sellerId);
    const quantity = raw.quantity;
    const lineAmount = unit * BigInt(quantity);
    if (lineAmount > MAX_MINOR || total + lineAmount > MAX_MINOR) return null;
    const existing = groups.get(raw.storeId) ?? { sellerId: raw.sellerId, lines: [], amount: 0n };
    if (existing.amount + lineAmount > MAX_MINOR) return null;
    existing.lines.push({
      productId: raw.productId, storeId: raw.storeId, sellerId: raw.sellerId,
      title: raw.title, quantity, unitPriceSnapshot: raw.unitPriceSnapshot,
      lineSubtotal: renderMinorUnits(lineAmount),
    });
    existing.amount += lineAmount;
    groups.set(raw.storeId, existing);
    total += lineAmount;
    ids.add(raw.productId);
  }
  const projected: StoreGroup[] = Array.from(groups, ([storeId, value]) => ({
    storeId, sellerId: value.sellerId, lines: value.lines, storeSubtotal: renderMinorUnits(value.amount),
  }));
  return {
    revision: source.revision, claimed: source.claimed, groups: projected,
    total: renderMinorUnits(total), itemCount: ids.size,
  };
}
