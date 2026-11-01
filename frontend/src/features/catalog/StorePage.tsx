import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { api } from '../../shared/api';
import { Loaded } from '../../shared/ui';
import { CatalogPage } from './CatalogPage';
import type { StoreRef } from './CatalogPage';

export function StorePage() {
  const { slug = '' } = useParams();
  const query = useQuery({ queryKey: ['store', slug], queryFn: () => api<StoreRef>(`/api/stores/by-slug/${encodeURIComponent(slug)}`) });
  return <Loaded query={query}>{(store) => <div className="stack">
    <h1>{store.name}</h1>
    <CatalogPage store={store} />
  </div>}</Loaded>;
}
