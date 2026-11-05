import { useRef, useState } from 'react';
import { api } from '../../shared/api';
import { Badge, dateTime, ErrorNote, useAction } from '../../shared/ui';

export interface ReturnView {
  readonly requestId: string;
  readonly orderId: string;
  readonly storeOrderId: string;
  readonly status: 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'RECEIVED';
  readonly reason: string;
  readonly requestedAt: string;
}

interface Props {
  readonly orderId: string;
  readonly storeOrderId: string;
  readonly deliveredAt: string | null;
  readonly settledAt: string | null;
  readonly status: string;
  readonly existing: ReturnView | undefined;
  readonly loading: boolean;
  readonly loadError: unknown;
}

/**
 * Buyer Return MVP in the existing Order History. On network uncertainty, preserve
 * both the original payload AND Idempotency-Key for any manual retry. Never silently
 * mint a new key for an ambiguous request.
 */
export function ReturnRequestPanel({ orderId, storeOrderId, deliveredAt, settledAt, status,
                                     existing, loading, loadError }: Props) {
  const [reason, setReason] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const submittedPayload = useRef<{ key: string; reason: string } | null>(null);
  const returns = useAction(async (_: void) => {
    if (!submittedPayload.current) {
      const cleaned = reason.trim();
      if (!cleaned || cleaned.length > 500) throw new Error('Lý do phải có từ 1 đến 500 ký tự.');
      submittedPayload.current = { key: crypto.randomUUID(), reason: cleaned };
    }
    const payload = submittedPayload.current;
    return api<ReturnView>(`/api/orders/${orderId}/store-orders/${storeOrderId}/returns`, {
      method: 'POST',
      headers: { 'Idempotency-Key': payload.key },
      body: { reason: payload.reason },
    });
  }, ['order-returns'], () => setSubmitted(true));

  if (status !== 'DELIVERED') return null;
  if (loading) return <p role="status">Đang kiểm tra yêu cầu trả hàng…</p>;
  if (loadError) return <div className="note note--error" role="alert">
    Không thể kiểm tra trạng thái trả hàng. Hãy tải lại trang trước khi gửi yêu cầu.
  </div>;
  if (existing && existing.status !== 'REJECTED') return <section className="stack" aria-label="Trạng thái yêu cầu trả hàng">
    <div className="row row--wrap"><strong>Yêu cầu trả hàng</strong> <Badge value={existing.status} /></div>
    <small className="muted">Đã gửi: {dateTime(existing.requestedAt)}</small>
    <p className="muted">Lý do: {existing.reason}</p>
    <p className="muted">Không hoàn tiền tự động. Cửa hàng sẽ xử lý yêu cầu.</p>
  </section>;

  if (settledAt) return <p className="muted">Đơn đã đối soát, không còn đủ điều kiện yêu cầu trả hàng.</p>;
  if (!deliveredAt || !Number.isFinite(Date.parse(deliveredAt))) return null;
  // The deadline gates new requests, not replay of a potentially committed request.
  // Preserve the frozen key/body and let the server return its authoritative outcome.
  if (!submittedPayload.current && Date.now() > Date.parse(deliveredAt) + 7 * 24 * 60 * 60 * 1000) {
    return <p className="muted">Đã quá 7 ngày kể từ khi giao hàng.</p>;
  }

  return <section className="stack" aria-label="Yêu cầu trả hàng">
    {existing?.status === 'REJECTED' && <p className="muted">Yêu cầu trước đã bị từ chối. Bạn có thể gửi yêu cầu mới nếu còn trong thời hạn 7 ngày.</p>}
    <h3>Yêu cầu trả hàng</h3>
    <p className="muted">Chỉ yêu cầu trả toàn bộ đơn của cửa hàng này trong 7 ngày sau khi nhận.
      Chưa có hoàn tiền tự động.</p>
    <label className="field">Lý do trả hàng
      <textarea aria-label="Lý do trả hàng" placeholder="Mô tả lý do (bắt buộc, tối đa 500 ký tự)"
        value={reason} maxLength={500} required disabled={submittedPayload.current !== null}
        onChange={(event) => setReason(event.currentTarget.value)} />
    </label>
    <button type="button" className="btn" disabled={returns.isPending || submitted || (!submittedPayload.current && !reason.trim())}
      onClick={() => returns.mutate(undefined)}>
      {returns.isPending ? 'Đang gửi…' : submittedPayload.current ? 'Thử lại cùng mã yêu cầu' : 'Gửi yêu cầu trả hàng'}
    </button>
    <ErrorNote error={returns.error} />
    {submitted && <p role="status">Đã gửi yêu cầu. Đang cập nhật trạng thái từ máy chủ…</p>}
    {submittedPayload.current && !submitted && <p className="muted">
      Nếu mạng bị gián đoạn, chỉ thử lại cùng yêu cầu và cùng mã; không sửa lý do hoặc tạo mã mới.
    </p>}
  </section>;
}
