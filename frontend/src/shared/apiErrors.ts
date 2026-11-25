/**
 * Vietnamese text for a refused API call. Many services answer in English ("SKU already exists"),
 * so the code picks a Vietnamese message, then the HTTP status does. A message the server already
 * wrote in Vietnamese is shown as it is.
 */
const byCode: Readonly<Record<string, string>> = {
  PRODUCT_SKU_EXISTS: 'SKU này đã có trong cửa hàng. Hãy dùng SKU khác.',
  PRODUCT_SKU_CONFLICT: 'SKU này đã có trong cửa hàng. Hãy dùng SKU khác.',
  VALIDATION_FAILED: 'Thông tin chưa hợp lệ. Hãy kiểm tra lại các ô đã nhập.',
  VALIDATION_ERROR: 'Thông tin chưa hợp lệ. Hãy kiểm tra lại các ô đã nhập.',
  INVALID_REQUEST: 'Thông tin chưa hợp lệ. Hãy kiểm tra lại các ô đã nhập.',
  PRODUCT_INVALID_INPUT: 'Thông tin sản phẩm chưa hợp lệ. Hãy kiểm tra lại các ô đã nhập.',
  AUTH_INVALID_CREDENTIALS: 'Email hoặc mật khẩu không đúng.',
  AUTH_EMAIL_UNAVAILABLE: 'Không thể đăng ký bằng email này.',
  AUTH_ACCOUNT_PROTECTED: 'Không thể khóa tài khoản quản trị.',
  AUTH_RATE_LIMITED: 'Bạn thao tác quá nhiều lần. Vui lòng thử lại sau ít phút.',
  IMAGE_COUNT_EXCEEDED: 'Mỗi sản phẩm có tối đa 5 ảnh.',
  USER_ADDRESS_LIMIT: 'Bạn chỉ lưu được tối đa 10 địa chỉ.',
  INSUFFICIENT_STOCK: 'Không đủ hàng trong kho.',
  RETURN_ALREADY_OPEN: 'Đơn này đã có yêu cầu trả hàng đang xử lý.',
  RETURN_NOT_ELIGIBLE: 'Đơn này không còn đủ điều kiện trả hàng.',
  // The service sends this code both before delivery and after a review, so the text covers both.
  REVIEW_NOT_ALLOWED: 'Chưa gửi được nhận xét. Chỉ nhận xét được khi đơn đã giao, và mỗi sản phẩm trong đơn chỉ nhận xét một lần.',
  PROMOTION_OVERLAP: 'Đã có chương trình khuyến mãi khác trùng thời gian.',
  PROMOTION_SCHEDULE_CONFLICT: 'Khung thời gian này đã có chương trình khác.',
  PROMOTION_PRICE_CONFLICT: 'Giá khuyến mãi phải thấp hơn giá gốc.',
  SELLER_NOT_APPROVED: 'Cửa hàng chưa được duyệt.',
  SELLER_SELF_MODERATION: 'Quản trị viên không thể tự duyệt cửa hàng của mình.',
  STORE_CLOSED: 'Cửa hàng đang tạm đóng.',
  VOUCHER_CODE_EXISTS: 'Mã voucher này đã có trong cửa hàng.',
  VOUCHER_QUOTA_EXCEEDED: 'Cửa hàng đã dùng hết hạn mức voucher.',
  VOUCHER_LIMIT_REQUEST_PENDING_EXISTS: 'Đã có một yêu cầu đang chờ duyệt.',
  VOUCHER_LIMIT_REQUEST_INVALID_STATE: 'Yêu cầu này không còn ở trạng thái chờ duyệt.',
  SETTLEMENT_RETURN_OPEN: 'Chưa thanh toán được cho cửa hàng: đơn đang có yêu cầu trả hàng chưa đóng (chờ duyệt, đã duyệt hoặc đã nhận hàng trả).',
};

// Promotion rules all answer VALIDATION_FAILED, so only the message says which rule was broken.
const byMessage: Readonly<Record<string, string>> = {
  'Flash Sale date must be Saturday': 'Flash Sale chỉ chạy vào thứ Bảy, từ 22:00 đến 24:00. Hãy chọn một ngày thứ Bảy.',
  'Campaign must start in the future': 'Chương trình phải bắt đầu trong tương lai.',
  'Clearance duration must be between one hour and 30 days': 'Chương trình xả kho phải kéo dài từ 1 giờ đến 30 ngày.',
};

const byStatus = (status: number) =>
  status === 400 || status === 422 ? 'Thông tin chưa hợp lệ. Hãy kiểm tra lại các ô đã nhập.'
  : status === 401 ? 'Phiên đăng nhập đã hết. Hãy đăng nhập lại.'
  : status === 403 ? 'Bạn không có quyền thực hiện thao tác này.'
  : status === 404 ? 'Không tìm thấy dữ liệu. Có thể dữ liệu đã bị xóa hoặc thay đổi.'
  : status === 409 ? 'Dữ liệu vừa thay đổi hoặc không cho phép thao tác này. Hãy tải lại trang rồi thử lại.'
  : status >= 500 ? 'Hệ thống đang bận. Vui lòng thử lại sau.'
  : 'Yêu cầu không thành công. Vui lòng thử lại.';

// A Vietnamese letter (đ, or a vowel carrying a Vietnamese tone or vowel mark) means the server already
// answered in Vietnamese. Curly quotes, dashes, "…" and "₫" are outside ASCII too, but English messages use them.
const isVietnamese = (message: string) => /[đĐ]|[aeiouyAEIOUY][̛̣̀́̂̃̆̉]/.test(message.normalize('NFD'));

export function apiErrorText({ status, code, message }: { readonly status: number; readonly code: string; readonly message: string }) {
  if (isVietnamese(message)) return message;
  if (Object.hasOwn(byMessage, message)) return byMessage[message];
  return Object.hasOwn(byCode, code) ? byCode[code] : byStatus(status);
}
