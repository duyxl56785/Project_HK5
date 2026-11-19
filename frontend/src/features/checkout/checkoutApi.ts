import { api, ApiError } from '../../shared/api';
import { minorUnits } from '../cart/cartModel';
import type { CheckoutResponse, CheckoutSubmission, VoucherCode, VoucherQuote } from './checkoutTypes';

export interface Shipping { readonly name: string; readonly phone: string; readonly address: string }

const voucherMessages: Readonly<Record<string, string>> = {
  VOUCHER_NOT_FOUND: 'Không tìm thấy mã voucher của cửa hàng này.',
  VOUCHER_CHANGED: 'Mã đã hết hạn, tạm dừng hoặc báo giá đã đổi. Hãy áp dụng lại mã.',
  VOUCHER_ALREADY_USED: 'Tài khoản này đã dùng voucher.',
  VOUCHER_USAGE_LIMIT_REACHED: 'Voucher đã hết lượt.',
  VOUCHER_MIN_ORDER_AMOUNT: 'Tiền hàng chưa đạt mức tối thiểu của voucher.',
  VOUCHER_GROSS_TOO_LOW: 'Số tiền phải trả sau giảm phải từ 1 đồng.',
  VOUCHER_PROMOTION_CONFLICT: 'Cửa hàng này có sản phẩm đang khuyến mãi và không thể dùng chung voucher.',
  VOUCHER_PROMOTION_UNVERIFIED: 'Chưa xác minh được khuyến mãi hiện tại. Hãy thử lấy báo giá lại.',
  VOUCHER_STORE_MISMATCH: 'Mã không thuộc cửa hàng trong giỏ hàng.',
  VOUCHER_CART_CHANGED: 'Giỏ hàng đã thay đổi. Hãy tải lại giỏ.',
  VOUCHER_C2_DISABLED: 'Voucher tạm thời chưa khả dụng tại bước đặt hàng.',
  VOUCHER_CHECKOUT_NOT_PLACED: 'Đơn chưa được tạo. Hãy tải lại giỏ hàng và áp dụng lại mã.',
  VOUCHER_FINANCE_POLICY_UNAVAILABLE: 'Hệ thống chưa xác nhận được phí sàn cho đơn có voucher.',
};

export function voucherErrorMessage(error: unknown): string {
  return error instanceof ApiError && Object.hasOwn(voucherMessages, error.code)
    ? voucherMessages[error.code] : 'Chưa thể xác nhận báo giá voucher. Hãy thử lại.';
}

export function formatExactVnd(value: string): string {
  const minor = minorUnits(value);
  if (minor === null) return '—';
  const fractional = minor % 100n;
  return new Intl.NumberFormat('vi-VN').format(minor / 100n)
    + (fractional === 0n ? '' : ',' + fractional.toString().padStart(2, '0')) + ' ₫';
}

export function validateVoucherQuote(raw: VoucherQuote, revision: string, requested: readonly VoucherCode[]): VoucherQuote {
  if (!raw || !Number.isSafeInteger(raw.revision) || String(raw.revision) !== revision || !Array.isArray(raw.stores))
    throw new Error('Voucher quote does not match the Cart');
  const codes = new Map(requested.map(({ storeId, code }) => [storeId, code.toUpperCase()]));
  const stores = new Set<string>();
  let total = 0n;
  for (const store of raw.stores) {
    if (!store || typeof store.storeId !== 'string' || stores.has(store.storeId))
      throw new Error('Invalid Store quote');
    stores.add(store.storeId);
    const merchandise = minorUnits(store.merchandiseAmount);
    const discount = minorUnits(store.discountAmount);
    const gross = minorUnits(store.gross);
    const fee = minorUnits(store.platformFee);
    const code = codes.get(store.storeId);
    if (merchandise === null || discount === null || gross === null || fee === null
        || gross !== merchandise - discount || fee > gross
        || (code ? store.voucherCode !== code || gross < 100n
          || typeof store.quoteFingerprint !== 'string' || !/^[a-f0-9]{64}$/.test(store.quoteFingerprint)
          : store.voucherCode !== null || store.quoteFingerprint !== null || discount !== 0n))
      throw new Error('Invalid Store quote amount');
    if (code) codes.delete(store.storeId);
    total += gross;
  }
  if (stores.size === 0 || codes.size > 0 || minorUnits(raw.total) !== total)
    throw new Error('Invalid Checkout quote total');
  return raw;
}

export const voucherCapability = () => api<{ enabled: boolean }>('/api/orders/voucher-capability');

export async function quoteCheckout(request: CheckoutSubmission, codes: readonly VoucherCode[]): Promise<VoucherQuote> {
  const raw = await api<VoucherQuote>('/api/orders/voucher-quote', {
    method: 'POST',
    body: { expectedItems: request.expectedItems, expectedRevision: Number(request.expectedRevision), vouchers: codes },
  });
  return validateVoucherQuote(raw, request.expectedRevision, codes);
}

/** Only a definite server answer may report success or rejection; anything else stays unknown. */
export function classify(error: unknown): CheckoutResponse {
  if (!(error instanceof ApiError)) return { status: 'unknown' };
  if (error.status === 401 || error.status === 403) return { status: 'rejected', reason: 'UNAUTHORIZED' };
  if (error.status < 500 && Object.hasOwn(voucherMessages, error.code))
    return { status: 'rejected', reason: 'VOUCHER_REFUSED', voucherMessage: voucherMessages[error.code] };
  if (error.status !== 409) return { status: 'unknown' };
  if (['CHECKOUT_REQUIRES_RECONCILIATION', 'CHECKOUT_IN_PROGRESS_RETRY_LATER', 'IDEMPOTENCY_PAYLOAD_MISMATCH'].includes(error.message)) return { status: 'unknown' };
  if (error.message === 'FINANCE_POLICY_NOT_APPROVED') return { status: 'rejected', reason: 'CHECKOUT_PAUSED' };
  if (error.code === 'CHECKOUT_ITEMS_REJECTED') return { status: 'rejected', reason: 'ITEMS_REJECTED', items: error.items ?? [] };
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
      body: { expectedItems: request.expectedItems, expectedRevision: Number(request.expectedRevision), shipping,
        ...(request.vouchers?.length ? { vouchers: request.vouchers } : {}) },
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
