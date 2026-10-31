import { useState } from 'react';
import { DashboardStatCard, DataTable, DetailFields, DetailPanel, FilterBar, Pagination, SnapshotNote, StatePanel, StatusBadge, dateTime } from './components';
import { filterTransactions, financeSummary, formatVnd, paginate, sumAmounts } from './workspaceModel';
import type { Transaction, View } from './workspaceTypes';

export function FinanceSummary({ rows }: { rows: readonly Transaction[] }) {
  const s = financeSummary(rows);
  return <div className="tv5-stats">
    <DashboardStatCard label="Giá trị đơn quan sát" value={formatVnd(s.total, 'VND')} note="Snapshot DEMO · không phải doanh thu" accent />
    <DashboardStatCard label="COD có evidence mẫu" value={formatVnd(s.verifiedCod, 'VND')} note="Không xác nhận COD production" />
    <DashboardStatCard label="COD cần đối chiếu" value={formatVnd(s.unverifiedCod, 'VND')} note="COLLECTED nhưng chưa đủ evidence mẫu" />
    <DashboardStatCard label="SellerOrder" value={s.count} note={`${s.settlements} bản ghi settlement · ${s.errors} bị chặn`} />
  </div>;
}
function FinanceCharts({ rows }: { rows: readonly Transaction[] }) {
  const days = [...new Set(rows.map(r => r.createdAt.slice(0, 10)))].sort();
  const totals = days.map(day => sumAmounts(rows.filter(r => r.createdAt.startsWith(day)).map(r => r.currency === 'VND' ? r.amount : null)));
  const units = (s: string | null) => { if (!s) return 0n; const [w, f = ''] = s.split('.'); return BigInt(w + f.padEnd(6, '0')); };
  const maximum = totals.reduce((max, v) => units(v) > max ? units(v) : max, 1n);
  const cod = ['PENDING', 'COLLECTED', 'VOID', 'UNKNOWN'].map(status => ({ status, count: rows.filter(r => (r.cod ?? 'UNKNOWN') === status).length }));
  const settlement = ['RECORDED', 'BLOCKED', 'UNAVAILABLE'].map(status => ({ status, count: rows.filter(r => (r.settlement?.state ?? 'UNAVAILABLE') === status).length }));
  return <div className="tv5-chart-grid">
    <section className="tv5-card"><div className="tv5-card-heading"><h2>Giá trị theo ngày</h2><small>DEMO · VND · UTC</small></div>
      <div className="tv5-bars" role="img" aria-label="Giá trị snapshot theo ngày UTC, xem bảng số liệu bên dưới">
        {days.map((day, i) => <div key={day} className="tv5-bar-column"><span className="tv5-bar" style={{ height: `${Math.max(2, Number(units(totals[i]) * 100n / maximum))}%` }} /><small>{day.slice(8)}/{day.slice(5, 7)}</small></div>)}
      </div><details><summary>Xem số liệu biểu đồ</summary><ul>{days.map((day, i) => <li key={day}>{day}: {formatVnd(totals[i], 'VND')}</li>)}</ul></details>
    </section>
    <section className="tv5-card"><h2>Trạng thái quan sát</h2><h3>COD</h3>{cod.map(v => <div className="tv5-distribution" key={v.status}><StatusBadge value={v.status} /><strong>{v.count}</strong></div>)}
      <h3>Settlement <small>· nhãn view mẫu</small></h3>{settlement.map(v => <div className="tv5-distribution" key={v.status}><StatusBadge value={v.status} /><strong>{v.count}</strong></div>)}
    </section>
  </div>;
}
export function TransactionViews({ view, rows }: { view: Extract<View, 'finance' | 'cod' | 'settlement'>; rows: readonly Transaction[] }) {
  const [search, setSearch] = useState(''), [storeId, setStoreId] = useState(''), [status, setStatus] = useState('');
  const [from, setFrom] = useState(''), [to, setTo] = useState(''), [page, setPage] = useState(1), [selectedId, setSelectedId] = useState<string | null>(null);
  const filtered = filterTransactions(rows, { search, storeId, status, from, to });
  const list = view === 'settlement' ? filtered.filter(r => r.settlement !== null) : filtered;
  const slice = paginate(list, page), selected = rows.find(r => r.id === selectedId);
  const stores = [...new Map(rows.map(r => [r.storeId, r.store])).entries()];
  function clear() { setSearch(''); setStoreId(''); setStatus(''); setFrom(''); setTo(''); setPage(1); }
  const title = view === 'finance' ? 'Giao dịch quan sát' : view === 'cod' ? 'Theo dõi COD' : 'Bản ghi settlement mẫu';
  return <>
    <div className="tv5-source-banner"><StatusBadge value="PENDING" label="DEMO DATA" /><span>Dữ liệu tài chính mô phỏng · Chỉ đọc · Chưa kết nối backend</span></div>
    <FinanceSummary rows={filtered} />
    {view === 'finance' && filtered.some(r => r.commission === null || r.sellerNet === null) && <SnapshotNote text="Một số bản ghi: Chưa có snapshot commission / seller_net. Giao diện không tự tính phí hoặc suy ra doanh thu khi policy chưa được phê duyệt." />}
    {view === 'finance' && <FinanceCharts rows={filtered} />}
    {view !== 'finance' && <SnapshotNote text={view === 'cod' ? 'PENDING/COLLECTED/VOID phản chiếu SellerOrder.CodStatus; VERIFIED là nhãn evidence của view. Authority xác nhận COD thật chưa tích hợp. UNKNOWN thể hiện thiếu dữ liệu, không phải enum nghiệp vụ mới.' : 'Commission và seller_net chỉ hiển thị snapshot được cung cấp; không tự tính phí. RECORDED/BLOCKED là nhãn view mẫu, không phải contract settlement đã duyệt. M01 Finance production vẫn PENDING.'} />}
    <section className="tv5-card tv5-table-card"><div className="tv5-card-heading"><div><h2>{title}</h2><small>Tìm kiếm và bộ lọc chỉ tác động dữ liệu đang xem.</small></div><span className="tv5-count">{list.length} bản ghi</span></div>
      <FilterBar>
        <label className="tv5-search">Tìm giao dịch<input type="search" placeholder="Order, SellerOrder, Store…" value={search} onChange={e => { setSearch(e.target.value); setPage(1); }} /></label>
        <label>Store<select aria-label="Store" value={storeId} onChange={e => { setStoreId(e.target.value); setPage(1); }}><option value="">Tất cả trong phạm vi</option>{stores.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></label>
        <label>Trạng thái<select aria-label="Trạng thái" value={status} onChange={e => { setStatus(e.target.value); setPage(1); }}><option value="">Tất cả trạng thái</option>{(view === 'settlement' ? ['RECORDED', 'BLOCKED'] : ['PENDING', 'COLLECTED', 'VOID', 'UNVERIFIED', 'UNKNOWN', 'DELIVERED', 'DISPATCHED', 'CANCELLED']).map(s => <option key={s}>{s}</option>)}</select></label>
        <label>Từ ngày UTC<input type="date" value={from} onChange={e => { setFrom(e.target.value); setPage(1); }} /></label>
        <label>Đến ngày UTC<input type="date" value={to} min={from || undefined} onChange={e => { setTo(e.target.value); setPage(1); }} /></label>
        <button className="tv5-button tv5-button--quiet" type="button" onClick={clear}>Xóa bộ lọc</button>
      </FilterBar>
      {from && to && from > to && <p role="alert" className="tv5-inline-error">Ngày kết thúc phải từ ngày bắt đầu trở đi.</p>}
      {!slice.total ? <StatePanel status="empty" /> : <DataTable label={title} headers={view === 'settlement'
        ? ['Settlement / SellerOrder', 'Store / Seller', 'Amount / Currency', 'Commission', 'Seller net', 'Status', 'Created / Settled', 'Chi tiết']
        : view === 'cod' ? ['Order / SellerOrder', 'Store / Seller', 'Amount / Currency', 'Delivery', 'COD', 'Evidence', 'Updated', 'Chi tiết']
          : ['Order / SellerOrder', 'Store / Seller', 'Amount / Currency', 'COD', 'Settlement', 'Created / Updated', 'Chi tiết']}>
        {slice.rows.map(r => <tr key={r.id}>
          <td><strong>{view === 'settlement' ? r.settlement!.id : r.orderId}</strong><small>{r.id}</small></td>
          <td><strong>{r.store}</strong><small>{r.seller}</small></td>
          <td className="tv5-money">{formatVnd(r.amount, r.currency)}<small>{r.currency}</small></td>
          {view === 'settlement' ? <><td>{formatVnd(r.commission, r.currency)}</td><td>{formatVnd(r.sellerNet, r.currency)}</td><td><StatusBadge value={r.settlement!.state} /></td><td>{dateTime(r.settlement!.createdAt)}<small>{dateTime(r.settlement!.settledAt)}</small></td></>
            : view === 'cod' ? <><td><StatusBadge value={r.delivery} /></td><td><StatusBadge value={r.cod ?? 'UNKNOWN'} /></td><td><StatusBadge value={r.verification} /><small>{r.paymentEvidence ? 'Tham chiếu mẫu có sẵn' : 'Chưa có thông tin'}</small></td><td>{dateTime(r.updatedAt)}</td></>
              : <><td><StatusBadge value={r.cod ?? 'UNKNOWN'} /></td><td><StatusBadge value={r.settlement?.state ?? 'UNAVAILABLE'} /></td><td>{dateTime(r.createdAt)}<small>{dateTime(r.updatedAt)}</small></td></>}
          <td><button type="button" className="tv5-row-action" aria-label={`Xem chi tiết ${r.id}`} onClick={() => setSelectedId(r.id)}>Xem →</button></td>
        </tr>)}
      </DataTable>}
      <Pagination {...slice} onPage={setPage} />
    </section>
    {selected && <DetailPanel title={selected.id} onClose={() => setSelectedId(null)}><StatusBadge value="PENDING" label="DEMO DATA · CHỈ ĐỌC" />
      <DetailFields fields={[
        ['Order ID', selected.orderId], ['SellerOrder ID', selected.id], ['Store', selected.store], ['Seller', selected.seller],
        ['Amount', formatVnd(selected.amount, selected.currency)], ['Currency', selected.currency], ['Delivery', selected.delivery], ['COD', selected.cod ?? 'UNKNOWN'],
        ['Xác minh evidence', selected.verification], ['Payment evidence mẫu', selected.paymentEvidence], ['Commission snapshot', formatVnd(selected.commission, selected.currency)],
        ['Seller net snapshot', formatVnd(selected.sellerNet, selected.currency)], ['Settlement ID', selected.settlement?.id], ['Settlement status', selected.settlement?.state],
        ['Created at', dateTime(selected.createdAt)], ['Updated at', dateTime(selected.updatedAt)], ['Settlement date', dateTime(selected.settlement?.settledAt ?? null)],
        ['Blocked reason', selected.settlement?.reason ?? 'Chưa có quyết định policy production'],
      ]} /><SnapshotNote text="Không tạo settlement, không xác nhận COD, không release payment/refund. Không suy ra tính đủ điều kiện từ dữ liệu mô phỏng." />
    </DetailPanel>}
  </>;
}
