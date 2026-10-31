import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useParams } from 'react-router-dom';
import type { z } from 'zod';
import { api } from '../../shared/api';
import { productSchema } from '../../shared/forms';
import { Badge, ErrorNote, Field, Loaded, money, StatusFilter, useAction } from '../../shared/ui';
import { useCategories } from '../catalog/CatalogPage';

export interface Product {
  readonly id: string; readonly categoryId: string; readonly sku: string; readonly name: string;
  readonly unitPrice: string; readonly status: string; readonly version: string;
}
interface SellerProduct {
  readonly product: Product; readonly description: string; readonly available: number | null; readonly reserved: number | null;
  readonly images: readonly { readonly id: string; readonly url: string }[];
}
type ProductInput = z.infer<typeof productSchema>;
const STATUSES = ['DRAFT', 'PENDING_REVIEW', 'ACTIVE', 'REJECTED', 'SUSPENDED', 'INACTIVE'];

export function SellerProductsPage() {
  const [status, setStatus] = useState('');
  const list = useQuery({
    queryKey: ['seller-products', status],
    queryFn: () => api<{ products: Product[] }>(`/api/seller/products?size=100${status && `&status=${status}`}`),
  });
  return <div className="stack">
    <div className="row row--between"><h1>Sản phẩm của cửa hàng</h1><Link className="btn btn--primary" to="/seller/products/new">Thêm sản phẩm</Link></div>
    <StatusFilter value={status} options={STATUSES} onChange={setStatus} allLabel="Tất cả" />
    <Loaded query={list} empty={(data) => data.products.length === 0}>{(data) => <table className="table">
      <thead><tr><th>Tên</th><th>SKU</th><th>Giá</th><th>Trạng thái</th></tr></thead>
      <tbody>{data.products.map((p) => <tr key={p.id}>
        <td><Link to={`/seller/products/${p.id}`}>{p.name}</Link></td><td>{p.sku}</td><td>{money(p.unitPrice)}</td><td><Badge value={p.status} /></td>
      </tr>)}</tbody></table>}</Loaded>
  </div>;
}

function ProductForm({ current }: { readonly current?: SellerProduct }) {
  const navigate = useNavigate();
  const categories = useCategories();
  const p = current?.product;
  const form = useForm<ProductInput>({
    resolver: zodResolver(productSchema),
    defaultValues: {
      categoryId: p?.categoryId ?? '', sku: p?.sku ?? '', name: p?.name ?? '', unitPrice: p?.unitPrice ?? '',
      description: current?.description ?? '', initialStock: '0',
    },
  });
  const { errors } = form.formState;
  const save = useAction(({ categoryId, sku, name, unitPrice, description, initialStock }: ProductInput) => p
    ? api<SellerProduct>(`/api/seller/products/${p.id}`, { method: 'PUT', body: { categoryId, name, unitPrice, description, expectedVersion: p.version } })
    : api<SellerProduct>('/api/seller/products', { method: 'POST', body: { categoryId, sku, name, unitPrice, description, initialStock: Number(initialStock) } }),
  ['seller-products'], (saved) => { if (!p) navigate(`/seller/products/${saved.product.id}`, { replace: true }); });
  return <form className="panel stack" noValidate onSubmit={form.handleSubmit((value) => save.mutate(value))}>
    <h2>Thông tin sản phẩm</h2>
    <Field label="Danh mục" error={errors.categoryId}><select {...form.register('categoryId')}>
      <option value="">Chọn danh mục…</option>
      {p && categories.data && !categories.data.some((c) => c.id === p.categoryId) && <option value={p.categoryId}>Danh mục hiện tại (đã ẩn)</option>}
      {categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field>
    <Field label="SKU" error={errors.sku}><input readOnly={!!p} {...form.register('sku')} /></Field>
    <Field label="Tên sản phẩm" error={errors.name}><input {...form.register('name')} /></Field>
    <Field label="Giá (VND)" error={errors.unitPrice}><input inputMode="numeric" {...form.register('unitPrice')} /></Field>
    <Field label="Mô tả" error={errors.description}><textarea rows={5} {...form.register('description')} /></Field>
    {!p && <Field label="Tồn kho ban đầu" error={errors.initialStock}><input inputMode="numeric" {...form.register('initialStock')} /></Field>}
    <ErrorNote error={save.error} />
    {p && save.isSuccess && <p className="note" role="status">Đã lưu.</p>}
    <button className="btn btn--primary" disabled={save.isPending}>Lưu</button>
  </form>;
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

function Images({ id, images }: { readonly id: string; readonly images: SellerProduct['images'] }) {
  const upload = useAction((files: File[]) => {
    const form = new FormData();
    files.forEach((file) => form.append('files', file));
    return api(`/api/seller/products/${id}/images`, { method: 'POST', form });
  }, ['seller-product']);
  const remove = useAction((imageId: string) => api(`/api/seller/products/${id}/images/${imageId}`, { method: 'DELETE' }), ['seller-product']);
  return <section className="panel stack">
    <h2>Ảnh sản phẩm ({images.length}/5)</h2>
    <div className="row row--wrap">{images.map((image) => <div key={image.id} className="stack thumb-box">
      <img src={image.url} alt="" /><button type="button" className="btn" disabled={remove.isPending} onClick={() => remove.mutate(image.id)}>Xóa</button>
    </div>)}</div>
    <label className="field">Tải ảnh lên (JPEG, PNG, WebP; tối đa 2 MB mỗi ảnh)
      <input type="file" multiple accept="image/jpeg,image/png,image/webp" disabled={upload.isPending}
        onChange={(e) => {
          // Copy first: FileList is live and clearing the input empties it.
          const files = Array.from(e.currentTarget.files ?? []);
          e.currentTarget.value = '';
          if (files.length) upload.mutate(files);
        }} /></label>
    <ErrorNote error={upload.error ?? remove.error} />
  </section>;
}

export function SellerProductPage() {
  const { id } = useParams();
  const detail = useQuery({ queryKey: ['seller-product', id], queryFn: () => api<SellerProduct>(`/api/seller/products/${id}`), enabled: !!id });
  const lifecycle = useAction(({ action, version }: { action: string; version: string }) =>
    api(`/api/seller/products/${id}/${action}`, { method: 'POST', body: { expectedVersion: version } }), ['seller-product', 'seller-products']);
  if (!id) return <div className="stack"><Link to="/seller/products">← Sản phẩm</Link><h1>Thêm sản phẩm</h1><ProductForm /></div>;
  return <Loaded query={detail}>{(data) => <div className="stack">
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
    <Stock key={`stock-${data.available}`} id={data.product.id} available={data.available} reserved={data.reserved} />
    <Images id={data.product.id} images={data.images} />
  </div>}</Loaded>;
}
