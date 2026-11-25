import assert from 'node:assert/strict';
import test from 'node:test';
import { apiErrorText } from './apiErrors.ts';

test('known English refusals are shown in Vietnamese', () => {
  assert.equal(apiErrorText({ status: 409, code: 'PRODUCT_SKU_EXISTS', message: 'SKU already exists' }),
    'SKU này đã có trong cửa hàng. Hãy dùng SKU khác.');
  assert.equal(apiErrorText({ status: 400, code: 'VALIDATION_FAILED', message: 'Invalid profile update' }),
    'Thông tin chưa hợp lệ. Hãy kiểm tra lại các ô đã nhập.');
});

test('a refused review does not claim it was already reviewed, since the code also means "not delivered yet"', () => {
  const text = apiErrorText({ status: 409, code: 'REVIEW_NOT_ALLOWED', message: 'Review requires a purchased product in a delivered order' });
  assert.doesNotMatch(text, /đã đánh giá/i);
  assert.match(text, /đã giao/);
});

test('a settlement held by a Return says so, not "the data changed, reload"', () => {
  const text = apiErrorText({ status: 409, code: 'SETTLEMENT_RETURN_OPEN', message: 'A pending or received Return blocks settlement' });
  assert.match(text, /trả hàng/);
  assert.doesNotMatch(text, /tải lại/);
});

test('promotion schedule rules say which rule was broken', () => {
  const text = (message) => apiErrorText({ status: 400, code: 'VALIDATION_FAILED', message });
  assert.match(text('Flash Sale date must be Saturday'), /thứ Bảy/);
  assert.match(text('Campaign must start in the future'), /tương lai/);
  assert.match(text('Clearance duration must be between one hour and 30 days'), /1 giờ.*30 ngày/);
});

test('an unknown English refusal falls back to a Vietnamese message for its status', () => {
  assert.match(apiErrorText({ status: 404, code: 'SOMETHING_NEW', message: 'Thing not found' }), /^Không tìm thấy/);
  assert.match(apiErrorText({ status: 503, code: 'X_UNAVAILABLE', message: 'Unavailable' }), /^Hệ thống đang bận/);
  assert.match(apiErrorText({ status: 418, code: 'X', message: 'Teapot' }), /^Yêu cầu không thành công/);
});

test('a message the server already wrote in Vietnamese is kept as it is', () => {
  const message = 'Bạn thao tác quá nhiều lần. Vui lòng thử lại sau 2 phút.';
  assert.equal(apiErrorText({ status: 429, code: 'RATE_LIMITED', message }), message);
  assert.equal(apiErrorText({ status: 400, code: 'VALIDATION_FAILED', message: 'Lý do hủy quá dài' }), 'Lý do hủy quá dài');
});

test('an English message with typographic punctuation is not taken for Vietnamese', () => {
  // Curly quotes, dashes, ellipses and the ₫ sign are outside ASCII but appear in English messages too.
  assert.match(apiErrorText({ status: 409, code: 'SOMETHING_NEW', message: 'Can’t update – the item changed…' }), /^Dữ liệu vừa thay đổi/);
  assert.equal(apiErrorText({ status: 400, code: 'VALIDATION_FAILED', message: '“Price” must be at least 1000 ₫' }),
    'Thông tin chưa hợp lệ. Hãy kiểm tra lại các ô đã nhập.');
});

test('a Vietnamese message written without tone marks on its first words is still kept', () => {
  assert.equal(apiErrorText({ status: 400, code: 'VALIDATION_FAILED', message: 'Đơn hàng không hợp lệ' }), 'Đơn hàng không hợp lệ');
  assert.equal(apiErrorText({ status: 400, code: 'VALIDATION_FAILED', message: 'San pham het hang, vui lòng thử lại' }),
    'San pham het hang, vui lòng thử lại');
});
