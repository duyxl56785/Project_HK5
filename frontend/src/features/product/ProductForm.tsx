import { useId, useRef, useState } from 'react';
import { buildCategoryTree } from '../catalog/categoryTree.ts';
import type { CategoryDto } from '../catalog/categoryTypes.ts';
import { validateProductFields } from './productManagementModel.ts';
import type { ProductFieldErrors, ProductFields, ProductSummary, ValidProductFields } from './productManagementTypes.ts';

export interface ProductFormProps {
  readonly product?: ProductSummary;
  readonly categories: readonly CategoryDto[];
  readonly busy: boolean;
  readonly canSave: boolean;
  readonly onSave: (fields: ValidProductFields) => void;
  readonly onCancel: () => void;
}
export function ProductForm({ product, categories, busy, canSave, onSave, onCancel }: ProductFormProps) {
  const id = useId(), summary = useRef<HTMLDivElement>(null);
  const [fields, setFields] = useState<ProductFields>(() => product ? { name: product.name, sku: product.sku, categoryId: product.categoryId, unitPrice: product.unitPrice, currency: product.currency } : { name: '', sku: '', categoryId: '', unitPrice: '', currency: '' });
  const [errors, setErrors] = useState<ProductFieldErrors>({});
  const tree = buildCategoryTree(categories);
  const fieldId = (field: keyof ProductFields) => `${id}-${field}`;
  function update(field: keyof ProductFields, value: string) {
    setFields((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }
  function checkField(field: keyof ProductFields) {
    setErrors((current) => ({ ...current, [field]: validateProductFields(fields, categories).errors[field] }));
  }
  const describedBy = (field: keyof ProductFields) => `${fieldId(field)}-help${errors[field] ? ` ${fieldId(field)}-error` : ''}`;
  const error = (field: keyof ProductFields) => errors[field] && <p className="tv2-product__field-error" id={`${fieldId(field)}-error`}>{errors[field]}</p>;
  return <form className="tv2-product__form" noValidate aria-labelledby={`${id}-title`} aria-busy={busy} onSubmit={(event) => {
    event.preventDefault();
    if (busy) return;
    const result = validateProductFields(fields, categories);
    setErrors(result.errors);
    if (!result.value) { requestAnimationFrame(() => summary.current?.focus()); return; }
    if (canSave) onSave(result.value);
  }}>
    <div className="tv2-product__form-heading"><div><p className="tv2-product__eyebrow">THÔNG TIN SẢN PHẨM</p><h3 id={`${id}-title`}>{product ? 'Chỉnh sửa sản phẩm' : 'Thêm sản phẩm'}</h3></div><span className="tv2-product__subtle">{product ? 'Giữ nguyên SKU' : 'Tạo ở trạng thái Nháp'}</span></div>
    {Object.values(errors).some(Boolean) && <div ref={summary} tabIndex={-1} role="alert" className="tv2-product__alert"><strong>Kiểm tra lại thông tin.</strong><ul>{Object.entries(errors).filter(([, message]) => message).map(([field, message]) => <li key={field}><a href={`#${fieldId(field as keyof ProductFields)}`}>{message}</a></li>)}</ul></div>}
    {!canSave && <p className="tv2-product__notice">Hiện chưa thể lưu. Cần kết nối API được bảo vệ và dữ liệu mới nhất. Bạn có thể nhập và kiểm tra thông tin; dữ liệu chưa được lưu.</p>}
    {product && <p className="tv2-product__subtle">Phiên bản đang sửa: <code>{product.version}</code></p>}
    <fieldset disabled={busy}>
      <div className="tv2-product__fields">
        <div className="tv2-product__field tv2-product__field--wide"><label htmlFor={fieldId('name')}>Tên sản phẩm <span aria-hidden="true">*</span></label><input id={fieldId('name')} name="name" value={fields.name} required autoFocus onChange={(event) => update('name', event.target.value)} onBlur={() => checkField('name')} aria-invalid={!!errors.name} aria-describedby={describedBy('name')} /><p id={`${fieldId('name')}-help`}>Tối đa 255 ký tự; emoji có thể tính là 2. Bỏ khoảng trắng ở đầu/cuối theo quy tắc tên.</p>{error('name')}</div>
        <div className="tv2-product__field"><label htmlFor={fieldId('sku')}>SKU <span aria-hidden="true">*</span></label><input id={fieldId('sku')} name="sku" value={fields.sku} required readOnly={!!product} spellCheck={false} autoCapitalize="off" autoCorrect="off" onChange={(event) => update('sku', event.target.value)} onBlur={() => checkField('sku')} aria-invalid={!!errors.sku} aria-describedby={describedBy('sku')} /><p id={`${fieldId('sku')}-help`}>{product ? 'SKU cố định, không thể thay đổi.' : 'Mã duy nhất toàn hệ thống, tối đa 128 ký tự; emoji có thể tính là 2. Giữ nguyên hoa/thường.'}</p>{error('sku')}</div>
        <div className="tv2-product__field"><label htmlFor={fieldId('categoryId')}>Danh mục <span aria-hidden="true">*</span></label><select id={fieldId('categoryId')} name="categoryId" required value={fields.categoryId} onChange={(event) => update('categoryId', event.target.value)} onBlur={() => checkField('categoryId')} aria-invalid={!!errors.categoryId} aria-describedby={describedBy('categoryId')}><option value="">Chọn danh mục</option>{tree.ok && tree.rows.map((category) => <option key={category.id} value={category.id}>{'— '.repeat(category.depth - 1)}{category.name}</option>)}</select><p id={`${fieldId('categoryId')}-help`}>{categories.length ? 'Chọn từ danh mục hiện có.' : 'Chưa có danh mục. Cần tải danh mục trước khi lưu.'}</p>{error('categoryId')}</div>
        <div className="tv2-product__field"><label htmlFor={fieldId('unitPrice')}>Đơn giá <span aria-hidden="true">*</span></label><input id={fieldId('unitPrice')} name="unitPrice" type="text" inputMode="numeric" required value={fields.unitPrice} onChange={(event) => update('unitPrice', event.target.value)} onBlur={() => checkField('unitPrice')} aria-invalid={!!errors.unitPrice} aria-describedby={describedBy('unitPrice')} /><p id={`${fieldId('unitPrice')}-help`}>1–1.000.000.000 VND. Nhập số nguyên, ví dụ 125000.</p>{error('unitPrice')}</div>
        <div className="tv2-product__field"><label htmlFor={fieldId('currency')}>Tiền tệ <span aria-hidden="true">*</span></label><select id={fieldId('currency')} name="currency" required value={fields.currency} onChange={(event) => update('currency', event.target.value)} onBlur={() => checkField('currency')} aria-invalid={!!errors.currency} aria-describedby={describedBy('currency')}><option value="">Chọn tiền tệ</option><option value="VND">VND — Đồng Việt Nam</option></select><p id={`${fieldId('currency')}-help`}>Chỉ hỗ trợ VND; không quy đổi hoặc làm tròn giá.</p>{error('currency')}</div>
      </div>
    </fieldset>
    <div className="tv2-product__actions"><button type="submit" disabled={busy || !canSave || !categories.length}>{busy ? 'Đang lưu…' : product ? 'Lưu thay đổi' : 'Lưu bản nháp'}</button><button type="button" className="tv2-product__secondary" disabled={busy} onClick={onCancel}>Hủy</button><span className="tv2-product__subtle">Các trường có * là bắt buộc.</span></div>
  </form>;
}
