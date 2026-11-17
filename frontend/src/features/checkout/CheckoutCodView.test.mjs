import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let server, CheckoutCodView, validateVoucherQuote, formatExactVnd, classify, ApiError;
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  ({ CheckoutCodView } = await server.ssrLoadModule('/src/features/checkout/CheckoutCodView.tsx'));
  ({ validateVoucherQuote, formatExactVnd, classify } = await server.ssrLoadModule('/src/features/checkout/checkoutApi.ts'));
  ({ ApiError } = await server.ssrLoadModule('/src/shared/api.ts'));
});
after(async () => { await server?.close(); });

const cart = { revision: '9007199254740993', claimed: false, items: [
  { productId: 'p1', storeId: 's1', sellerId: 'seller1', title: 'Áo', quantity: 2, unitPriceSnapshot: '10000.00' },
  { productId: 'p2', storeId: 's2', sellerId: 'seller2', title: 'Sách', quantity: 1, unitPriceSnapshot: '15000.00' },
] };
const render = (state, onConfirm) => renderToStaticMarkup(createElement(CheckoutCodView, { state, onConfirm }));

test('COD preview shows two Store groups, exact total and no assumed monetary policy', () => {
  const html = render({ status: 'ready', cart, idempotencyKey: 'fixed-key' });
  assert.match(html, /Thanh toán khi nhận hàng/);
  assert.match(html, /Cửa hàng: s1/); assert.match(html, /Cửa hàng: s2/);
  assert.match(html, /35\.000 ₫/);
  assert.match(html, /disabled=""/);
  assert.doesNotMatch(html, /Đã xác nhận đơn hàng|COD_PAID|VNPay/);
});
test('only an explicitly connected handler enables submit button; SSR makes no network call', () => {
  const html = render({ status: 'ready', cart, idempotencyKey: 'fixed-key' }, async () => ({ status: 'unknown' }));
  assert.match(html, /Xác nhận đặt hàng COD/);
  assert.doesNotMatch(html, /<button[^>]*disabled=/);
});
test('Claimed cart or malformed key cannot trigger checkout', () => {
  for (const state of [
    { status: 'ready', cart: { ...cart, claimed: true }, idempotencyKey: 'fixed-key' },
    { status: 'ready', cart, idempotencyKey: ' fixed-key' },
    { status: 'ready', cart: { ...cart, items: [] }, idempotencyKey: 'fixed-key' },
  ]) {
    assert.match(render(state, async () => ({ status: 'confirmed' })), /disabled=""/);
  }
});
test('UNKNOWN/reconciliation is prominent and cannot display a confirmed order', () => {
  const html = render({ status: 'unknown', cart, idempotencyKey: 'fixed-key' });
  assert.match(html, /Chưa xác định kết quả đặt hàng/);
  assert.doesNotMatch(html, /Backend đã xác nhận|s1|s2|fixed-key/);
});

const voucherCodes = [{ storeId: 's1', code: 'save10' }];
const quote = {
  revision: 7, total: '9007199254750992', stores: [
    { storeId: 's1', voucherCode: 'SAVE10', quoteFingerprint: 'a'.repeat(64),
      merchandiseAmount: '9007199254740993', discountAmount: '1', gross: '9007199254740992',
      platformFee: '450359962737050' },
    { storeId: 's2', voucherCode: null, quoteFingerprint: null,
      merchandiseAmount: '10000', discountAmount: '0', gross: '10000', platformFee: '500' },
  ],
};

test('C2 validates two Store totals exactly and retains a confirmed quote fingerprint', () => {
  assert.equal(validateVoucherQuote(quote, '7', voucherCodes).stores[0].quoteFingerprint, 'a'.repeat(64));
  assert.equal(formatExactVnd(quote.total), '9.007.199.254.750.992 ₫');
});

test('C2 refuses a stale, incomplete or inconsistent server quote', () => {
  assert.throws(() => validateVoucherQuote(quote, '8', voucherCodes));
  assert.throws(() => validateVoucherQuote({ ...quote, stores: quote.stores.slice(1) }, '7', voucherCodes));
  assert.throws(() => validateVoucherQuote({ ...quote, total: '9007199254750993' }, '7', voucherCodes));
  const first = { ...quote.stores[0], discountAmount: '0' };
  assert.throws(() => validateVoucherQuote({ ...quote, stores: [first, quote.stores[1]] }, '7', voucherCodes));
});

test('C2 shows definitive refusals and quarantines a timeout', () => {
  assert.equal(classify(new ApiError(409, 'VOUCHER_USAGE_LIMIT_REACHED', 'Quota')).reason, 'VOUCHER_REFUSED');
  assert.deepEqual(classify(new ApiError(503, 'VOUCHER_CHECKOUT_PENDING', 'Timeout')), { status: 'unknown' });
});
