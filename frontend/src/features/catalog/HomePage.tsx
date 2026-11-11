import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../../shared/api';
import { Loaded } from '../../shared/ui';
import { ProductCard, useCategories } from './CatalogPage';
import type { Page, StoreRef } from './CatalogPage';
import aoSoMi from '../../assets/banner/AO-002.jpg';
import nonLuoiTrai from '../../assets/banner/NON-001.jpg';
import quanJean from '../../assets/banner/QUAN-001.jpg';
import sach from '../../assets/banner/SACH-002.jpg';
import caPhe from '../../assets/banner/TP-001.jpg';
import tuiTote from '../../assets/banner/TUI-001.jpg';

const ICONS = {
  bag: 'M6 8h12l1.5 13h-15zM9 8a3 3 0 0 1 6 0',
  wallet: 'M3 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM3 7l13-3v3M16 13.5h2',
  track: 'M3 6h11v11H3zM14 10h4l3 3v4h-7M7 19.5a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM17 19.5a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
  back: 'M9 14 4 9l5-5M4 9h11a5 5 0 0 1 0 10h-4',
};

// Keyed by the seeded root categories; any other category shows its first letter.
const CATEGORY_ICONS: Record<string, string> = {
  'Thời trang': 'M20.4 3.5 16 2a4 4 0 0 1-8 0L3.6 3.5a2 2 0 0 0-1.3 2.2l.6 3.5a1 1 0 0 0 1 .8H6v10a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V10h2.1a1 1 0 0 0 1-.8l.6-3.5a2 2 0 0 0-1.3-2.2z',
  'Phụ kiện': 'M7 6a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2zM9 4l.7-2h4.6l.7 2M9 20l.7 2h4.6l.7-2M12 9v3l2 1',
  'Nhà cửa': 'M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z',
  'Mẹ và bé': 'M12 20.5S3 15 3 8.8A4.8 4.8 0 0 1 12 6a4.8 4.8 0 0 1 9 2.8C21 15 12 20.5 12 20.5z',
  'Thực phẩm': 'M12 7c-2-1.5-7-1.5-8 3.5C3 15 6 21 9 21c1.2 0 1.8-.7 3-.7s1.8.7 3 .7c3 0 6-6 5-10.5C19 5.5 14 5.5 12 7zM12 7c0-2 1-4 3-4',
  'Điện tử': 'M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM11 18.5h2',
  'Làm đẹp': 'M12 3l1.9 5.6 5.6 1.9-5.6 1.9L12 18l-1.9-5.6-5.6-1.9 5.6-1.9zM19 16v4M17 18h4',
  'Thể thao': 'M6.5 7v10M3 9.5v5M17.5 7v10M21 9.5v5M6.5 12h11',
  'Sách': 'M4 19.5V5a2 2 0 0 1 2-2h14v18H6a2 2 0 0 1-2-1.5zM4 19.5a2 2 0 0 1 2-2h14',
};

function Icon({ path }: { readonly path: string }) {
  return <svg width={26} height={26} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.7}
    strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={path} /></svg>;
}

const SLIDES = [
  { title: 'Một giỏ hàng, nhiều cửa hàng', text: 'Chọn hàng từ nhiều người bán và đặt trong một lần.', to: '/products', action: 'Mua sắm ngay', photos: [quanJean, caPhe, aoSoMi] },
  { title: 'Trả tiền khi nhận hàng', text: 'Thanh toán COD, theo dõi từng đơn của từng cửa hàng.', to: '/products', action: 'Khám phá sản phẩm', photos: [tuiTote, nonLuoiTrai, sach] },
  { title: 'Mở cửa hàng trên Soopi', text: 'Gửi hồ sơ, được duyệt là có thể đăng bán.', to: '/seller', action: 'Đăng ký bán hàng', photos: [nonLuoiTrai, caPhe, quanJean] },
];

function Banner() {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  useEffect(() => {
    if (paused || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const timer = setInterval(() => setIndex((n) => (n + 1) % SLIDES.length), 8000);
    return () => clearInterval(timer);
  }, [paused]);
  const slide = SLIDES[index];
  return <section className={`banner banner--${index}`} aria-label="Giới thiệu Soopi"
    onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}
    onFocus={() => setPaused(true)} onBlur={() => setPaused(false)}>
    <div className="banner__art">{slide.photos.map((photo) => <img key={photo} src={photo} alt="" />)}</div>
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
    <ul className="perks">
      <li><Icon path={ICONS.bag} /><div><strong>Một giỏ, nhiều cửa hàng</strong><span>Đặt một lần cho mọi người bán</span></div></li>
      <li><Icon path={ICONS.wallet} /><div><strong>Trả tiền khi nhận hàng</strong><span>Thanh toán COD khi nhận đơn</span></div></li>
      <li><Icon path={ICONS.track} /><div><strong>Theo dõi từng đơn</strong><span>Cập nhật theo từng cửa hàng</span></div></li>
      <li><Icon path={ICONS.back} /><div><strong>Yêu cầu trả hàng</strong><span>Ngay trong đơn đã giao</span></div></li>
    </ul>
    {roots.length > 0 && <section className="shelf">
      <header className="shelf__head"><h2>Danh mục</h2></header>
      <div className="tiles">{roots.map((c) => <Link key={c.id} className="tile" to={`/products?categoryId=${c.id}`}>
        <span className="tile__mark" aria-hidden="true">{CATEGORY_ICONS[c.name] ? <Icon path={CATEGORY_ICONS[c.name]} /> : c.name.charAt(0)}</span>{c.name}
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
