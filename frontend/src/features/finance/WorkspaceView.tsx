import { useId, useState } from 'react';
import type { LoadState, Scope, View, WorkspaceSnapshot } from './workspaceTypes';
import { scopeTransactions } from './workspaceModel';
import { StatePanel, StatusBadge } from './components';
import { TransactionViews } from './TransactionViews';
import { PRDashboard, QADashboard } from './EvidenceViews';
import './workspace.css';

const views: readonly { id: View; label: string; title: string; icon: string; note: string }[] = [
  { id: 'finance', label: 'Tổng quan', title: 'Tổng quan tài chính', icon: '▦', note: 'Quan sát snapshot, theo dõi đối soát và bằng chứng.' },
  { id: 'cod', label: 'Theo dõi COD', title: 'COD Management', icon: '▤', note: 'Tách trạng thái giao hàng, thanh toán và xác minh evidence.' },
  { id: 'settlement', label: 'Settlement', title: 'Settlement Management', icon: '⇄', note: 'Đọc bản ghi mẫu. Không thực hiện quyết toán hay tính phí.' },
  { id: 'qa', label: 'Kiểm thử QA', title: 'QA Dashboard', icon: '✓', note: 'Từ kết quả kiểm thử đến evidence, trên đúng commit.' },
  { id: 'prs', label: 'Pull Requests', title: 'Pull Requests & Reviewers', icon: '⑂', note: 'Theo dõi quyết định review và các điều kiện còn chờ.' },
];
export interface WorkspaceViewProps {
  state: LoadState<WorkspaceSnapshot>; scope: Scope; initialView?: View; onRetry?: () => void;
}
export function WorkspaceView({ state, scope, initialView = 'finance', onRetry }: WorkspaceViewProps) {
  const [view, setView] = useState<View>(initialView), id = useId();
  const visible = views.filter(v => scope.kind === 'admin' || !['qa', 'prs'].includes(v.id));
  const active = visible.find(v => v.id === view) ?? visible[0];
  const effective = scope.kind === 'denied' || scope.kind === 'store' && !scope.storeId ? { status: 'denied' as const } : state;
  const rows = effective.status === 'ready' ? scopeTransactions(effective.data.transactions, scope) : [];
  return <div className="tv5-finance">
    <a className="tv5-skip" href={`#${id}`}>Đến nội dung chính</a>
    <aside className="tv5-sidebar"><div className="tv5-brand"><span className="tv5-brand-mark" aria-hidden="true">S</span><div>Smart Commerce<small>FINANCE & QUALITY</small></div></div>
      <div className="tv5-sidebar-label">KHÔNG GIAN TV5</div>
      <nav aria-label="Điều hướng TV5">{visible.map(v => <button type="button" key={v.id} aria-current={active.id === v.id ? 'page' : undefined} onClick={() => setView(v.id)}><span aria-hidden="true">{v.icon}</span>{v.label}{active.id === v.id && <i aria-hidden="true" />}</button>)}</nav>
      <div className="tv5-sidebar-bottom"><span className="tv5-sidebar-label">TRẠNG THÁI TÍCH HỢP</span><p><span className="tv5-dot" /> Chỉ đọc · API chưa kết nối</p><small>Finance policy đang chờ phê duyệt. Không có thao tác thanh toán.</small></div>
      <div className="tv5-profile"><span aria-hidden="true">ND</span><div>Nguyễn Minh Duy<small>TV5 · Finance / QA</small></div></div>
    </aside>
    <div className="tv5-main"><header className="tv5-topbar"><span>Workspace <span aria-hidden="true">/</span> <strong>{active.label}</strong></span><div><span className="tv5-readonly">◉ Read-only</span><span className="tv5-avatar" aria-hidden="true">ND</span></div></header>
      <main id={id} tabIndex={-1} className="tv5-content"><div className="tv5-page-heading"><div><span className="tv5-eyebrow">SMART E-COMMERCE · TV5</span><h1>{active.title}</h1><p>{active.note}</p></div><StatusBadge value="BLOCKED" label="POLICY GATED" /></div>
        {effective.status !== 'ready' ? <StatePanel status={effective.status} onRetry={onRetry} />
          : active.id === 'qa' && scope.kind === 'admin' ? <QADashboard runs={effective.data.qaRuns} />
            : active.id === 'prs' && scope.kind === 'admin' ? <PRDashboard audit={effective.data.audit} />
              : <TransactionViews key={active.id} view={active.id === 'cod' || active.id === 'settlement' ? active.id : 'finance'} rows={rows} />}
        <footer className="tv5-footer"><span>Project_HK5 · Finance / QA</span><span>Phân quyền UI không thay xác minh phía server.</span></footer>
      </main>
    </div>
  </div>;
}
