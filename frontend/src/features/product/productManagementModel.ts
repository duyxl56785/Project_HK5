import { buildCategoryTree } from '../catalog/categoryTree.ts';
import type { CategoryDto } from '../catalog/categoryTypes.ts';
import { isCanonicalVersion } from '../seller/sellerStoreModel.ts';
import type { ProductFieldErrors, ProductFields, ProductManagementData, ProductStatus, ProductSummary, ValidProductFields } from './productManagementTypes.ts';

export const PRODUCT_STATUS_LABELS: Readonly<Record<ProductStatus, string>> = {
  DRAFT: 'Nháp', PENDING_REVIEW: 'Chờ duyệt', ACTIVE: 'Đang bán', REJECTED: 'Bị từ chối', SUSPENDED: 'Tạm khóa', INACTIVE: 'Ngừng bán',
};
// Java Character.isWhitespace: deliberately excludes NBSP, figure/narrow NBSP and BOM.
const javaWhitespace = /[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]/u;
const skuBoundary = /[\u0009-\u000d\u001c-\u0020\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000]/u;
function stripJava(value: string): string {
  let first = 0, last = value.length;
  while (first < last && javaWhitespace.test(value[first])) first++;
  while (last > first && javaWhitespace.test(value[last - 1])) last--;
  return value.slice(first, last);
}
function validUnicode(value: string): boolean {
  for (let i = 0; i < value.length; i++) {
    const code = value.charCodeAt(i);
    if (code === 0 || code >= 0xdc00 && code <= 0xdfff) return false;
    if (code >= 0xd800 && code <= 0xdbff) {
      const next = value.charCodeAt(++i);
      if (!(next >= 0xdc00 && next <= 0xdfff)) return false;
    }
  }
  return true;
}
const uuid = (value: unknown): value is string => typeof value === 'string' && value.length === 36 && /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(value);
const validPrice = (value: unknown): value is string => typeof value === 'string' && /^[1-9][0-9]{0,9}$/.test(value) && !/[^0-9]/.test(value) && BigInt(value) <= 1000000000n;
function record(value: unknown): value is Record<string, unknown> { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function validName(value: unknown): value is string { return typeof value === 'string' && validUnicode(value) && stripJava(value).length > 0 && stripJava(value).length <= 255; }
function validSku(value: unknown): value is string { return typeof value === 'string' && value.length > 0 && value.length <= 128 && validUnicode(value) && !skuBoundary.test(value[0]) && !skuBoundary.test(value[value.length - 1]); }
export const canEditProduct = (status: string): boolean => status === 'DRAFT' || status === 'REJECTED' || status === 'INACTIVE';
export function validateProductFields(fields: ProductFields, categories: readonly CategoryDto[]): { errors: ProductFieldErrors; value?: ValidProductFields } {
  const errors: ProductFieldErrors = {};
  if (!validName(fields.name)) errors.name = 'Nhập tên từ 1–255 ký tự (emoji có thể tính là 2), không chứa ký tự không hợp lệ.';
  if (!validSku(fields.sku)) errors.sku = 'SKU từ 1–128 ký tự (emoji có thể tính là 2); không có khoảng trắng ở đầu/cuối hoặc ký tự không hợp lệ.';
  if (!uuid(fields.categoryId) || !categories.some((category) => category.id === fields.categoryId)) errors.categoryId = 'Chọn một danh mục hiện có.';
  if (!validPrice(fields.unitPrice)) errors.unitPrice = 'Nhập số nguyên từ 1 đến 1.000.000.000, không dấu phân cách hoặc phần lẻ.';
  if (fields.currency !== 'VND') errors.currency = 'Chọn đơn vị tiền tệ VND.';
  return Object.keys(errors).length ? { errors } : { errors, value: { ...fields, name: stripJava(fields.name), currency: 'VND' } };
}
/** All-or-nothing consistency/privacy projection. Not authentication or authorization. */
export function readProductManagement(storeInput: unknown, productsInput: unknown, categoriesInput: unknown): ProductManagementData | null {
  if (!record(storeInput) || !uuid(storeInput.id) || !validName(storeInput.name) || !Array.isArray(productsInput)) return null;
  const tree = buildCategoryTree(categoriesInput);
  if (!tree.ok || tree.rows.some((row) => !uuid(row.id))) return null;
  const categories = tree.rows.map(({ id, name, parentId }) => ({ id, name, parentId }));
  const products: ProductSummary[] = [], ids = new Set<string>(), skus = new Set<string>();
  for (const item of productsInput) {
    if (!record(item) || !uuid(item.id) || ids.has(item.id) || item.storeId !== storeInput.id
      || !isCanonicalVersion(item.version) || typeof item.status !== 'string' || !Object.hasOwn(PRODUCT_STATUS_LABELS, item.status)
      || !validName(item.name) || stripJava(item.name) !== item.name || !validSku(item.sku) || skus.has(item.sku)
      || !validPrice(item.unitPrice) || item.currency !== 'VND' || !uuid(item.categoryId)
      || !categories.some((category) => category.id === item.categoryId)) return null;
    ids.add(item.id); skus.add(item.sku);
    products.push({ id: item.id, storeId: storeInput.id, categoryId: item.categoryId, sku: item.sku, name: item.name,
      unitPrice: item.unitPrice, currency: item.currency, status: item.status as ProductStatus, version: item.version });
  }
  return { store: { id: storeInput.id, name: storeInput.name }, products, categories };
}
export const formatProductPrice = (value: string): string => `${new Intl.NumberFormat('vi-VN').format(BigInt(value))} ₫`;
