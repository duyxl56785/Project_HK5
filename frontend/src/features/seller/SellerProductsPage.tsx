import { useState } from 'react';
import type { FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../shared/api';
import { Badge, ErrorNote, Loaded, money, StatusFilter, useAction } from '../../shared/ui';
import { useCategories } from '../catalog/CatalogPage';

export interface Product {
  readonly id: string; readonly categoryId: string; readonly sku: string; readonly name: string;
  readonly unitPrice: string; readonly status: string; readonly version: string;
}
interface SellerProduct {
  readonly product: Product; readonly description: string; readonly available: number | null; readonly reserved: number | null;
  readonly images: readonly { readonly id: string; readonly url: string }[];
}
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
  const [categoryId, setCategoryId] = useState(p?.categoryId ?? '');
  const [sku, setSku] = useState(p?.sku ?? '');
  const [name, setName] = useState(p?.name ?? '');
  const [unitPrice, setUnitPrice] = useState(p?.unitPrice ?? '');
  const [description, setDescription] = useState(current?.description ?? '');
  const [initialStock, setInitialStock] = useState('0');
  const save = useAction(() => p
    ? api<SellerProduct>(`/api/seller/products/${p.id}`, { method: 'PUT', body: { categoryId, name, unitPrice, description, expectedVersion: p.version } })
    : api<SellerProduct>('/api/seller/products', { method: 'POST', body: { categoryId, sku, name, unitPrice, description, initialStock: Number(initialStock) } }),
  ['seller-products'], (saved) => { if (!p) navigate(`/seller/products/${saved.product.id}`, { replace: true }); });
  function submit(event: FormEvent) { event.preventDefault(); save.mutate(undefined); }
  return <form className="panel stack" onSubmit={submit}>
    <h2>Thông tin sản phẩm</h2>
    <label className="field">Danh mục<select required value={categoryId} onChange={(e) => setCategoryId(e.currentTarget.value)}>
      <option value="">Chọn danh mục…</option>
      {categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
    <label className="field">SKU<input required maxLength={128} disabled={!!p} value={sku} onChange={(e) => setSku(e.currentTarget.value)} /></label>
    <label className="field">Tên sản phẩm<input required maxLength={255} value={name} onChange={(e) => setName(e.currentTarget.value)} /></label>
    <label className="field">Giá (VND)<input required type="number" min={0} step={1} value={unitPrice} onChange={(e) => setUnitPrice(e.currentTarget.value)} /></label>
    <label className="field">Mô tả<textarea rows={5} maxLength={5000} value={description} onChange={(e) => setDescription(e.currentTarget.value)} /></label>
    {!p && <label className="field">Tồn kho ban đầu<input type="number" min={0} max={1000000} value={initialStock} onChange={(e) => setInitialStock(e.currentTarget.value)} /></label>}
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
  const upload = useAction((files: FileList) => {
    const form = new FormData();
    Array.from(files).forEach((file) => form.append('files', file));
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
        onChange={(e) => { if (e.currentTarget.files?.length) upload.mutate(e.currentTarget.files); e.currentTarget.value = ''; }} /></label>
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
      {['DRAFT', 'REJECTED', 'INACTIVE'].includes(data.product.status) && <button type="button" className="btn btn--primary" disabled={lifecycle.isPending}
        onClick={() => lifecycle.mutate({ action: 'submit', version: data.product.version })}>Gửi duyệt</button>}
      {data.product.status === 'ACTIVE' && <button type="button" className="btn" disabled={lifecycle.isPending}
        onClick={() => lifecycle.mutate({ action: 'deactivate', version: data.product.version })}>Ngừng bán</button>}
    </div>
    <ErrorNote error={lifecycle.error} />
    <ProductForm key={data.product.version} current={data} />
    <Stock key={`stock-${data.available}`} id={data.product.id} available={data.available} reserved={data.reserved} />
    <Images id={data.product.id} images={data.images} />
  </div>}</Loaded>;
}
