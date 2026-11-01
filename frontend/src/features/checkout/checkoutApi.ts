import { api, ApiError } from '../../shared/api';
import type { CheckoutResponse, CheckoutSubmission } from './checkoutTypes';

export interface Shipping { readonly name: string; readonly phone: string; readonly address: string }

/** Only a definite server answer may report success or rejection; anything else stays unknown. */
export function classify(error: unknown): CheckoutResponse {
  if (!(error instanceof ApiError)) return { status: 'unknown' };
  if (error.status === 401 || error.status === 403) return { status: 'rejected', reason: 'UNAUTHORIZED' };
  if (error.status !== 409) return { status: 'unknown' };
  if (['CHECKOUT_REQUIRES_RECONCILIATION', 'CHECKOUT_IN_PROGRESS_RETRY_LATER', 'IDEMPOTENCY_PAYLOAD_MISMATCH'].includes(error.message)) return { status: 'unknown' };
  if (error.message === 'FINANCE_POLICY_NOT_APPROVED') return { status: 'rejected', reason: 'CHECKOUT_PAUSED' };
  // shortcut: Order reports these as one BUSINESS_CONFLICT code, so the wording decides; give each its own code to drop this.
  const text = error.message.toLowerCase();
  if (text.includes('stock')) return { status: 'rejected', reason: 'OUT_OF_STOCK' };
  if (text.includes('price')) return { status: 'rejected', reason: 'PRICE_CHANGED' };
  if (text.includes('cart')) return { status: 'rejected', reason: 'CART_CHANGED' };
  return { status: 'rejected', reason: 'STORE_UNAVAILABLE' };
}

export async function placeOrder(request: CheckoutSubmission, shipping: Shipping): Promise<CheckoutResponse> {
  try {
    const placed = await api<{ orderId: string }>('/api/orders', {
      method: 'POST',
      headers: { 'Idempotency-Key': request.idempotencyKey },
      body: { expectedItems: request.expectedItems, expectedRevision: Number(request.expectedRevision), shipping },
    });
    return { status: 'confirmed', orderId: placed.orderId };
  } catch (error) {
    return classify(error);
  }
}

/** One key per Cart revision survives a reload, so resending reaches the same attempt. */
export function checkoutKey(revision: string): string {
  const slot = `checkout-key:${revision}`;
  const existing = sessionStorage.getItem(slot);
  if (existing) return existing;
  const key = crypto.randomUUID();
  sessionStorage.setItem(slot, key);
  return key;
}

export const forgetCheckoutKey = (revision: string) => sessionStorage.removeItem(`checkout-key:${revision}`);
