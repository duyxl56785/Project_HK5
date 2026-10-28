import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { CartView } from '../../cart/index.ts';
import type { CartSnapshot } from '../../cart/index.ts';
import { CheckoutCodView } from '../index.ts';
import './preview.css';

const initial: CartSnapshot = {
  revision: '3', claimed: false, items: [
    { productId: 'p-demo-1', storeId: 'store-hoa-sen', sellerId: 'seller-A',
      title: 'Áo thun demo', quantity: 2, unitPriceSnapshot: '10000.00' },
    { productId: 'p-demo-2', storeId: 'store-sach-viet', sellerId: 'seller-B',
      title: 'Sách demo', quantity: 1, unitPriceSnapshot: '15000.00' },
  ],
};

function Preview() {
  const [cart, setCart] = useState<CartSnapshot>(initial);
  function changeQuantity(productId: string, quantity: number) {
    setCart((previous) => ({
      ...previous, revision: (BigInt(previous.revision) + 1n).toString(),
      items: previous.items.map((line) => line.productId === productId ? { ...line, quantity } : line),
    }));
  }
  function remove(productId: string) {
    setCart((previous) => ({
      ...previous, revision: (BigInt(previous.revision) + 1n).toString(),
      items: previous.items.filter((line) => line.productId !== productId),
    }));
  }

  return <main className="tv3-preview">
    <header className="tv3-preview__banner">
      <span>TV3 · DEMO FIXTURE · KHÔNG CÓ API</span>
      <h1>Giỏ hàng và Checkout COD</h1>
      <p>Chỉ xem trước component với dữ liệu mô phỏng. Thay đổi số lượng chỉ tồn tại trên trang này;
        không ghi vào database, không đặt hàng hay xác nhận thanh toán.</p>
    </header>
    <CartView state={{ status: 'ready', snapshot: cart }}
      onQuantityChange={changeQuantity} onRemove={remove} />
    <CheckoutCodView state={{ status: 'ready', cart, idempotencyKey: 'fixture-stable-key' }} />
    <footer className="tv3-preview__footer">Checkout thật cần TV1 Auth/API, TV2 Catalog, TV4 Inventory và TV5/M01 phê duyệt.</footer>
  </main>;
}

const root = document.getElementById('tv3-preview-root');
if (root) createRoot(root).render(<Preview />);
