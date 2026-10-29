import React from 'react';
import ReactDOM from 'react-dom/client';
import { InventoryDashboard } from '../inventory';
import { SellerOrderFulfillment } from '../fulfillment';
import { NotificationCenter } from '../notifications';
import '../../styles.css';
import './Tv4Preview.css';

function Tv4Preview() {
  return <main className="container">
    <span className="tag">PROJECT_HK5 · TV4 LOCAL VIEW</span>
    <h1>Vận hành tồn kho &amp; giao hàng</h1>
    <p>Trang này chạy bằng component TV4 thật. Chưa nạp dữ liệu mẫu: API Store-scoped, SellerOrder/C06 và Notification event contract vẫn chờ phê duyệt.</p>
    <div className="tv4-preview-grid">
      <InventoryDashboard state={{ status: 'empty' }} />
      <SellerOrderFulfillment state={{ status: 'empty' }} />
      <NotificationCenter state={{ status: 'empty' }} />
    </div>
  </main>;
}

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><Tv4Preview /></React.StrictMode>);
