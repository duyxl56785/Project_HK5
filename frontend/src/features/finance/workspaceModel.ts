import type { AuditView, PrRow, QaCase, Result, Scope, Transaction, TransactionFilter } from './workspaceTypes';

const money = /^-?(?:0|[1-9]\d{0,29})(?:\.\d{1,6})?$/;
function isMoney(value: unknown): value is string { return typeof value === 'string' && money.test(value); }
export function formatVnd(value: unknown, currency: unknown): string {
  if (currency !== 'VND' || !isMoney(value)) return 'Chưa có snapshot';
  const [whole, fraction] = value.split('.');
  return whole.replace(/\B(?=(\d{3})+(?!\d))/g, '.') + (fraction ? ',' + fraction : '') + ' ₫';
}
export function sumAmounts(values: readonly (string | null)[]): string | null {
  if (!values.every(isMoney)) return null;
  const scale = Math.max(0, ...values.map(v => v!.split('.')[1]?.length ?? 0));
  const total = values.reduce((sum, value) => {
    const v = value!; const sign = v.startsWith('-') ? -1n : 1n;
    const [whole, fraction = ''] = v.replace(/^-/, '').split('.');
    return sum + sign * BigInt(whole + fraction.padEnd(scale, '0'));
  }, 0n);
  const abs = (total < 0n ? -total : total).toString().padStart(scale + 1, '0');
  return (total < 0n ? '-' : '') + (scale ? abs.slice(0, -scale) + '.' + abs.slice(-scale) : abs);
}
export function scopeTransactions(rows: readonly Transaction[], scope: Scope): readonly Transaction[] {
  if (scope.kind === 'denied' || (scope.kind === 'store' && !scope.storeId)) return [];
  return rows.filter(r => scope.kind === 'admin' || r.storeId === scope.storeId);
}
export function financeSummary(rows: readonly Transaction[]) {
  const amounts = (predicate: (r: Transaction) => boolean) => sumAmounts(rows.filter(predicate).map(r => r.currency === 'VND' ? r.amount : null));
  const verified = (r: Transaction) => r.cod === 'PAID' && r.verification === 'VERIFIED' && !!r.paymentEvidence;
  return { total: amounts(() => true), count: rows.length, verifiedCod: amounts(verified),
    unverifiedCod: amounts(r => r.cod === 'PAID' && !verified(r)), settlements: rows.filter(r => r.settlement?.state === 'RECORDED').length,
    errors: rows.filter(r => r.settlement?.state === 'BLOCKED').length };
}
function dateBound(value: string | undefined): number | null {
  if (!value) return null;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return NaN;
  const timestamp = Date.parse(value + 'T00:00:00Z');
  return Number.isFinite(timestamp) && new Date(timestamp).toISOString().startsWith(value) ? timestamp : NaN;
}
export function filterTransactions(rows: readonly Transaction[], f: TransactionFilter): readonly Transaction[] {
  const start = dateBound(f.from), end = dateBound(f.to);
  if (Number.isNaN(start) || Number.isNaN(end) || (start !== null && end !== null && start > end)) return [];
  const search = (f.search ?? '').toLocaleLowerCase('vi');
  return rows.filter(r => (!search || [r.id, r.orderId, r.store, r.seller].some(s => s.toLocaleLowerCase('vi').includes(search)))
    && (!f.storeId || r.storeId === f.storeId) && (!f.status || r.cod === f.status || r.delivery === f.status || r.settlement?.state === f.status || r.verification === f.status)
    && (start === null || Date.parse(r.createdAt) >= start) && (end === null || Date.parse(r.createdAt) < end + 86400000));
}
export function paginate<T>(rows: readonly T[], requested: number, size = 5) {
  const limit = Number.isInteger(size) && size > 0 ? Math.min(size, 100) : 5;
  const pages = Math.max(1, Math.ceil(rows.length / limit));
  const page = Math.min(pages, Math.max(1, Number.isFinite(requested) ? Math.trunc(requested) : 1));
  return { rows: rows.slice((page - 1) * limit, page * limit), page, pages, total: rows.length };
}
export function safeEvidenceUrl(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  try {
    const u = new URL(value);
    return u.protocol === 'https:' && u.hostname === 'github.com' && !u.username && !u.password && !u.search
      && /^\/duyxl56785\/Project_HK5\/(?:blob|pull|issues|commit|actions)\//.test(u.pathname) ? u.href : null;
  } catch { return null; }
}
export function qaResult(row: Pick<QaCase, 'result' | 'evidence'>): Result {
  return row.result === 'PASS' && !safeEvidenceUrl(row.evidence) ? 'NOT_RUN' : row.result;
}
export function qaCounts(rows: readonly QaCase[]) {
  const count = { PASS: 0, FAIL: 0, SKIP: 0, NOT_RUN: 0, BLOCKED: 0, executed: 0 };
  for (const row of rows) count[qaResult(row)]++;
  count.executed = count.PASS + count.FAIL;
  return count;
}
function record(v: unknown): v is Record<string, unknown> { return typeof v === 'object' && v !== null && !Array.isArray(v); }
function strings(v: unknown): v is string[] { return Array.isArray(v) && v.every(x => typeof x === 'string'); }
export function readPrAudit(input: unknown): AuditView {
  const blocked: AuditView = { status: 'blocked', rows: [], asOf: null };
  if (!record(input) || input.complete !== true || !Array.isArray(input.errors) || input.errors.length || !Array.isArray(input.PRs)
    || typeof input.generatedAtUtc !== 'string' || !Number.isFinite(Date.parse(input.generatedAtUtc))) return blocked;
  const rows: PrRow[] = [];
  for (const raw of input.PRs) {
    if (!record(raw) || !Number.isInteger(raw.number) || typeof raw.number !== 'number' || raw.number < 1 || typeof raw.title !== 'string'
      || typeof raw.author !== 'string' || typeof raw.head !== 'string' || !/^[a-f0-9]{7,40}$/.test(raw.head) || typeof raw.state !== 'string'
      || typeof raw.draft !== 'boolean' || !strings(raw.requestedReviewers) || !Array.isArray(raw.effectiveReviews)
      || !Array.isArray(raw.unresolvedThreads) || typeof raw.ci !== 'string' || ![true, false, null].includes(raw.mergeable as boolean | null)
      || !safeEvidenceUrl(raw.url)) return blocked;
    const reviews: { author: string; state: string; head: string | null }[] = [];
    for (const r of raw.effectiveReviews) {
      if (!record(r) || typeof r.author !== 'string' || typeof r.state !== 'string' || !(r.head === null || typeof r.head === 'string')) return blocked;
      reviews.push({ author: r.author, state: r.state, head: r.head });
    }
    const needs = reviews.some(r => r.state === 'CHANGES_REQUESTED');
    rows.push({ number: raw.number, title: raw.title, author: raw.author, head: raw.head, state: raw.state, draft: raw.draft,
      url: safeEvidenceUrl(raw.url)!, requested: [...raw.requestedReviewers], reviews, ci: raw.ci, mergeable: raw.mergeable as boolean | null,
      unresolved: raw.unresolvedThreads.length, nextAction: needs ? 'Reviewer xác minh bản sửa và cập nhật quyết định' : raw.draft ? 'Owner xác nhận phạm vi sẵn sàng review' : 'Owner kiểm tra gate trước khi tích hợp' });
  }
  return { status: 'ready', rows, asOf: input.generatedAtUtc };
}
