import { useEffect, useId, useRef } from 'react';
import type { ReactNode } from 'react';
import { safeEvidenceUrl } from './workspaceModel';

export function StatusBadge({ value, label }: { value: string; label?: string }) {
  const tone = ['PASS', 'PAID', 'VERIFIED', 'APPROVED', 'SUCCESS', 'DELIVERED', 'RECORDED'].includes(value) ? 'good'
    : ['FAIL', 'FAILED', 'CHANGES_REQUESTED', 'error'].includes(value) ? 'bad'
    : ['BLOCKED', 'UNVERIFIED', 'REVIEW_REQUIRED', 'PENDING', 'UNPAID'].includes(value) ? 'warn' : 'neutral';
  return <span className={`tv5-badge tv5-badge--${tone}`}><span aria-hidden="true" className="tv5-dot" />{label ?? value}</span>;
}
export function DashboardStatCard({ label, value, note, accent = false }: { label: string; value: ReactNode; note: string; accent?: boolean }) {
  return <article className={`tv5-stat${accent ? ' tv5-stat--accent' : ''}`}><h3>{label}</h3><strong>{value}</strong><small>{note}</small></article>;
}
export function StatePanel({ status, onRetry }: { status: string; onRetry?: () => void }) {
  const labels: Record<string, [string, string]> = {
    loading: ['Đang tải dữ liệu', 'Đang chuẩn bị snapshot trong phạm vi được phép.'],
    empty: ['Chưa có dữ liệu', 'Không có bản ghi phù hợp. Thử thay đổi bộ lọc hoặc nguồn dữ liệu.'],
    error: ['Không thể tải dữ liệu', 'Chưa thể đọc nguồn dữ liệu. Vui lòng thử lại.'],
    denied: ['Không có quyền truy cập', 'Nguồn dữ liệu phải xác minh tài khoản và phạm vi Store trước khi hiển thị.'],
    blocked: ['Chưa kết nối API', 'BLOCKED_API_INTEGRATION · Cần contract đọc dữ liệu và authority đã được owner phê duyệt.'],
  };
  const [title, note] = labels[status] ?? labels.blocked;
  return <section className={`tv5-state tv5-state--${status}`} aria-busy={status === 'loading'} role={status === 'error' ? 'alert' : 'status'}>
    <div className="tv5-state-icon" aria-hidden="true">{status === 'loading' ? '◌' : status === 'empty' ? '▤' : '◇'}</div>
    <h2>{title}</h2><p>{note}</p>{status === 'loading' && <div className="tv5-skeleton" />}
    {status === 'error' && onRetry && <button type="button" className="tv5-button" onClick={onRetry}>Thử lại</button>}
  </section>;
}
export function FilterBar({ children }: { children: ReactNode }) {
  return <form className="tv5-filters" onSubmit={e => e.preventDefault()} aria-label="Bộ lọc dữ liệu">{children}</form>;
}
export function DataTable({ label, headers, children }: { label: string; headers: readonly string[]; children: ReactNode }) {
  return <div className="tv5-table-scroll" tabIndex={0} role="region" aria-label={label}>
    <table><caption className="tv5-sr-only">{label}</caption><thead><tr>{headers.map(h => <th key={h} scope="col">{h}</th>)}</tr></thead><tbody>{children}</tbody></table>
  </div>;
}
export function Pagination({ page, pages, total, onPage }: { page: number; pages: number; total: number; onPage: (n: number) => void }) {
  return <nav className="tv5-pagination" aria-label="Phân trang"><span>{total} bản ghi · Trang {page}/{pages}</span><div>
    <button className="tv5-button tv5-button--quiet" type="button" disabled={page <= 1} onClick={() => onPage(page - 1)}>← Trước</button>
    <button className="tv5-button tv5-button--quiet" type="button" disabled={page >= pages} onClick={() => onPage(page + 1)}>Sau →</button>
  </div></nav>;
}
export function EvidenceLink({ href, children = 'Xem evidence ↗' }: { href: unknown; children?: ReactNode }) {
  const safe = safeEvidenceUrl(href);
  return safe ? <a href={safe} target="_blank" rel="noopener noreferrer">{children}</a> : <span className="tv5-muted">Chưa có biên nhận</span>;
}
export function DetailPanel({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  const id = useId(), panel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    panel.current?.querySelector<HTMLButtonElement>('button')?.focus();
    return () => { if (previous?.isConnected) previous.focus(); };
  }, []);
  return <div className="tv5-overlay" onMouseDown={e => { if (e.target === e.currentTarget) onClose(); }}>
    <div ref={panel} className="tv5-detail" role="dialog" aria-modal="true" aria-labelledby={id} onKeyDown={e => {
      if (e.key === 'Escape') { e.preventDefault(); onClose(); }
      if (e.key !== 'Tab') return;
      const focusable = panel.current?.querySelectorAll<HTMLElement>('button:not(:disabled), a[href], input, select, [tabindex="0"]');
      if (!focusable?.length) return;
      const first = focusable[0], last = focusable[focusable.length - 1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    }}>
      <header><div><span className="tv5-eyebrow">CHI TIẾT BẢN GHI</span><h2 id={id}>{title}</h2></div><button type="button" className="tv5-button tv5-button--quiet" onClick={onClose} aria-label="Đóng chi tiết">✕</button></header>
      {children}
    </div>
  </div>;
}
export function DetailFields({ fields }: { fields: readonly (readonly [string, ReactNode])[] }) {
  return <dl className="tv5-detail-fields">{fields.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value ?? 'Chưa có snapshot'}</dd></div>)}</dl>;
}
export function SnapshotNote({ text }: { text: string }) { return <p className="tv5-snapshot-note"><span aria-hidden="true">ⓘ </span>{text}</p>; }
export function dateTime(value: string | null) {
  if (!value || !Number.isFinite(Date.parse(value))) return 'Chưa có thông tin';
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short', timeZone: 'UTC' }).format(new Date(value)) + ' UTC';
}
