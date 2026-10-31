import { Badge, dateTime, money, shortId } from '../../shared/ui';

export interface Totals {
  readonly deliveredOrders: number; readonly gross: number; readonly platformFee: number; readonly sellerNet: number;
  readonly codCollected: number; readonly codPending: number; readonly settledNet: number; readonly payableNet: number;
}
interface Row {
  readonly storeOrderId: string; readonly orderId: string; readonly gross: number; readonly platformFee: number;
  readonly sellerNet: number; readonly codStatus: string; readonly deliveredAt: string | null; readonly settledAt: string | null;
}
export interface StoreSummary { readonly storeId: string; readonly storeName: string | null; readonly totals: Totals; readonly rows: readonly Row[] }

export function TotalsGrid({ totals }: { readonly totals: Totals }) {
  const cells: [string, string][] = [
    ['Đơn đã giao', String(totals.deliveredOrders)], ['Doanh thu', money(totals.gross)], ['Phí sàn', money(totals.platformFee)],
    ['Thực nhận', money(totals.sellerNet)], ['COD đã thu', money(totals.codCollected)], ['COD chờ thu', money(totals.codPending)],
    ['Đã thanh toán', money(totals.settledNet)], ['Còn phải trả', money(totals.payableNet)],
  ];
  return <dl className="stats">{cells.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>;
}

export function FinanceTable({ summary }: { readonly summary: StoreSummary }) {
  return <div className="stack">
    <TotalsGrid totals={summary.totals} />
    {summary.rows.length > 0 && <table className="table">
      <thead><tr><th>Đơn</th><th>Giao lúc</th><th>Doanh thu</th><th>Phí sàn</th><th>Thực nhận</th><th>COD</th><th>Thanh toán</th></tr></thead>
      <tbody>{summary.rows.map((r) => <tr key={r.storeOrderId}>
        <td>#{shortId(r.orderId)}</td><td>{dateTime(r.deliveredAt)}</td><td>{money(r.gross)}</td><td>{money(r.platformFee)}</td>
        <td>{money(r.sellerNet)}</td><td><Badge value={r.codStatus} /></td><td>{dateTime(r.settledAt)}</td>
      </tr>)}</tbody></table>}
  </div>;
}
