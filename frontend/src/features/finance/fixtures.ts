import type { Transaction, WorkspaceAdapter } from './workspaceTypes';
import { readPrAudit, scopeTransactions } from './workspaceModel';
import type { QaRun } from './workspaceTypes';

// Explicit synthetic, READ-ONLY view fixtures. These are not persisted business records.
export const demoTransactions: readonly Transaction[] = Array.from({ length: 12 }, (_, i): Transaction => {
  const a = i % 2 === 0;
  const day = String(22 + i % 6).padStart(2, '0');
  return {
    id: `DEMO-SO-${String(i + 1).padStart(3, '0')}`, orderId: `DEMO-ORD-${Math.floor(i / 2) + 1}`,
    storeId: a ? 'store-a' : 'store-b', store: a ? 'Mộc Studio' : 'Sách & Chuyện', seller: a ? 'Demo Seller A' : 'Demo Seller B',
    amount: ['1250000', '890000', '375000', '2400000', '569000', '180000'][i % 6], currency: 'VND',
    delivery: i % 4 === 0 ? 'DISPATCHED' : i % 4 === 3 ? 'CANCELLED' : 'DELIVERED',
    cod: i % 5 === 0 ? 'PENDING' : i % 5 === 1 ? 'VOID' : i % 5 === 4 ? null : 'COLLECTED',
    verification: i % 4 === 1 ? 'VERIFIED' : i % 4 === 3 ? 'UNKNOWN' : 'UNVERIFIED',
    paymentEvidence: i % 4 === 1 ? `DEMO-EVIDENCE-${i}` : null,
    commission: null, sellerNet: null, // No fee/House/rounding rule silently chosen.
    settlement: i % 3 === 0 ? { id: `DEMO-ST-${i + 1}`, state: 'BLOCKED', createdAt: `2026-10-${day}T09:00:00Z`, settledAt: null, reason: 'DEMO · Chờ policy, snapshot và authority được phê duyệt.' } : null,
    createdAt: `2026-10-${day}T08:00:00Z`, updatedAt: `2026-10-${day}T10:30:00Z`,
  };
});
export function createPreviewAdapter(mode = 'ready', delay = 180): WorkspaceAdapter {
  return {
    id: `explicit-preview-${mode}`,
    async read(scope, signal) {
      await new Promise<void>(resolve => { const timeout = setTimeout(resolve, delay); signal.addEventListener('abort', () => { clearTimeout(timeout); resolve(); }, { once: true }); });
      if (signal.aborted) return { status: 'loading' };
      if (scope.kind === 'denied' || mode === 'denied') return { status: 'denied' };
      if (mode === 'error') return { status: 'error' };
      if (mode === 'blocked') return { status: 'blocked' };
      if (mode === 'empty') return { status: 'empty' };
      if (mode === 'loading') return { status: 'loading' };
      const recorded = scope.kind === 'admin' ? await Promise.all([import('./recorded/qa-runs.json'), import('./recorded/pr-audit.json')]) : null;
      return { status: 'ready', data: {
        source: 'DEMO', asOf: '2026-10-28T00:00:00Z', transactions: scopeTransactions(demoTransactions, scope),
        qaRuns: recorded ? recorded[0].default as readonly QaRun[] : [],
        audit: recorded ? readPrAudit(recorded[1].default) : { status: 'blocked', rows: [], asOf: null },
      } };
    },
  };
}
