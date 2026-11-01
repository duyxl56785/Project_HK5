import { z } from 'zod';

/** Rules the browser can check before calling the API. The server stays the authority. */
export const text = (label: string, max: number) => z.string().trim()
  .min(1, `Nhập ${label.toLowerCase()}`).max(max, `${label} tối đa ${max} ký tự`);
export const optionalText = (label: string, max: number) => z.string().trim().max(max, `${label} tối đa ${max} ký tự`);

/** E.164, as User stores it. */
export const internationalPhone = z.string().trim().regex(/^\+[1-9][0-9]{1,14}$/, 'Số điện thoại dạng +84…');
export const contactPhone = z.string().trim().regex(/^\+?[0-9]{8,15}$/, 'Số điện thoại gồm 8–15 chữ số, có thể bắt đầu bằng +');
export const shippingPhone = z.string().trim().regex(/^\+?[0-9 .-]{8,20}$/, 'Số điện thoại không hợp lệ');

export const wholeNumber = (label: string, min: number, max: number) => z.string().trim().refine((value) => {
  const number = Number(value);
  return value !== '' && Number.isInteger(number) && number >= min && number <= max;
}, `${label} là số nguyên từ ${min} đến ${max}`);

export const contactSchema = z.object({
  representativeName: text('Tên người đại diện', 100),
  phone: contactPhone,
  pickupAddress: text('Địa chỉ lấy hàng', 255),
});

export const storeSchema = z.object({
  name: text('Tên cửa hàng', 255),
  slug: text('Đường dẫn', 128).regex(/^[a-z0-9]+(-[a-z0-9]+)*$/, 'Chỉ dùng chữ thường, số và dấu gạch ngang'),
});

export const productSchema = z.object({
  categoryId: z.string().min(1, 'Chọn danh mục'),
  sku: text('SKU', 128),
  name: text('Tên sản phẩm', 255),
  unitPrice: wholeNumber('Giá', 1, 99_999_999_999_999),
  description: optionalText('Mô tả', 5000),
  initialStock: wholeNumber('Tồn kho', 0, 1_000_000),
});

export const shippingSchema = z.object({
  name: text('Người nhận', 120),
  phone: shippingPhone,
  address: text('Địa chỉ', 500),
});
