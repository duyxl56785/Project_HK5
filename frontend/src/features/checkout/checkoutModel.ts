import { projectCart } from '../cart/cartModel.ts';
import type { CartSnapshot } from '../cart/cartTypes.ts';
import type { CheckoutSubmission } from './checkoutTypes.ts';

export function canonicalCheckoutKey(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0 && value.length <= 128
    && value === value.trim() && !/^\s|\s$/u.test(value);
}

/** No new key is ever minted here. Invalid/untrusted snapshots cannot produce a request. */
export function prepareCheckout(cart: CartSnapshot, key: string): CheckoutSubmission | null {
  if (!canonicalCheckoutKey(key)) return null;
  const projected = projectCart(cart);
  if (!projected || projected.claimed || projected.itemCount === 0) return null;
  return {
    idempotencyKey: key,
    expectedRevision: projected.revision,
    expectedItems: projected.groups.flatMap((group) =>
      group.lines.map((line) => ({ productId: line.productId, quantity: line.quantity }))),
  };
}

/** Gate keeps a single in-flight/uncertain attempt; never auto-retries a transient failure. */
export function createCheckoutOnce() {
  let status: 'idle' | 'sending' | 'unknown' | 'confirmed' | 'rejected' = 'idle';
  return {
    get status() { return status; },
    async send(run: () => Promise<'confirmed' | 'unknown' | 'rejected'>) {
      if (status !== 'idle') return 'blocked' as const;
      status = 'sending';
      try {
        const result = await run();
        status = result === 'confirmed' || result === 'rejected' ? result : 'unknown';
      } catch {
        status = 'unknown';
      }
      return status;
    },
  };
}
