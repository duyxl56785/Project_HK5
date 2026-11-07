import type { InventoryItem } from './inventoryTypes';

const quantity = /^\d+$/;

/**
 * Keeps the UI fail-closed when an API response is incomplete or has a negative
 * quantity. It does not coerce values through Number, preserving 64-bit values.
 */
export function readInventory(items: readonly InventoryItem[] | undefined): readonly InventoryItem[] | null {
  if (!items) return null;
  const seen = new Set<string>();
  for (const item of items) {
    if (!item || !item.sku || seen.has(item.sku)
      || !quantity.test(item.availableQuantity) || !quantity.test(item.reservedQuantity)) {
      return null;
    }
    seen.add(item.sku);
  }
  return items;
}

export function isOutOfStock(item: InventoryItem): boolean {
  return item.availableQuantity === '0';
}
