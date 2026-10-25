// Isolated visual/interaction fixture. Not a production entry, API or mutation adapter.
import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import '../../../styles.css';
import { ProductManagementPage } from '../ProductManagementPage.tsx';
import type { ProductManagementState, ProductStatus, ProductSummary } from '../productManagementTypes.ts';

const categories = [
  { id: '22222222-2222-4222-8222-222222222222', name: 'Đồ gia dụng', parentId: null },
  { id: '33333333-3333-4333-8333-333333333333', name: 'Gốm thủ công', parentId: '22222222-2222-4222-8222-222222222222' },
];
const store = { id: '11111111-1111-4111-8111-111111111111', name: 'Gốm Việt Studio' };
const statuses: ProductStatus[] = ['DRAFT', 'PENDING_REVIEW', 'ACTIVE', 'REJECTED', 'SUSPENDED', 'INACTIVE'];
const names = ['Cốc gốm men mây', 'Bình hoa dáng mộc', 'Bộ bát men ngọc', 'Đĩa gốm hoa nắng', 'Ấm trà đất nung', 'Lọ hoa mini'];
const products: ProductSummary[] = statuses.map((status, i) => ({
  id: `44444444-4444-4444-8444-44444444444${i}`, storeId: store.id, categoryId: categories[1].id,
  sku: `GOM-VIET-0${i + 1}`, name: names[i], unitPrice: ['125000', '285000', '450000', '95000', '375000', '80000'][i],
  currency: 'VND', status, version: '9007199254740993',
}));
function Preview() {
  const [mode, setMode] = useState('ready'), [second, setSecond] = useState(false);
  const currentStore = second ? { id: '55555555-5555-4555-8555-555555555555', name: 'Gốm Việt — Cửa hàng thứ hai' } : store;
  const state: ProductManagementState = mode === 'ready' || mode === 'empty'
    ? { status: 'ready', store: currentStore, categories, products: mode === 'empty' || second ? [] : products }
    : { status: mode as 'loading' | 'unauthorized' | 'unavailable' | 'error' };
  return <main style={{ padding: 'clamp(12px, 3vw, 32px)', minHeight: '100vh' }}>
    <div style={{ maxWidth: '76rem', margin: '0 auto 16px', padding: '12px 16px', border: '1px dashed #a4b5c4', borderRadius: 12, display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center', fontSize: 13 }}>
      <strong style={{ flex: '1 1 260px' }}>Kiểm thử giao diện · dữ liệu mẫu · không kết nối API</strong>
      <label>Trạng thái kiểm thử <select aria-label="Trạng thái kiểm thử" value={mode} onChange={(event) => setMode(event.target.value)} style={{ minHeight: 44, padding: 8 }}><option value="ready">Có dữ liệu</option><option value="empty">Trống</option><option value="loading">Đang tải</option><option value="error">Lỗi tải</option><option value="unauthorized">Không có quyền</option><option value="unavailable">Chưa khả dụng</option></select></label>
      <button type="button" onClick={() => setSecond(!second)} style={{ minHeight: 44, padding: '8px 12px', border: '1px solid #8ea0b2', borderRadius: 8, cursor: 'pointer' }}>Đổi Store kiểm thử</button>
    </div>
    <ProductManagementPage state={state} onRetry={() => setMode('unavailable')} />
  </main>;
}
createRoot(document.getElementById('root')!).render(<Preview />);
