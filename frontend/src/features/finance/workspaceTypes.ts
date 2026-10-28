export type View = 'finance' | 'cod' | 'settlement' | 'qa' | 'prs';
// Local view projections, NOT a shared backend Finance/COD/Settlement contract.
export type Scope = { kind: 'admin' } | { kind: 'store'; storeId: string } | { kind: 'denied' };
export type Result = 'PASS' | 'FAIL' | 'SKIP' | 'NOT_RUN' | 'BLOCKED';
export type Delivery = 'PLACED' | 'ACCEPTED' | 'PREPARING' | 'READY_FOR_HANDOFF' | 'DISPATCHED' | 'DELIVERED' | 'CANCELLED' | 'REJECTED';
export interface SettlementRecord {
  id: string; state: 'RECORDED' | 'BLOCKED'; // presentation labels, no backend enum asserted
  createdAt: string; settledAt: string | null; reason: string | null;
}
export interface Transaction {
  id: string; orderId: string; storeId: string; store: string; seller: string;
  amount: string; currency: string; delivery: Delivery; cod: 'PAID' | 'UNPAID' | null;
  verification: 'VERIFIED' | 'UNVERIFIED' | 'UNKNOWN'; paymentEvidence: string | null;
  commission: string | null; sellerNet: string | null; settlement: SettlementRecord | null;
  createdAt: string; updatedAt: string;
}
export interface QaCase {
  id: string; name: string; suite: string; module: string; type: string;
  result: Result; evidence: string | null; error?: string;
}
export interface QaRun {
  id: string; title: string; head: string; branch: string; environment: string;
  executedAt: string; cases: readonly QaCase[]; evidence: string;
}
export interface PrRow {
  number: number; title: string; author: string; head: string; state: string; draft: boolean;
  url: string; requested: readonly string[];
  reviews: readonly { author: string; state: string; head: string | null }[];
  ci: string; mergeable: boolean | null; unresolved: number; nextAction: string;
}
export type AuditView = { status: 'ready'; rows: readonly PrRow[]; asOf: string } | { status: 'blocked'; rows: readonly PrRow[]; asOf: null };
export interface WorkspaceSnapshot {
  transactions: readonly Transaction[]; qaRuns: readonly QaRun[]; audit: AuditView;
  source: 'DEMO'; asOf: string;
}
export type LoadState<T> = { status: 'loading' | 'empty' | 'denied' | 'error' | 'blocked' } | { status: 'ready'; data: T };
export interface WorkspaceAdapter {
  // Future server adapters MUST authenticate/authorize independently; UI filtering is not server authorization.
  readonly id: string;
  read(scope: Scope, signal: AbortSignal): Promise<LoadState<WorkspaceSnapshot>>;
}
export interface TransactionFilter { search?: string; storeId?: string; status?: string; from?: string; to?: string }
