import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { useAuth } from '../../shared/auth';
import type { CartSnapshot } from './cartTypes';

interface WireItem { readonly productId: string; readonly storeId: string; readonly sellerId: string; readonly quantity: number; readonly unitPriceSnapshot: number }
interface WireCart { readonly revision: number; readonly claimed: boolean; readonly items: readonly WireItem[] }

export interface LoadedCart { readonly snapshot: CartSnapshot; readonly storeNames: Readonly<Record<string, string>> }

/** The Cart API has ids only; titles and Store names are joined from public read endpoints. */
async function loadCart(): Promise<LoadedCart> {
  const cart = await api<WireCart>('/api/cart');
  const ids = cart.items.map((item) => item.productId);
  const [names, stores] = ids.length === 0 ? [[], []] : await Promise.all([
    api<{ id: string; name: string }[]>(`/api/catalog/products/names?ids=${ids.join(',')}`),
    api<{ id: string; name: string }[]>('/api/stores'),
  ]);
  const titles = new Map(names.map((n) => [n.id, n.name]));
  return {
    storeNames: Object.fromEntries(stores.map((s) => [s.id, s.name])),
    snapshot: {
      // shortcut: JSON numbers lose exactness above 2^53, safe while VND prices and revisions stay below it; upgrade by serializing them as strings in the Cart API.
      revision: String(cart.revision),
      claimed: cart.claimed,
      items: cart.items.map((item) => ({
        productId: item.productId, storeId: item.storeId, sellerId: item.sellerId, quantity: item.quantity,
        title: titles.get(item.productId) ?? 'Sản phẩm', unitPriceSnapshot: String(item.unitPriceSnapshot),
      })),
    },
  };
}

export function useCart() {
  const { has } = useAuth();
  return useQuery({ queryKey: ['cart'], queryFn: loadCart, enabled: has('CUSTOMER') });
}
