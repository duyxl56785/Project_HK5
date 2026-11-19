import { money } from '../../shared/ui';
import { minorUnits, renderMinorUnits } from '../cart/cartModel';
import type { StoreOrder } from './OrdersPage';

/** Refused Store orders leave the amount the customer owes. */
export function orderPayable(children: readonly Pick<StoreOrder, 'status' | 'grossExact' | 'gross'>[]): string {
  const open = children.filter((child) => child.status !== 'CANCELLED' && child.status !== 'REJECTED');
  const exact = open.map((child) => minorUnits(child.grossExact));
  if (exact.every((value) => value !== null))
    return renderMinorUnits(exact.reduce((sum, value) => sum + value, 0n));
  return money(open.reduce((sum, child) => sum + Number(child.gross), 0));
}
