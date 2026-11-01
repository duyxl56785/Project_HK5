import { Link } from 'react-router-dom';
import { api, ApiError } from '../../shared/api';
import { ErrorNote, useAction } from '../../shared/ui';
import { CartView } from './CartView';
import { useCart } from './cartApi';

export function CartPage() {
  const cart = useCart();
  const change = useAction(({ productId, quantity }: { productId: string; quantity: number }) =>
    api(`/api/cart/items/${productId}`, { method: 'PATCH', body: { quantity } }), ['cart']);
  const remove = useAction((productId: string) => api(`/api/cart/items/${productId}`, { method: 'DELETE' }), ['cart']);

  if (cart.isPending) return <CartView state={{ status: 'loading' }} />;
  if (cart.error) return <CartView onRetry={() => void cart.refetch()}
    state={{ status: cart.error instanceof ApiError && cart.error.status === 503 ? 'unavailable' : 'error' }} />;
  const { snapshot, storeNames } = cart.data;
  return <div className="stack">
    <CartView state={{ status: 'ready', snapshot }} storeNames={storeNames}
      onQuantityChange={(productId, quantity) => change.mutate({ productId, quantity })}
      onRemove={(productId) => remove.mutate(productId)} />
    <ErrorNote error={change.error ?? remove.error} />
    {snapshot.items.length > 0 && !snapshot.claimed && <Link className="btn btn--primary" to="/checkout">Tiến hành đặt hàng</Link>}
    {snapshot.items.length === 0 && <Link to="/">Tiếp tục mua sắm</Link>}
  </div>;
}
