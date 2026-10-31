import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../shared/api';
import { useAuth } from '../../shared/auth';
import { dateTime, ErrorNote, Loaded, money, useAction } from '../../shared/ui';
import type { StoreRef } from './CatalogPage';

interface Detail {
  readonly id: string; readonly name: string; readonly unitPrice: string; readonly description: string;
  readonly store: StoreRef; readonly images: readonly string[]; readonly available: number;
  readonly rating: { readonly count: number; readonly average: number };
}
interface Review { readonly id: string; readonly rating: number; readonly comment: string | null; readonly createdAt: string }

const stars = (n: number) => '★'.repeat(n) + '☆'.repeat(5 - n);

function Buy({ product }: { readonly product: Detail }) {
  const { account, ready, has } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [quantity, setQuantity] = useState(1);
  const add = useAction(() => api('/api/cart/items', { method: 'POST', body: { productId: product.id, quantity } }), ['cart']);
  const buyNow = useAction(async () => {
    await api('/api/cart/items', { method: 'POST', body: { productId: product.id, quantity } });
    navigate('/cart');
  }, ['cart']);
  const toLogin = () => navigate(`/login?next=${encodeURIComponent(location.pathname)}`);
  const blocked = !ready || add.isPending || buyNow.isPending || (account !== null && !has('CUSTOMER'));
  if (product.available < 1) return <p className="note note--error">Sản phẩm tạm hết hàng.</p>;
  return <div className="stack">
    <div className="row">
      <label className="field field--inline">Số lượng
        <input type="number" min={1} max={Math.min(1000, product.available)} value={quantity}
          onChange={(e) => setQuantity(Math.max(1, Math.trunc(Number(e.currentTarget.value)) || 1))} /></label>
    </div>
    <div className="row row--wrap">
      <button type="button" className="btn btn--soft" disabled={blocked}
        onClick={() => account ? add.mutate(undefined) : toLogin()}>Thêm vào giỏ hàng</button>
      <button type="button" className="btn btn--primary" disabled={blocked}
        onClick={() => account ? buyNow.mutate(undefined) : toLogin()}>Mua ngay</button>
    </div>
    <ErrorNote error={add.error ?? buyNow.error} />
    {add.isSuccess && <p className="note" role="status">Đã thêm vào giỏ. <Link to="/cart">Xem giỏ hàng</Link></p>}
  </div>;
}

export function ProductPage() {
  const { id = '' } = useParams();
  const product = useQuery({ queryKey: ['product', id], queryFn: () => api<Detail>(`/api/catalog/products/${id}`) });
  const reviews = useQuery({ queryKey: ['product-reviews', id], queryFn: () => api<Review[]>(`/api/catalog/products/${id}/reviews`) });
  const [image, setImage] = useState(0);
  return <Loaded query={product}>{(p) => <div className="stack">
    <div className="detail panel">
      <div className="stack">
        {p.images.length > 0 ? <img className="detail__image" src={p.images[image] ?? p.images[0]} alt={p.name} />
          : <div className="product__blank detail__image" aria-hidden="true" />}
        {p.images.length > 1 && <div className="row row--wrap">{p.images.map((src, index) =>
          <button key={src} type="button" className="thumb" aria-label={`Ảnh ${index + 1}`} onClick={() => setImage(index)}><img src={src} alt="" /></button>)}</div>}
      </div>
      <div className="stack">
        <h1>{p.name}</h1>
        <p className="price price--large">{money(p.unitPrice)}</p>
        <p className="muted">Cửa hàng: <Link to={`/stores/${p.store.slug}`}>{p.store.name}</Link> · Còn {p.available} sản phẩm</p>
        <p className="muted">{p.rating.count > 0 ? `${stars(Math.round(p.rating.average))} ${p.rating.average}/5 (${p.rating.count} đánh giá)` : 'Chưa có đánh giá'}</p>
        <Buy product={p} />
      </div>
    </div>
    <section className="panel stack">
      <h2>Mô tả sản phẩm</h2>
      <p className="pre">{p.description}</p>
    </section>
    <section className="panel stack">
      <h2>Đánh giá</h2>
      <Loaded query={reviews} empty={(rows) => rows.length === 0}>{(rows) => <ul className="list">{rows.map((r) => <li key={r.id}>
        <span aria-label={`${r.rating} trên 5 sao`}>{stars(r.rating)}</span> <small className="muted">{dateTime(r.createdAt)}</small>
        {r.comment && <p className="pre">{r.comment}</p>}
      </li>)}</ul>}</Loaded>
    </section>
  </div>}</Loaded>;
}
