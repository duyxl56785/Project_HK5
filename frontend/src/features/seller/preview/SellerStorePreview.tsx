import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { SellerStoreOverview } from '../index';
import type { SellerStoreState } from '../index';
import '../../../styles.css';

// Test/preview fixtures only, never an authenticated session or production data provider.
function Preview() {
  const params = new URLSearchParams(window.location.search);
  const mode = params.get('state');
  const [retried, setRetried] = useState(false);
  const seller = { id: '11111111-1111-4111-8111-111111111111', version: '9007199254740993', status: 'APPROVED' as const,
    reviewReason: 'PRIVATE_REASON_DO_NOT_RENDER', ownerReference: 'PRIVATE_OWNER_DO_NOT_RENDER' };
  const store = { id: '22222222-2222-4222-8222-222222222222', sellerId: seller.id,
    name: params.has('long') ? 'Cửa hàng thiết kế và phụ kiện dành cho gia đình Việt Nam '.repeat(3) : 'Cửa hàng Việt',
    slug: params.has('long') ? `cua-hang-${'a'.repeat(100)}` : 'cua-hang-viet', open: mode !== 'closed', version: '9223372036854775807' };
  let state: SellerStoreState = { status: 'ready', seller, store };
  if (mode === 'loading' || mode === 'empty' || mode === 'unauthorized' || mode === 'unavailable') state = { ...state, status: mode };
  if (mode === 'error' && !retried) state = { ...state, status: 'error', onRetry: () => setRetried(true) };
  if (mode === 'no-store') state = { status: 'ready', seller, store: null };
  if (mode === 'inconsistent') state = { status: 'ready', seller, store: { ...store, sellerId: '33333333-3333-4333-8333-333333333333' } };
  return <main style={{ maxWidth: '68rem', margin: '0 auto', padding: '1.25rem', boxSizing: 'border-box' }}>
    <h1>Seller/Store — kiểm thử UI</h1>
    <p>Dữ liệu mẫu phục vụ kiểm thử. Không phải hồ sơ của tài khoản hiện tại; không kết nối API.</p>
    <SellerStoreOverview state={state} />
  </main>;
}
createRoot(document.getElementById('root')!).render(<Preview />);
