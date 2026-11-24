import assert from 'node:assert/strict';
import test from 'node:test';
import { apiErrorText } from './apiErrors.ts';

test('known English refusals are shown in Vietnamese', () => {
  assert.equal(apiErrorText({ status: 409, code: 'PRODUCT_SKU_EXISTS', message: 'SKU already exists' }),
    'SKU này đã có trong cửa hàng. Hãy dùng SKU khác.');
  assert.equal(apiErrorText({ status: 400, code: 'VALIDATION_FAILED', message: 'Invalid profile update' }),
    'Thông tin chưa hợp lệ. Hãy kiểm tra lại các ô đã nhập.');
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
