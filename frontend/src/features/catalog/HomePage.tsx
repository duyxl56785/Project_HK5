import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../../shared/api';
import { Loaded } from '../../shared/ui';
import { ProductCard, useCategories } from './CatalogPage';
import type { Page, StoreRef } from './CatalogPage';

const SLIDES = [
  { title: 'Một giỏ hàng, nhiều cửa hàng', text: 'Chọn hàng từ nhiều người bán và đặt trong một lần.', to: '/products', action: 'Mua sắm ngay' },
  { title: 'Trả tiền khi nhận hàng', text: 'Thanh toán COD, theo dõi từng đơn của từng cửa hàng.', to: '/products', action: 'Khám phá sản phẩm' },
  { title: 'Mở cửa hàng trên Soopi', text: 'Gửi hồ sơ, được duyệt là có thể đăng bán.', to: '/seller', action: 'Đăng ký bán hàng' },
];

function Banner() {
  const [index, setIndex] = useState(0);
  useEffect(() => {
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const timer = setInterval(() => setIndex((n) => (n + 1) % SLIDES.length), 5000);
    return () => clearInterval(timer);
  }, []);
  const slide = SLIDES[index];
  return <section className={`banner banner--${index}`} aria-label="Giới thiệu Soopi">
    <h1>{slide.title}</h1>
    <p>{slide.text}</p>
    <Link className="btn banner__action" to={slide.to}>{slide.action}</Link>
    <div className="banner__dots">{SLIDES.map((item, n) => <button key={item.title} type="button"
      aria-label={`Xem mục ${n + 1}`} aria-current={n === index} onClick={() => setIndex(n)} />)}</div>
  </section>;
}

function Shelf({ title, query, more, row }: { readonly title: string; readonly query: string; readonly more?: string; readonly row?: boolean }) {
  const products = useQuery({ queryKey: ['catalog', query], queryFn: () => api<Page>(`/api/catalog/products?${query}`) });
  return <section className={row ? 'shelf shelf--row' : 'shelf'}>
    <header className="shelf__head"><h2>{title}</h2>{more && <Link to={more}>Xem tất cả</Link>}</header>
    <Loaded query={products} empty={(data) => data.items.length === 0}>{(data) =>
      <div className="products">{data.items.map((p) => <ProductCard key={p.id} product={p} />)}</div>}</Loaded>
  </section>;
}

export function HomePage() {
  const categories = useCategories();
  const stores = useQuery({ queryKey: ['stores'], queryFn: () => api<StoreRef[]>('/api/stores') });
  const roots = (categories.data ?? []).filter((c) => c.parentId === null).slice(0, 9);
  return <div className="stack stack--page">
    <Banner />
    {roots.length > 0 && <section className="shelf">
      <header className="shelf__head"><h2>Danh mục</h2></header>
      <div className="tiles">{roots.map((c) => <Link key={c.id} className="tile" to={`/products?categoryId=${c.id}`}>
        <span className="tile__mark" aria-hidden="true">{c.name.charAt(0)}</span>{c.name}
      </Link>)}</div>
    </section>}
    <Shelf title="Giá tốt hôm nay" query="size=6&sort=PRICE_ASC" more="/products?sort=PRICE_ASC" row />
    <Shelf title="Gợi ý hôm nay" query="size=12&sort=NEWEST" more="/products" />
    {(stores.data?.length ?? 0) > 0 && <section className="shelf">
      <header className="shelf__head"><h2>Cửa hàng trên Soopi</h2></header>
      <div className="row row--wrap">{stores.data?.map((s) => <Link key={s.id} className="btn" to={`/stores/${s.slug}`}>{s.name}</Link>)}</div>
    </section>}
  </div>;
}
