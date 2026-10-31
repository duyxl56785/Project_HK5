import { useId, useState } from 'react';
import { InventoryDashboard } from '../inventory';
import { SellerOrderFulfillment } from '../fulfillment';
import type { SellerOperationsState, SellerOperationsView } from './sellerOperationsTypes';
import './SellerOperationsDashboard.css';

export interface SellerOperationsDashboardProps {
  readonly state: SellerOperationsState;
  readonly initialView?: SellerOperationsView;
}

export function SellerOperationsDashboard({ state, initialView = 'inventory' }: SellerOperationsDashboardProps) {
  const id = useId();
  const [view, setView] = useState<SellerOperationsView>(initialView);
  const inventoryTab = `${id}-inventory`;
  const ordersTab = `${id}-orders`;
  return <section className="tv4-seller-operations" aria-labelledby={`${id}-title`}>
    <header><span className="tv4-seller-operations__eyebrow">TV4 · Seller operations</span>
      <h1 id={`${id}-title`}>Quản lý cửa hàng</h1>
      <p>Quản lý tồn kho và SellerOrder trong phạm vi Store đã được xác thực.</p>
    </header>
    <div className="tv4-seller-operations__tabs" role="tablist" aria-label="Khu vực quản lý cửa hàng">
      <button id={inventoryTab} type="button" role="tab" aria-selected={view === 'inventory'}
        aria-controls={`${id}-inventory-panel`} onClick={() => setView('inventory')}>Tồn kho</button>
      <button id={ordersTab} type="button" role="tab" aria-selected={view === 'orders'}
        aria-controls={`${id}-orders-panel`} onClick={() => setView('orders')}>Đơn hàng</button>
    </div>
    {view === 'inventory'
      ? <div id={`${id}-inventory-panel`} role="tabpanel" aria-labelledby={inventoryTab}><InventoryDashboard state={state.inventory} /></div>
      : <div id={`${id}-orders-panel`} role="tabpanel" aria-labelledby={ordersTab}><SellerOrderFulfillment state={state.fulfillment} /></div>}
  </section>;
}
