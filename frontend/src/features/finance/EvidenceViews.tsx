import { useState } from 'react';
import { DashboardStatCard, DataTable, DetailFields, DetailPanel, EvidenceLink, FilterBar, Pagination, SnapshotNote, StatePanel, StatusBadge, dateTime } from './components';
import { paginate, qaCounts, qaResult } from './workspaceModel';
import type { AuditView, PrRow, QaCase, QaRun } from './workspaceTypes';

export function QADashboard({ runs }: { runs: readonly QaRun[] }) {
  const [runId, setRunId] = useState(runs[0]?.id ?? ''), [branch, setBranch] = useState(''), [commit, setCommit] = useState('');
  const [search, setSearch] = useState(''), [module, setModule] = useState(''), [status, setStatus] = useState(''), [type, setType] = useState('');
  const [page, setPage] = useState(1), [selected, setSelected] = useState<QaCase | null>(null);
  const choices = runs.filter(r => (!branch || r.branch === branch) && (!commit || r.head.includes(commit.trim())));
  const run = choices.find(r => r.id === runId) ?? choices[0];
  const cases = run?.cases ?? [], counts = qaCounts(cases);
  const rows = cases.filter(r => (!search || [r.name, r.suite].some(v => v.toLowerCase().includes(search.toLowerCase()))) && (!module || r.module === module) && (!status || qaResult(r) === status) && (!type || r.type === type));
  const slice = paginate(rows, page, 10);
  function selectRun(id: string) { setRunId(id); setPage(1); setSelected(null); setModule(''); setStatus(''); setType(''); setSearch(''); }
  return <>
    <div className="tv5-source-banner"><StatusBadge value="SKIP" label="RECORDED QA" /><span>Biên nhận đã lưu · không phải test đang chạy hay tổng release</span></div>
    <FilterBar>
      <label className="tv5-search">Lượt chạy<select aria-label="Lượt chạy" value={run?.id ?? ''} onChange={e => selectRun(e.target.value)}>{choices.map(r => <option key={r.id} value={r.id}>{r.title} · {r.head.slice(0, 7)}</option>)}</select></label>
      <label>Branch<select aria-label="Branch" value={branch} onChange={e => { setBranch(e.target.value); setSelected(null); setPage(1); }}><option value="">Tất cả branch</option>{[...new Set(runs.map(r => r.branch))].map(b => <option key={b}>{b}</option>)}</select></label>
      <label>Commit SHA<input value={commit} placeholder="SHA hoặc tiền tố" onChange={e => { setCommit(e.target.value); setSelected(null); setPage(1); }} /></label>
    </FilterBar>
    {!run ? <StatePanel status="empty" /> : <>
      <div className="tv5-run-info"><div><strong>{run.title}</strong><code>{run.head}</code><small>{run.environment} · {dateTime(run.executedAt)}</small></div><EvidenceLink href={run.evidence}>Biên nhận nguồn ↗</EvidenceLink></div>
      <div className="tv5-stats tv5-stats--qa">
        <DashboardStatCard label="Đã thực thi" value={counts.executed} note="PASS + FAIL của đúng lượt này" accent />
        {(['PASS', 'FAIL', 'SKIP', 'NOT_RUN', 'BLOCKED'] as const).map(key => <DashboardStatCard key={key} label={key} value={counts[key]} note="Không cộng lượt chạy khác" />)}
      </div>
      <SnapshotNote text="Bộ lọc bảng không đổi số liệu gốc của lượt chạy. PASS phải có đường dẫn receipt được phép; thiếu receipt hiển thị NOT_RUN. Các gate chưa chạy không phải lỗi test." />
      <section className="tv5-card tv5-table-card"><div className="tv5-card-heading"><h2>Test cases & evidence</h2><span className="tv5-count">{rows.length} ca phù hợp</span></div>
        <FilterBar><label className="tv5-search">Tìm test<input type="search" value={search} onChange={e => { setSearch(e.target.value); setPage(1); }} placeholder="Tên ca hoặc suite" /></label>
          <label>Module<select aria-label="Module" value={module} onChange={e => { setModule(e.target.value); setPage(1); }}><option value="">Tất cả module</option>{[...new Set(cases.map(r => r.module))].map(m => <option key={m}>{m}</option>)}</select></label>
          <label>Kết quả<select aria-label="Kết quả" value={status} onChange={e => { setStatus(e.target.value); setPage(1); }}><option value="">Tất cả kết quả</option>{['PASS', 'FAIL', 'SKIP', 'NOT_RUN', 'BLOCKED'].map(s => <option key={s}>{s}</option>)}</select></label>
          <label>Test type<select aria-label="Test type" value={type} onChange={e => { setType(e.target.value); setPage(1); }}><option value="">Tất cả loại</option>{[...new Set(cases.map(r => r.type))].map(t => <option key={t}>{t}</option>)}</select></label>
        </FilterBar>
        {!rows.length ? <StatePanel status="empty" /> : <DataTable label="Danh sách test" headers={['Test / Suite', 'Module / Type', 'Kết quả', 'SHA', 'Environment / Executed', 'Evidence', 'Chi tiết']}>
          {slice.rows.map(r => <tr key={r.id}><td><strong className="tv5-wrap">{r.name}</strong><small className="tv5-wrap">{r.suite}</small></td><td>{r.module}<small>{r.type}</small></td><td><StatusBadge value={qaResult(r)} /></td><td><code>{run.head.slice(0, 7)}</code></td><td>{run.environment}<small>{dateTime(run.executedAt)}</small></td><td><EvidenceLink href={r.evidence} /></td><td><button type="button" className="tv5-row-action" onClick={() => setSelected(r)} aria-label={`Chi tiết test ${r.id}`}>Xem →</button></td></tr>)}
        </DataTable>}
        <Pagination {...slice} onPage={setPage} />
      </section>
      {selected && <DetailPanel title={selected.name} onClose={() => setSelected(null)}><StatusBadge value={qaResult(selected)} /><DetailFields fields={[
        ['Suite', selected.suite], ['Module', selected.module], ['Test type', selected.type], ['Commit', run.head], ['Branch', run.branch], ['Environment', run.environment], ['Executed at', dateTime(run.executedAt)],
        ['Lỗi / điều kiện', selected.error ?? (selected.result === 'PASS' && qaResult(selected) !== 'PASS' ? 'Thiếu biên nhận được phép; chưa xác minh PASS.' : 'Không có lỗi được ghi nhận trong receipt này')], ['Evidence', <EvidenceLink href={selected.evidence} />],
      ]} /></DetailPanel>}
    </>}
  </>;
}
export function PRDashboard({ audit }: { audit: AuditView }) {
  const [search, setSearch] = useState(''), [mode, setMode] = useState('all'), [page, setPage] = useState(1), [selected, setSelected] = useState<PrRow | null>(null);
  if (audit.status !== 'ready') return <><SnapshotNote text="Audit chưa đầy đủ hoặc dữ liệu không hợp lệ. Không hiển thị tập PR một phần như kết quả đã xác minh." /><StatePanel status="blocked" /></>;
  const rows = audit.rows.filter(r => (!search || `${r.number} ${r.title} ${r.author}`.toLowerCase().includes(search.toLowerCase()))
    && (mode === 'all' || mode === 'open' && r.state === 'open' || mode === 'tv5' && r.requested.includes('nguyenminhduy090-alt')
      || mode === 'changes' && r.reviews.some(v => v.state === 'CHANGES_REQUESTED') || mode === 'failed' && r.ci === 'FAILED'));
  const slice = paginate(rows, page);
  return <>
    <div className="tv5-source-banner"><StatusBadge value="SKIP" label="RECORDED GITHUB" /><span>Snapshot {dateTime(audit.asOf)} · không gọi GitHub API từ trình duyệt</span></div>
    <div className="tv5-stats"><DashboardStatCard label="PR trong snapshot" value={audit.rows.length} note="Chỉ các PR đã thu thập, không phải live" accent />
      <DashboardStatCard label="Chờ TV5" value={audit.rows.filter(r => r.requested.includes('nguyenminhduy090-alt')).length} note="Request đang mở tại thời điểm ghi" />
      <DashboardStatCard label="Changes requested" value={audit.rows.filter(r => r.reviews.some(v => v.state === 'CHANGES_REQUESTED')).length} note="COMMENT không thay formal decision" />
      <DashboardStatCard label="Thread chưa resolve" value={audit.rows.reduce((s, r) => s + r.unresolved, 0)} note="Giữ lịch sử, không có thao tác ghi" />
    </div>
    <SnapshotNote text="APPROVED chỉ đúng phạm vi/HEAD của review. CI xanh và mergeable không thay acceptance. NO_CHECKS không phải PASS; Draft và contract gates vẫn cần owner xác nhận. Snapshot này không tự cập nhật khi PR mới được tạo." />
    <section className="tv5-card tv5-table-card"><div className="tv5-card-heading"><h2>Pull Requests & Reviewers</h2><span className="tv5-count">{rows.length} PR phù hợp</span></div>
      <FilterBar><label className="tv5-search">Tìm Pull Request<input type="search" value={search} onChange={e => { setSearch(e.target.value); setPage(1); }} placeholder="Số PR, tiêu đề, author…" /></label>
        <label>Phạm vi PR<select aria-label="Phạm vi PR" value={mode} onChange={e => { setMode(e.target.value); setPage(1); }}><option value="all">Tất cả snapshot</option><option value="open">OPEN</option><option value="tv5">Chờ TV5</option><option value="changes">CHANGES_REQUESTED</option><option value="failed">CI FAIL</option></select></label>
      </FilterBar>
      {!rows.length ? <StatePanel status="empty" /> : <DataTable label="Pull Requests" headers={['PR / Author', 'HEAD / State', 'Reviewers / Decisions', 'CI', 'Mergeability / Threads', 'Next action', 'Chi tiết']}>
        {slice.rows.map(r => <tr key={r.number}><td><EvidenceLink href={r.url}>#{r.number} ↗</EvidenceLink><strong className="tv5-wrap">{r.title}</strong><small>{r.author}</small></td><td><code>{r.head.slice(0, 7)}</code><small>{r.draft ? 'DRAFT' : r.state.toUpperCase()}</small></td>
          <td>{r.reviews.map(v => <div className="tv5-review" key={v.author}><small>{v.author}</small><StatusBadge value={v.state} /><code>@{v.head?.slice(0, 7) ?? '?'}</code>{v.head !== r.head && <small className="tv5-warning">HEAD cũ · cần đọc delta</small>}</div>)}<small>Requested: {r.requested.join(', ') || 'Không có request mở'}</small></td>
          <td><StatusBadge value={r.ci} /></td><td>{r.mergeable === null ? 'UNKNOWN' : r.mergeable ? 'Không conflict' : 'Có conflict'}<small>{r.unresolved} unresolved</small></td><td className="tv5-wrap">{r.nextAction}</td><td><button type="button" className="tv5-row-action" onClick={() => setSelected(r)} aria-label={`Chi tiết PR ${r.number}`}>Xem →</button></td></tr>)}
      </DataTable>}
      <Pagination {...slice} onPage={setPage} />
    </section>
    {selected && <DetailPanel title={`PR #${selected.number}`} onClose={() => setSelected(null)}><h3>{selected.title}</h3><DetailFields fields={[
      ['Author', selected.author], ['HEAD', selected.head], ['State', selected.draft ? 'DRAFT' : selected.state], ['Requested', selected.requested.join(', ') || 'Không có request mở'], ['CI', selected.ci],
      ['Mergeability', selected.mergeable === null ? 'UNKNOWN' : selected.mergeable ? 'Không conflict' : 'Có conflict'], ['Unresolved threads', selected.unresolved], ['Next action', selected.nextAction], ['GitHub', <EvidenceLink href={selected.url}>Mở PR ↗</EvidenceLink>],
    ]} /><SnapshotNote text="Chỉ đọc. Không request reviewer, Approve, resolve thread hoặc Merge từ giao diện này." /></DetailPanel>}
  </>;
}
