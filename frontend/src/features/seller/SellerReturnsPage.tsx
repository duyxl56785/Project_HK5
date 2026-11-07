import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { Badge, dateTime, ErrorNote, Loaded, shortId, useAction } from '../../shared/ui';

export interface SellerReturn {
  readonly requestId: string; readonly orderId: string; readonly storeOrderId: string;
  readonly status: 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'RECEIVED';
  readonly reason: string; readonly requestedAt: string;
  readonly reviewedAt: string | null; readonly receivedAt: string | null; readonly receiptReference: string | null;
}

function Row({ item }: { readonly item: SellerReturn }) {
  const [receipt, setReceipt] = useState('');
  const [confirmReject, setConfirmReject] = useState(false);
  const client = useQueryClient();
  const act = useAction(({ action, body }: { action: string; body?: { receiptReference: string } }) =>
    api(`/api/seller/returns/${item.requestId}/${action}`, { method: 'POST', body }), ['seller-returns', 'seller-orders']);
  // A lost response then retry answers 409; re-read so the Seller sees the committed state.
  const run = (action: string, body?: { receiptReference: string }) =>
    act.mutate({ action, body }, { onError: () => client.invalidateQueries({ queryKey: ['seller-returns'] }) });
  return <article className="panel stack">
    <div className="row row--between"><strong>Đơn #{shortId(item.orderId)}</strong><Badge value={item.status} /></div>
    <small className="muted">Gửi lúc {dateTime(item.requestedAt)}</small>
    <p>Lý do: {item.reason}</p>
    {item.receiptReference && <p className="muted">Mã nhận hàng: {item.receiptReference} · {item.receivedAt && dateTime(item.receivedAt)}</p>}
    {item.status === 'REQUESTED' && <div className="row row--wrap">
      <button type="button" className="btn btn--primary" disabled={act.isPending} onClick={() => run('approve')}>Đồng ý trả hàng</button>
      {confirmReject
        // REJECTED is final and unblocks settlement, so it takes a second, explicit click.
        ? <>
          <span role="alert">Từ chối là không đổi lại được và cho phép sàn thanh toán đơn này.</span>
          <button type="button" className="btn" disabled={act.isPending} onClick={() => run('reject')}>Xác nhận từ chối</button>
          <button type="button" className="btn" disabled={act.isPending} onClick={() => setConfirmReject(false)}>Hủy</button>
        </>
        : <button type="button" className="btn" disabled={act.isPending} onClick={() => setConfirmReject(true)}>Từ chối</button>}
    </div>}
    {item.status === 'APPROVED' && <div className="row row--wrap">
      <input aria-label="Mã nhận hàng" placeholder="Mã vận đơn hoặc phiếu nhận" maxLength={128} value={receipt}
        onChange={(e) => setReceipt(e.currentTarget.value)} />
      <button type="button" className="btn btn--primary" disabled={act.isPending || !receipt.trim()}
        onClick={() => run('receive', { receiptReference: receipt.trim() })}>Xác nhận đã nhận hàng</button>
    </div>}
    <ErrorNote error={act.error} />
  </article>;
}

/** Store-scoped Return workflow (#155/#156). Restock and refund are not done here. */
export function SellerReturnsPage() {
  const list = useQuery({ queryKey: ['seller-returns'], queryFn: () => api<SellerReturn[]>('/api/seller/returns') });
  return <div className="stack">
    <h1>Yêu cầu trả hàng</h1>
    <p className="muted">Đồng ý rồi xác nhận khi đã nhận lại hàng. Chưa có hoàn tiền hay nhập lại kho tự động.
      Yêu cầu chưa bị từ chối sẽ tạm giữ thanh toán của đơn.</p>
    <Loaded query={list} empty={(rows) => rows.length === 0}>{(rows) => <div className="stack">
      {rows.map((r) => <Row key={r.requestId} item={r} />)}
    </div>}</Loaded>
  </div>;
}
