import { useState } from 'react';
import { CategoryBrowser } from '../features/catalog/CategoryBrowser';
import { formatProductPrice } from '../features/product/productManagementModel';
import { publicProducts } from './demoModel';
import { DemoIcon, ProductArtwork } from './DemoVisuals';
import type { DemoProduct, DemoState } from './demoTypes';

export function ProductPicture({ product, label = true }: { product: DemoProduct; label?: boolean }) {
  return product.images[0] ? <img className="demo-photo" src={product.images[0].url} alt={label ? product.name : ''} />
    : <ProductArtwork kind={product.art} variant={Number.parseInt(product.id.slice(-2), 16) % 3} label={label ? product.name : undefined} />;
}

function ProductCard({ product, state, navigate }: { product: DemoProduct; state: DemoState; navigate: (path: string) => void }) {
  const store = state.stores.find(item => item.id === product.storeId);
  const category = state.categories.find(item => item.id === product.categoryId);
  return <article className="demo-product-card">
    <button className="demo-product-image" onClick={() => navigate(`/product/${product.id}`)} aria-label={`Xem ${product.name}`}><ProductPicture product={product} /><span className="demo-product-image-arrow"><DemoIcon name="arrow" /></span></button>
    <div className="demo-product-meta"><span>{category?.name}</span><span>{store?.name}</span></div>
    <h3><a href={`#/product/${product.id}`}>{product.name}</a></h3><p className="demo-product-price">{formatProductPrice(product.unitPrice)}</p>
  </article>;
}

export function CatalogPage({ state, navigate, categoriesOnly = false }: { state: DemoState; navigate: (path: string) => void; categoriesOnly?: boolean }) {
  const [query, setQuery] = useState(''), [category, setCategory] = useState<string | null>(null), [sort, setSort] = useState('name'), [page, setPage] = useState(1);
  const eligible = publicProducts(state);
  const included = new Set(category ? [category] : state.categories.map(item => item.id));
  if (category) for (let i = 0; i < state.categories.length; i++) for (const item of state.categories) if (item.parentId && included.has(item.parentId)) included.add(item.id);
  const filtered = eligible.filter(product => included.has(product.categoryId) && product.name.toLocaleLowerCase('vi').includes(query.trim().toLocaleLowerCase('vi')))
    .sort((a, b) => (sort === 'price-asc' ? (BigInt(a.unitPrice) < BigInt(b.unitPrice) ? -1 : BigInt(a.unitPrice) > BigInt(b.unitPrice) ? 1 : 0)
      : sort === 'price-desc' ? (BigInt(a.unitPrice) > BigInt(b.unitPrice) ? -1 : BigInt(a.unitPrice) < BigInt(b.unitPrice) ? 1 : 0)
        : a.name.localeCompare(b.name, 'vi')) || a.id.localeCompare(b.id));
  const pages = Math.max(1, Math.ceil(filtered.length / 6)), currentPage = Math.min(page, pages);
  return <>
    {!categoriesOnly && <section className="demo-hero"><div className="demo-hero-copy"><p className="demo-eyebrow"><span /> CHỌN ĐIỀU GIẢN DỊ, SỐNG THẬT TINH TẾ</p><h1>Một chút <em>nếp nhà.</em><br />Cho góc nhỏ của bạn.</h1><p>Khám phá những món đồ được chọn lọc từ các cửa hàng độc lập. Gần gũi, chỉn chu và dành cho góc nhỏ của bạn.</p><a className="demo-button" href="#catalog-products" onClick={event => { event.preventDefault(); document.getElementById('catalog-products')?.scrollIntoView({ behavior: 'smooth' }); }}>Khám phá bộ sưu tập <DemoIcon name="arrow" size={18} /></a><div className="demo-hero-signature"><span>{String(state.stores.length).padStart(2, '0')}</span><p>Cửa hàng mẫu<br /><strong>Một không gian để khám phá</strong></p></div></div><div className="demo-hero-art"><ProductArtwork kind="vase" /><span className="demo-hero-tag"><DemoIcon name="leaf" size={17} /> Cảm hứng từ tự nhiên</span><div className="demo-hero-art-label"><span>BỘ SƯU TẬP ĐỜI THƯỜNG</span><strong>Đẹp từ những điều nhỏ bé.</strong></div></div></section>}
    <section className="demo-catalog-section" id="catalog-products"><div className="demo-page-head"><div><p className="demo-eyebrow">ĐƯỢC CHỌN LỌC CHO BẠN</p><h1>{categoriesOnly ? 'Khám phá danh mục' : 'Tìm một điều bạn thích'}</h1><p>Chỉ sản phẩm đang bán từ người bán đã duyệt, cửa hàng đang mở.</p></div><span className="demo-count-label">{eligible.length} sản phẩm mẫu</span></div>
      <div className="demo-catalog-toolbar"><label className="demo-search"><DemoIcon name="search" /><span className="demo-sr-only">Tìm tên sản phẩm</span><input value={query} placeholder="Tìm tên sản phẩm…" onChange={event => { setQuery(event.target.value); setPage(1); }} /></label><label className="demo-sort">Sắp xếp<select aria-label="Sắp xếp" value={sort} onChange={event => { setSort(event.target.value); setPage(1); }}><option value="name">Tên A → Z</option><option value="price-asc">Giá tăng dần</option><option value="price-desc">Giá giảm dần</option></select></label></div>
      <div className="demo-catalog-layout"><aside className="demo-catalog-filter"><div className="demo-filter-title"><h2>Danh mục</h2><button className="demo-link-button" onClick={() => { setCategory(null); setQuery(''); setPage(1); }}>Bỏ lọc</button></div><CategoryBrowser state={{ status: 'ready', categories: state.categories }} selectedId={category} onSelect={item => { setCategory(item.id); setPage(1); }} /><p className="demo-filter-note"><DemoIcon name="check" size={16} /> Dữ liệu mẫu đã qua kiểm tra điều kiện hiển thị.</p></aside>
        <div><div className="demo-results-line" aria-live="polite"><span>{filtered.length} kết quả {category && `· ${state.categories.find(item => item.id === category)?.name}`}</span><span>Trang {currentPage}/{pages}</span></div>{filtered.length ? <div className="demo-product-grid">{filtered.slice((currentPage - 1) * 6, currentPage * 6).map(product => <ProductCard key={product.id} product={product} state={state} navigate={navigate} />)}</div> : <div className="demo-empty"><DemoIcon name="search" size={36} /><h2>Chưa tìm thấy điều bạn cần</h2><p>Thử một từ khóa khác hoặc bỏ lọc danh mục.</p><button className="demo-button secondary" onClick={() => { setQuery(''); setCategory(null); }}>Xem tất cả sản phẩm</button></div>}
          {pages > 1 && <nav className="demo-pagination" aria-label="Phân trang Catalog"><button disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)}>Trước</button>{Array.from({ length: pages }, (_, i) => <button key={i} aria-label={`Trang ${i + 1}`} aria-current={currentPage === i + 1 ? 'page' : undefined} onClick={() => setPage(i + 1)}>{i + 1}</button>)}<button disabled={currentPage === pages} onClick={() => setPage(currentPage + 1)}>Sau</button></nav>}
        </div></div>
    </section>
  </>;
}

export function ProductDetail({ state, id, navigate }: { state: DemoState; id: string; navigate: (path: string) => void }) {
  const [image, setImage] = useState(0);
  const product = publicProducts(state).find(item => item.id === id);
  if (!product) return <div className="demo-empty"><h1>Sản phẩm chưa sẵn sàng</h1><p>Sản phẩm không tồn tại hoặc chưa đủ điều kiện hiển thị công khai.</p><button className="demo-button" onClick={() => navigate('/catalog')}>Về bộ sưu tập</button></div>;
  const store = state.stores.find(item => item.id === product.storeId)!;
  const category = state.categories.find(item => item.id === product.categoryId)!;
  const selected = product.images[image] ?? product.images[0];
  return <><a className="demo-back-link" href="#/catalog">← Về bộ sưu tập</a><article className="demo-product-detail"><div><div className="demo-detail-gallery">{selected ? <img className="demo-photo" src={selected.url} alt={product.name} /> : <ProductPicture product={product} />}</div><div className="demo-gallery-thumbs">{product.images.length ? product.images.map((item, index) => <button key={item.id} aria-label={`Xem ảnh ${index + 1}`} aria-pressed={index === image} onClick={() => setImage(index)}><img src={item.url} alt="" /></button>) : <span>Hình minh họa cho dữ liệu mẫu</span>}</div></div><div className="demo-detail-info"><p className="demo-eyebrow">{category.name}</p><h1>{product.name}</h1><p className="demo-detail-price">{formatProductPrice(product.unitPrice)}</p><div className="demo-detail-labels"><span className="demo-badge" data-tone="success">Đang bán</span><span>SKU · {product.sku}</span></div><p className="demo-detail-description">{product.description}</p><div className="demo-store-card"><div className="demo-store-mark"><DemoIcon name="store" size={25} /></div><div><strong>{store.name}</strong><p>Cửa hàng đang mở · Người bán đã duyệt</p></div><DemoIcon name="check" /></div><div className="demo-callout"><DemoIcon name="help" /><p>Bạn đang xem dữ liệu mẫu. Giỏ hàng và Checkout thuộc phần bàn giao TV3, chưa có trong demo TV2.</p></div><button className="demo-button secondary" onClick={() => navigate('/catalog')}>Tiếp tục khám phá <DemoIcon name="arrow" size={17} /></button></div></article></>;
}
