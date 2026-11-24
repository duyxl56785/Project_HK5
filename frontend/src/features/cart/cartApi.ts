import { useQueries, useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { useAuth } from '../../shared/auth';
import type { CartSnapshot } from './cartTypes';

interface WireItem { readonly productId: string; readonly storeId: string; readonly sellerId: string; readonly quantity: number; readonly unitPriceSnapshot: number }
interface WireCart { readonly revision: number; readonly claimed: boolean; readonly items: readonly WireItem[] }
interface ProductName { readonly id: string; readonly name: string }

export interface LoadedCart { readonly snapshot: CartSnapshot; readonly storeNames: Readonly<Record<string, string>> }

const CATALOG_NAMES_BATCH_SIZE = 100;

async function loadProductNames(ids: readonly string[]): Promise<ProductName[]> {
  const names: ProductName[] = [];
  for (let offset = 0; offset < ids.length; offset += CATALOG_NAMES_BATCH_SIZE) {
    const batch = ids.slice(offset, offset + CATALOG_NAMES_BATCH_SIZE);
    names.push(...await api<ProductName[]>(`/api/catalog/products/names?ids=${batch.join(',')}`));
  }
  return names;
}

/** The Cart API has ids only; titles and Store names are joined from public read endpoints. */
async function loadCart(): Promise<LoadedCart> {
  const cart = await api<WireCart>('/api/cart');
  const ids = cart.items.map((item) => item.productId);
  const [names, stores] = ids.length === 0 ? [[], []] : await Promise.all([
    loadProductNames(ids),
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

/** Most carts hold a few lines; past this many, stock is left to Checkout to keep the page light. */
const STOCK_LOOKUP_LIMIT = 30;

/**
 * Units in stock per Product, read from the public Product page data. The Cart API does not check
 * stock, so this lets the Cart warn early; Checkout still refuses a quantity above stock.
 */
export function useCartStock(productIds: readonly string[]): Readonly<Record<string, number>> {
  const ids = productIds.length <= STOCK_LOOKUP_LIMIT ? productIds : [];
  const results = useQueries({ queries: ids.map((id) => ({
    queryKey: ['product-stock', id],
    queryFn: () => api<{ available: number }>(`/api/catalog/products/${id}`),
    select: (product: { available: number }) => product.available,
    retry: false,
  })) });
  return Object.fromEntries(ids.flatMap((id, i) => results[i].data === undefined ? [] : [[id, results[i].data]]));
}
