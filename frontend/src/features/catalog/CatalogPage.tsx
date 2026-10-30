import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../../shared/api';
import { Loaded, money } from '../../shared/ui';
import { CategoryBrowser } from './CategoryBrowser';
import type { CategoryDto } from './categoryTypes';

export interface StoreRef { readonly id: string; readonly name: string; readonly slug: string }
interface Summary {
  readonly id: string; readonly name: string; readonly unitPrice: string;
  readonly store: StoreRef; readonly imageUrl: string | null;
}
export interface Page { readonly items: readonly Summary[]; readonly total: number; readonly page: number; readonly size: number }

const SIZE = 12;
const SORTS = [['NEWEST', 'Mới nhất'], ['PRICE_ASC', 'Giá tăng dần'], ['PRICE_DESC', 'Giá giảm dần'], ['NAME_ASC', 'Tên A–Z']] as const;

export function ProductCard({ product: p }: { readonly product: Summary }) {
  return <Link className="product" to={`/products/${p.id}`}>
    {p.imageUrl ? <img src={p.imageUrl} alt="" loading="lazy" /> : <div className="product__blank" aria-hidden="true" />}
    <span className="product__name">{p.name}</span>
    <span className="price">{money(p.unitPrice)}</span>
    <small className="muted">{p.store.name}</small>
  </Link>;
}

export function useCategories() {
  return useQuery({ queryKey: ['categories'], queryFn: () => api<CategoryDto[]>('/api/categories') });
}

export function CatalogPage({ store }: { readonly store?: StoreRef }) {
  const [params, setParams] = useSearchParams();
  const effective = new URLSearchParams(params);
  if (store) effective.set('storeId', store.id);
  const categories = useCategories();
  const page = Number(params.get('page') ?? 0);
  const sort = params.get('sort') ?? 'NEWEST';
  const query = useQuery({
    queryKey: ['catalog', effective.toString()],
    queryFn: () => api<Page>(`/api/catalog/products?size=${SIZE}&${effective.toString()}`),
    placeholderData: keepPreviousData,
  });

  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params);
    next.delete('page');
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value); else next.delete(key);
    }
    setParams(next);
  }
  const goTo = (n: number) => { const next = new URLSearchParams(params); next.set('page', String(n)); setParams(next); };

  return <div className="catalog">
    <details className="filters" open={matchMedia('(min-width: 721px)').matches}>
      <summary>Bộ lọc</summary>
      <div className="row row--wrap">
        <label className="field field--inline">Giá từ
          <input type="number" min={0} defaultValue={params.get('minPrice') ?? ''} onBlur={(e) => update({ minPrice: e.currentTarget.value })} /></label>
        <label className="field field--inline">đến
          <input type="number" min={0} defaultValue={params.get('maxPrice') ?? ''} onBlur={(e) => update({ maxPrice: e.currentTarget.value })} /></label>
      </div>
      {!store && params.get('storeId') && <button type="button" className="btn" onClick={() => update({ storeId: null })}>Bỏ lọc cửa hàng</button>}
      <CategoryBrowser selectedId={params.get('categoryId')} onSelect={(c) => update({ categoryId: c.id })}
        state={categories.data ? { status: 'ready', categories: categories.data }
          : categories.isError ? { status: 'error', onRetry: () => void categories.refetch() } : { status: 'loading' }} />
      {params.get('categoryId') && <button type="button" className="btn" onClick={() => update({ categoryId: null })}>Bỏ lọc danh mục</button>}
    </details>
    <section className="stack">
      {!store && <h1 className="catalog__title">{params.get('q') ? `Kết quả cho “${params.get('q')}”` : 'Tất cả sản phẩm'}</h1>}
      <div className="sortbar" role="group" aria-label="Sắp xếp">
        <span className="muted">Sắp xếp theo</span>
        {SORTS.map(([value, label]) => <button key={value} type="button" aria-pressed={sort === value}
          className={sort === value ? 'btn btn--primary' : 'btn'} onClick={() => update({ sort: value })}>{label}</button>)}
      </div>
      <Loaded query={query}>{(data) => data.items.length === 0 ? <p className="muted">Không tìm thấy sản phẩm phù hợp.</p> : <>
        <div className="products">{data.items.map((p) => <ProductCard key={p.id} product={p} />)}</div>
        <nav className="row row--center" aria-label="Phân trang">
          <button type="button" className="btn" disabled={page <= 0} onClick={() => goTo(page - 1)}>Trước</button>
          <span>Trang {page + 1}/{Math.max(1, Math.ceil(data.total / SIZE))}</span>
          <button type="button" className="btn" disabled={(page + 1) * SIZE >= data.total} onClick={() => goTo(page + 1)}>Sau</button>
        </nav>
      </>}</Loaded>
    </section>
  </div>;
}
