import { useState } from 'react';
import type { FormEvent } from 'react';
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
interface Page { readonly items: readonly Summary[]; readonly total: number; readonly page: number; readonly size: number }

const SIZE = 12;

export function useCategories() {
  return useQuery({ queryKey: ['categories'], queryFn: () => api<CategoryDto[]>('/api/categories') });
}

export function CatalogPage({ store }: { readonly store?: StoreRef }) {
  const [params, setParams] = useSearchParams();
  const effective = new URLSearchParams(params);
  if (store) effective.set('storeId', store.id);
  const [term, setTerm] = useState(params.get('q') ?? '');
  const categories = useCategories();
  const page = Number(params.get('page') ?? 0);
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
  function search(event: FormEvent) { event.preventDefault(); update({ q: term.trim() }); }
  const goTo = (n: number) => { const next = new URLSearchParams(params); next.set('page', String(n)); setParams(next); };

  return <div className="catalog">
    <aside className="stack">
      <CategoryBrowser selectedId={params.get('categoryId')} onSelect={(c) => update({ categoryId: c.id })}
        state={categories.data ? { status: 'ready', categories: categories.data }
          : categories.isError ? { status: 'error', onRetry: () => void categories.refetch() } : { status: 'loading' }} />
      {params.get('categoryId') && <button type="button" className="btn" onClick={() => update({ categoryId: null })}>Bỏ lọc danh mục</button>}
    </aside>
    <section className="stack">
      <form className="row" onSubmit={search} role="search">
        <input aria-label="Tìm sản phẩm" className="grow" placeholder="Tìm sản phẩm…" maxLength={100} value={term} onChange={(e) => setTerm(e.currentTarget.value)} />
        <button className="btn btn--primary">Tìm</button>
      </form>
      <div className="row row--wrap">
        <label className="field field--inline">Giá từ
          <input type="number" min={0} defaultValue={params.get('minPrice') ?? ''} onBlur={(e) => update({ minPrice: e.currentTarget.value })} /></label>
        <label className="field field--inline">đến
          <input type="number" min={0} defaultValue={params.get('maxPrice') ?? ''} onBlur={(e) => update({ maxPrice: e.currentTarget.value })} /></label>
        <label className="field field--inline">Sắp xếp
          <select value={params.get('sort') ?? 'NEWEST'} onChange={(e) => update({ sort: e.currentTarget.value })}>
            <option value="NEWEST">Mới nhất</option><option value="PRICE_ASC">Giá tăng dần</option>
            <option value="PRICE_DESC">Giá giảm dần</option><option value="NAME_ASC">Tên A–Z</option>
          </select></label>
        {!store && params.get('storeId') && <button type="button" className="btn" onClick={() => update({ storeId: null })}>Bỏ lọc cửa hàng</button>}
      </div>
      <Loaded query={query}>{(data) => data.items.length === 0 ? <p className="muted">Không tìm thấy sản phẩm phù hợp.</p> : <>
        <div className="products">{data.items.map((p) => <Link key={p.id} className="product" to={`/products/${p.id}`}>
          {p.imageUrl ? <img src={p.imageUrl} alt="" loading="lazy" /> : <div className="product__blank" aria-hidden="true" />}
          <strong>{p.name}</strong><span className="price">{money(p.unitPrice)}</span><small className="muted">{p.store.name}</small>
        </Link>)}</div>
        <nav className="row row--center" aria-label="Phân trang">
          <button type="button" className="btn" disabled={page <= 0} onClick={() => goTo(page - 1)}>Trước</button>
          <span>Trang {page + 1}/{Math.max(1, Math.ceil(data.total / SIZE))}</span>
          <button type="button" className="btn" disabled={(page + 1) * SIZE >= data.total} onClick={() => goTo(page + 1)}>Sau</button>
        </nav>
      </>}</Loaded>
    </section>
  </div>;
}
