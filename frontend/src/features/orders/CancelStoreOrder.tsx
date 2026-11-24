import { useState } from 'react';

/**
 * Cancelling a Store order cannot be undone, so the first click only opens the confirmation,
 * where the customer may give a reason. It asks on the page, not with a browser dialog.
 */
export function CancelStoreOrder({ pending, onCancel }: {
  readonly pending: boolean; readonly onCancel: (reason: string) => void;
}) {
  const [asking, setAsking] = useState(false);
  const [reason, setReason] = useState('');
  if (!asking) return <div className="row row--wrap">
    <button type="button" className="btn btn--danger" onClick={() => setAsking(true)}>Hủy đơn của cửa hàng này</button>
  </div>;
  return <div className="stack" role="group" aria-label="Xác nhận hủy đơn">
    <p>Hủy đơn của cửa hàng này? Sau khi hủy không thể khôi phục.</p>
    <input aria-label="Lý do hủy" placeholder="Lý do hủy (không bắt buộc)" maxLength={500} value={reason}
      onChange={(e) => setReason(e.currentTarget.value)} />
    <div className="row row--wrap">
      <button type="button" className="btn btn--danger" disabled={pending} onClick={() => onCancel(reason.trim())}>Xác nhận hủy</button>
      <button type="button" className="btn" disabled={pending} onClick={() => setAsking(false)}>Không hủy</button>
    </div>
  </div>;
}
