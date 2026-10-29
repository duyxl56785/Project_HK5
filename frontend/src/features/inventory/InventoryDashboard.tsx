import { useId } from 'react';
import type { ReactNode } from 'react';
import type { InventoryState } from './inventoryTypes';
import { isOutOfStock, readInventory } from './inventoryModel';
import './InventoryDashboard.css';

export interface InventoryDashboardProps { readonly state: InventoryState }

const text = {
  loading: ['Đang tải tồn kho…', 'Đang lấy số liệu mới nhất từ Inventory.'],
  empty: ['Chưa có mặt hàng', 'Cửa hàng này chưa có SKU nào được ghi nhận.'],
  error: ['Không thể tải tồn kho', 'Vui lòng thử lại để lấy số liệu mới.'],
} as const;

export function InventoryDashboard({ state }: InventoryDashboardProps) {
  const id = useId();
  let content: ReactNode;
  if (state.status === 'ready') {
    const items = readInventory(state.items);
    if (!items) {
      content = <div className="tv4-inventory__notice" role="alert">
        <h3>Dữ liệu tồn kho không hợp lệ</h3>
        <p>Không thể hiển thị số liệu chưa được xác thực.</p>
      </div>;
    } else if (items.length === 0) {
      content = <div className="tv4-inventory__notice" role="status">
        <h3>{text.empty[0]}</h3><p>{text.empty[1]}</p>
      </div>;
    } else {
      content = <div className="tv4-inventory__table-wrap">
        <table>
          <caption className="sr-only">Danh sách tồn kho</caption>
          <thead><tr><th scope="col">SKU</th><th scope="col">Sản phẩm</th>
            <th scope="col">Khả dụng</th><th scope="col">Đã giữ</th><th scope="col">Trạng thái</th></tr></thead>
          <tbody>{items.map((item) => {
            const out = isOutOfStock(item);
            return <tr key={item.sku}>
              <th scope="row"><code>{item.sku}</code></th>
              <td>{item.name || 'Chưa có tên sản phẩm'}</td>
              <td>{item.availableQuantity}</td><td>{item.reservedQuantity}</td>
              <td><span className={`tv4-inventory__badge tv4-inventory__badge--${out ? 'out' : 'available'}`}
                aria-label={out ? 'Hết hàng' : 'Còn hàng'}>{out ? 'Hết hàng' : 'Còn hàng'}</span></td>
            </tr>;
          })}</tbody>
        </table>
      </div>;
    }
  } else if (state.status === 'error') {
    content = <div className="tv4-inventory__notice" role="alert">
      <h3>{text.error[0]}</h3><p>{text.error[1]}</p>
      <button type="button" onClick={state.onRetry}>Thử lại</button>
    </div>;
  } else {
    const message = text[state.status];
    content = <div className="tv4-inventory__notice" role="status"><h3>{message[0]}</h3><p>{message[1]}</p></div>;
  }

  return <section className="tv4-inventory" aria-labelledby={`${id}-title`} aria-busy={state.status === 'loading'}>
    <header><span className="tv4-inventory__eyebrow">TV4 · Inventory</span>
      <h2 id={`${id}-title`}>Tồn kho cửa hàng</h2>
      <p>Chỉ hiển thị dữ liệu Inventory đã được xác thực. Điều chỉnh tồn kho chưa mở vì API/quyền chưa được phê duyệt.</p>
    </header>
    {content}
  </section>;
}
