import { useRef, useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { useForm, type Resolver } from 'react-hook-form';
import { Link, useNavigate, useParams } from 'react-router-dom';
import type { z } from 'zod';
import { api } from '../../shared/api';
import { productSchema } from '../../shared/forms';
import { Badge, ErrorNote, Field, Loaded, money, StatusFilter, useAction } from '../../shared/ui';
import { useCategories } from '../catalog/CatalogPage';
import { baseName, variantLabel } from '../catalog/variantModel';
import { buildRows, MAX_VARIANTS, rowErrors, SIZE_SUGGESTIONS, splitOptions, toVariantBody, type VariantRow } from './variantDraft';

export interface Product {
  readonly id: string; readonly categoryId: string; readonly sku: string; readonly name: string;
  readonly unitPrice: string; readonly status: string; readonly version: string;
}
interface VariantInfo { readonly groupId: string; readonly position: number; readonly size: string | null; readonly color: string | null }
interface GroupRow {
  readonly id: string; readonly sku: string; readonly name: string; readonly size: string | null; readonly color: string | null;
  readonly unitPrice: string; readonly status: string; readonly version: string; readonly available: number | null;
}
interface SellerProduct {
  readonly product: Product; readonly description: string; readonly available: number | null; readonly reserved: number | null;
  readonly images: readonly { readonly id: string; readonly url: string }[];
  /** Null for a Product without size/color; variants lists the whole group, first variant first. */
  readonly variant?: VariantInfo | null; readonly variants?: readonly GroupRow[];
}
type ProductInput = z.infer<typeof productSchema>;
const STATUSES = ['DRAFT', 'PENDING_REVIEW', 'ACTIVE', 'REJECTED', 'SUSPENDED', 'INACTIVE'];
// With size/color each variant carries its own SKU, price and stock, so the form checks only the shared fields.
const fullResolver = zodResolver(productSchema);
const groupResolver = zodResolver(productSchema.pick({ categoryId: true, name: true, description: true })) as unknown as Resolver<ProductInput>;

export function SellerProductsPage() {
  const [status, setStatus] = useState('');
  const list = useQuery({
    queryKey: ['seller-products', status],
    queryFn: () => api<{ products: Product[] }>(`/api/seller/products?size=100${status && `&status=${status}`}`),
  });
  return <div className="stack">
    <div className="row row--between"><h1>Sản phẩm của cửa hàng</h1><Link className="btn btn--primary" to="/seller/products/new">Thêm sản phẩm</Link></div>
    <StatusFilter value={status} options={STATUSES} onChange={setStatus} allLabel="Tất cả" />
    <Loaded query={list} empty={(data) => data.products.length === 0}>{(data) => <table className="table" tabIndex={0}>
      <thead><tr><th>Tên</th><th>SKU</th><th>Giá</th><th>Trạng thái</th></tr></thead>
      <tbody>{data.products.map((p) => <tr key={p.id}>
        <td><Link to={`/seller/products/${p.id}`}>{p.name}</Link></td><td>{p.sku}</td><td>{money(p.unitPrice)}</td><td><Badge value={p.status} /></td>
      </tr>)}</tbody></table>}</Loaded>
  </div>;
}

/** Size/color table for a new Product: generate every pair, then adjust each SKU, price and stock. */
function VariantEditor({ rows, onRows, showErrors }: {
  readonly rows: readonly VariantRow[]; readonly onRows: (rows: VariantRow[]) => void; readonly showErrors: boolean;
}) {
  const [sizes, setSizes] = useState('');
  const [colors, setColors] = useState('');
  const [prefix, setPrefix] = useState('');
  const [price, setPrice] = useState('');
  const [tooMany, setTooMany] = useState(false);
  const errors = rowErrors(rows);
  const change = (index: number, field: 'sku' | 'unitPrice' | 'initialStock', value: string) =>
    onRows(rows.map((row, i) => i === index ? { ...row, [field]: value } : row));
  const generate = () => {
    const next = buildRows(splitOptions(sizes), splitOptions(colors), prefix, price);
    setTooMany(next.length > MAX_VARIANTS);
    if (next.length <= MAX_VARIANTS) onRows(next);
  };
  return <fieldset className="stack">
    <legend>Size và màu</legend>
    <div className="row row--wrap">
      <label className="field">Các size, cách nhau bằng dấu phẩy
        <input value={sizes} placeholder={SIZE_SUGGESTIONS} onChange={(e) => setSizes(e.currentTarget.value)} /></label>
      <label className="field">Các màu, cách nhau bằng dấu phẩy
        <input value={colors} placeholder="Đen, Trắng" onChange={(e) => setColors(e.currentTarget.value)} /></label>
    </div>
    <div className="row row--wrap">
      <label className="field">Tiền tố SKU<input value={prefix} placeholder="AO-THUN" onChange={(e) => setPrefix(e.currentTarget.value)} /></label>
      <label className="field">Giá chung (VND)<input inputMode="numeric" value={price} onChange={(e) => setPrice(e.currentTarget.value)} /></label>
    </div>
    <button type="button" className="btn" onClick={generate}>Tạo bảng biến thể</button>
    {tooMany && <p className="note note--error" role="alert">Tối đa {MAX_VARIANTS} biến thể.</p>}
    {showErrors && rows.length === 0 && <p className="note note--error" role="alert">Nhập size hoặc màu rồi bấm “Tạo bảng biến thể”.</p>}
    {rows.length > 0 && <table className="table" tabIndex={0}>
      <thead><tr><th>Biến thể</th><th>SKU</th><th>Giá (VND)</th><th>Tồn kho</th><th /></tr></thead>
      <tbody>{rows.map((row, index) => {
        const label = variantLabel(row.size, row.color);
        const error = showErrors ? errors[index] : {};
        return <tr key={label}>
          <td>{label}</td>
          <td><input aria-label={`SKU ${label}`} value={row.sku} onChange={(e) => change(index, 'sku', e.currentTarget.value)} />
            {error.sku && <small className="field__error" role="alert">{error.sku}</small>}</td>
          <td><input aria-label={`Giá ${label}`} inputMode="numeric" value={row.unitPrice} onChange={(e) => change(index, 'unitPrice', e.currentTarget.value)} />
            {error.unitPrice && <small className="field__error" role="alert">{error.unitPrice}</small>}</td>
          <td><input aria-label={`Tồn kho ${label}`} inputMode="numeric" value={row.initialStock} onChange={(e) => change(index, 'initialStock', e.currentTarget.value)} />
            {error.initialStock && <small className="field__error" role="alert">{error.initialStock}</small>}</td>
          <td><button type="button" className="btn" aria-label={`Bỏ ${label}`} onClick={() => onRows(rows.filter((_, i) => i !== index))}>Bỏ</button></td>
        </tr>;
      })}</tbody></table>}
  </fieldset>;
}

function ProductForm({ current }: { readonly current?: SellerProduct }) {
  const navigate = useNavigate();
  const categories = useCategories();
  const p = current?.product;
  const variant = current?.variant ?? null;
  // Name, category and description belong to the first variant; the others change only price and stock.
  const member = !!variant && variant.position > 1;
  const [withVariants, setWithVariants] = useState(false);
  const [rows, setRows] = useState<VariantRow[]>([]);
  const [checked, setChecked] = useState(false);
  const grouped = useRef(false);
  grouped.current = !p && withVariants;
  const form = useForm<ProductInput>({
    resolver: (values, context, options) => (grouped.current ? groupResolver : fullResolver)(values, context, options),
    defaultValues: {
      categoryId: p?.categoryId ?? '', sku: p?.sku ?? '', unitPrice: p?.unitPrice ?? '',
      name: p ? (variant ? baseName(p.name, variant.size, variant.color) : p.name) : '',
      description: current?.description ?? '', initialStock: '0',
    },
  });
  const { errors } = form.formState;
  const save = useAction(({ categoryId, sku, name, unitPrice, description, initialStock }: ProductInput) => {
    if (p) return api<SellerProduct>(`/api/seller/products/${p.id}`, { method: 'PUT', body: member
      ? { categoryId: p.categoryId, name, unitPrice, expectedVersion: p.version }
      : { categoryId, name, unitPrice, description, expectedVersion: p.version } });
    if (withVariants) return api<SellerProduct>('/api/seller/products', { method: 'POST', body: { categoryId, name, description, variants: toVariantBody(rows) } });
    return api<SellerProduct>('/api/seller/products', { method: 'POST', body: { categoryId, sku, name, unitPrice, description, initialStock: Number(initialStock) } });
  }, ['seller-products'], (saved) => { if (!p) navigate(`/seller/products/${saved.product.id}`, { replace: true }); });
  const submit = form.handleSubmit((value) => {
    if (grouped.current) {
      setChecked(true);
      if (rows.length === 0 || rowErrors(rows).some((e) => Object.keys(e).length > 0)) return;
    }
    save.mutate(value);
  });
  return <form className="panel stack" noValidate onSubmit={submit}>
    <h2>Thông tin sản phẩm</h2>
    {member && <p className="muted">Tên, danh mục và mô tả sửa ở <Link to={`/seller/products/${variant.groupId}`}>biến thể đầu tiên</Link>. Ở đây chỉ đổi giá và tồn kho.</p>}
    <Field label="Danh mục" error={errors.categoryId}><select {...form.register('categoryId')} disabled={member}>
      <option value="">Chọn danh mục…</option>
      {p && categories.data && !categories.data.some((c) => c.id === p.categoryId) && <option value={p.categoryId}>Danh mục hiện tại (đã ẩn)</option>}
      {categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field>
    {!p && <label className="field field--inline"><input type="checkbox" checked={withVariants}
      onChange={(e) => setWithVariants(e.currentTarget.checked)} /> Sản phẩm có size/màu (quần áo, giày…)</label>}
    {!grouped.current && <Field label="SKU" error={errors.sku}><input readOnly={!!p} {...form.register('sku')} /></Field>}
    <Field label="Tên sản phẩm" error={errors.name}><input readOnly={member} {...form.register('name')} /></Field>
    {variant && <p className="muted">Biến thể: {variantLabel(variant.size, variant.color)}</p>}
    {!grouped.current && <Field label="Giá (VND)" error={errors.unitPrice}><input inputMode="numeric" {...form.register('unitPrice')} /></Field>}
    {!member && <Field label="Mô tả" error={errors.description}><textarea rows={5} {...form.register('description')} /></Field>}
    {!p && !withVariants && <Field label="Tồn kho ban đầu" error={errors.initialStock}><input inputMode="numeric" {...form.register('initialStock')} /></Field>}
    {grouped.current && <VariantEditor rows={rows} onRows={setRows} showErrors={checked} />}
    <ErrorNote error={save.error} />
    {p && save.isSuccess && <p className="note" role="status">Đã lưu.</p>}
    <button className="btn btn--primary" disabled={save.isPending}>Lưu</button>
  </form>;
}

function Variants({ rows, currentId, lead }: { readonly rows: readonly GroupRow[]; readonly currentId: string; readonly lead: boolean }) {
  return <section className="panel stack">
    <h2>Biến thể ({rows.length})</h2>
    {lead && <p className="muted">Gửi duyệt, ngừng bán hay bán lại ở biến thể đầu tiên cũng áp dụng cho các biến thể đang cùng trạng thái.
      Đổi tên, danh mục hoặc mô tả ở đây sẽ đổi tên cả nhóm.</p>}
    <table className="table" tabIndex={0}>
      <thead><tr><th>Biến thể</th><th>SKU</th><th>Giá</th><th>Tồn kho</th><th>Trạng thái</th></tr></thead>
      <tbody>{rows.map((row) => <tr key={row.id}>
        <td>{row.id === currentId ? <strong>{variantLabel(row.size, row.color)}</strong>
          : <Link to={`/seller/products/${row.id}`}>{variantLabel(row.size, row.color)}</Link>}</td>
        <td>{row.sku}</td><td>{money(row.unitPrice)}</td><td>{row.available ?? '—'}</td><td><Badge value={row.status} /></td>
      </tr>)}</tbody></table>
  </section>;
}

function Stock({ id, available, reserved }: { readonly id: string; readonly available: number | null; readonly reserved: number | null }) {
  const [value, setValue] = useState(String(available ?? 0));
  const save = useAction(() => api(`/api/seller/products/${id}/stock`, { method: 'PUT', body: { available: Number(value) } }), ['seller-product']);
  return <section className="panel stack">
    <h2>Tồn kho</h2>
    {available === null ? <p className="muted">Chưa khởi tạo tồn kho.</p> : <p className="muted">Khả dụng {available} · Đang giữ cho đơn {reserved ?? 0}</p>}
    <div className="row"><input aria-label="Số lượng khả dụng" type="number" min={0} max={1000000} value={value} onChange={(e) => setValue(e.currentTarget.value)} />
      <button type="button" className="btn" disabled={save.isPending} onClick={() => save.mutate(undefined)}>Cập nhật</button></div>
    <ErrorNote error={save.error} />
  </section>;
}

function Images({ product, images }: { readonly product: SellerProduct['product']; readonly images: SellerProduct['images'] }) {
  const { id, status, version } = product;
  const changeable = ['DRAFT', 'REJECTED', 'INACTIVE'].includes(status);
  const upload = useAction((files: File[]) => {
    const form = new FormData();
    form.append('expectedVersion', version);
    files.forEach((file) => form.append('files', file));
    return api(`/api/seller/products/${id}/images`, { method: 'POST', form });
  }, ['seller-product', 'seller-products']);
  const remove = useAction((imageId: string) => api(`/api/seller/products/${id}/images/${imageId}?expectedVersion=${version}`, { method: 'DELETE' }),
    ['seller-product', 'seller-products']);
  return <section className="panel stack">
    <h2>Ảnh sản phẩm ({images.length}/5)</h2>
    <div className="row row--wrap">{images.map((image) => <div key={image.id} className="stack thumb-box">
      <img src={image.url} alt="" />{changeable && <button type="button" className="btn" disabled={remove.isPending} onClick={() => remove.mutate(image.id)}>Xóa</button>}
    </div>)}</div>
    {changeable ? <label className="field">Tải ảnh lên (JPEG, PNG, WebP; tối đa 2 MB mỗi ảnh)
      <input type="file" multiple accept="image/jpeg,image/png,image/webp" disabled={upload.isPending}
        onChange={(e) => {
          // Copy first: FileList is live and clearing the input empties it.
          const files = Array.from(e.currentTarget.files ?? []);
          e.currentTarget.value = '';
          if (files.length) upload.mutate(files);
        }} /></label> : <p className="muted">Chỉ đổi được ảnh khi sản phẩm là bản nháp, bị từ chối hoặc đã ngừng bán.</p>}
    <ErrorNote error={upload.error ?? remove.error} />
  </section>;
}

export function SellerProductPage() {
  const { id } = useParams();
  const detail = useQuery({ queryKey: ['seller-product', id], queryFn: () => api<SellerProduct>(`/api/seller/products/${id}`), enabled: !!id });
  const lifecycle = useAction(({ action, version }: { action: string; version: string }) =>
    api(`/api/seller/products/${id}/${action}`, { method: 'POST', body: { expectedVersion: version } }), ['seller-product', 'seller-products']);
  if (!id) return <div className="stack"><Link to="/seller/products">← Sản phẩm</Link><h1>Thêm sản phẩm</h1><ProductForm /></div>;
  return <Loaded query={detail}>{(data) => {
    const member = !!data.variant && data.variant.position > 1;
    return <div className="stack">
      <Link to="/seller/products">← Sản phẩm</Link>
      <div className="row row--between"><h1>{data.product.name}</h1><Badge value={data.product.status} /></div>
      <div className="row row--wrap">
        {['DRAFT', 'REJECTED'].includes(data.product.status) && <button type="button" className="btn btn--primary" disabled={lifecycle.isPending}
          onClick={() => lifecycle.mutate({ action: 'submit', version: data.product.version })}>Gửi duyệt</button>}
        {data.product.status === 'INACTIVE' && <button type="button" className="btn btn--primary" disabled={lifecycle.isPending}
          onClick={() => lifecycle.mutate({ action: 'activate', version: data.product.version })}>Bán lại</button>}
        {data.product.status === 'ACTIVE' && <button type="button" className="btn" disabled={lifecycle.isPending}
          onClick={() => lifecycle.mutate({ action: 'deactivate', version: data.product.version })}>Ngừng bán</button>}
      </div>
      <ErrorNote error={lifecycle.error} />
      {data.product.status === 'ACTIVE' && <p className="muted">Đổi giá áp dụng ngay. Đổi tên, danh mục hoặc mô tả sẽ đưa sản phẩm về chờ duyệt. Muốn đổi ảnh, hãy ngừng bán trước.</p>}
      {data.product.status === 'INACTIVE' && <p className="muted">Đổi giá vẫn bán lại được ngay. Đổi tên, danh mục, mô tả hoặc ảnh thì phải gửi duyệt lại.</p>}
      <ProductForm key={data.product.version} current={data} />
      {data.variants && data.variants.length > 0 && <Variants rows={data.variants} currentId={data.product.id} lead={!member} />}
      <Stock key={`stock-${data.available}`} id={data.product.id} available={data.available} reserved={data.reserved} />
      {member ? <p className="muted">Ảnh dùng chung cho cả nhóm, đổi ở <Link to={`/seller/products/${data.variant?.groupId}`}>biến thể đầu tiên</Link>.</p>
        : <Images product={data.product} images={data.images} />}
    </div>;
  }}</Loaded>;
}
