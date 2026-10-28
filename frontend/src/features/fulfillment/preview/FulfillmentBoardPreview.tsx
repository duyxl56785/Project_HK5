import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { FulfillmentBoard } from '../index.ts';
import type { FulfillmentBoardState } from '../index.ts';
import './preview.css';

const storeId = '11111111-1111-4111-8111-111111111111';
const initialOrders = [
  { id: '22222222-2222-4222-8222-222222222222', parentOrderId: '33333333-3333-4333-8333-333333333333', storeId,
    status: 'PLACED' as const, codStatus: 'UNRECORDED' as const, createdAt: '2026-10-28T10:00:00Z',
    actions: [{ id: 'ACCEPT', label: 'Nhận đơn' }] },
  { id: '44444444-4444-4444-8444-444444444444', parentOrderId: '55555555-5555-4555-8555-555555555555', storeId,
    status: 'DELIVERED' as const, codStatus: 'PAID' as const, createdAt: '2026-10-27T08:30:00Z', actions: [] },
];

function Preview() {
  const [lastRequest, setLastRequest] = useState<string | null>(null);
  const [pendingOrderId, setPendingOrderId] = useState<string | undefined>();
  const params = new URLSearchParams(window.location.search);
  let state: FulfillmentBoardState = {
    status: 'ready', storeId, orders: initialOrders, pendingOrderId,
    onAction: (orderId, actionId) => {
      setPendingOrderId(orderId);
      setLastRequest(`Đã nhận yêu cầu mô phỏng: ${actionId} · ${orderId}`);
    },
  };
  const mode = params.get('state');
  if (mode === 'loading' || mode === 'empty' || mode === 'unauthorized' || mode === 'unavailable') state = { status: mode };
  if (mode === 'error') state = { status: 'error', onRetry: () => setLastRequest('Đã nhận yêu cầu thử lại mô phỏng.') };
  if (mode === 'inconsistent') state = { status: 'ready', storeId, orders: [{ ...initialOrders[0], storeId: '66666666-6666-4666-8666-666666666666' }] };

  return <main className="tv4-preview">
    <header className="tv4-preview__banner">
      <span>TV4 · DEMO FIXTURE · KHÔNG CÓ API</span>
      <h1>Đơn giao hàng &amp; COD</h1>
      <p>Dữ liệu mô phỏng cho review giao diện. Nút thao tác chỉ ghi nhận callback tại trang này;
        không đổi SellerOrder, không ghi COD và không gọi Inventory.</p>
    </header>
    <div className="tv4-preview__content"><FulfillmentBoard state={state} />
      {lastRequest && <p className="tv4-preview__result" role="status">{lastRequest}</p>}
    </div>
    <footer className="tv4-preview__footer">Runtime cần TV1 Auth, TV2 Store facts, TV3 SellerOrder lifecycle và TV5 COD/Finance approval.</footer>
  </main>;
}

createRoot(document.getElementById('tv4-fulfillment-preview-root')!).render(<Preview />);
