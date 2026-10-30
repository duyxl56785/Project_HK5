import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { ApiClient } from '../../shared/apiClient';
import { CategoryBrowser } from './CategoryBrowser';
import { loadPublicCategories } from './publicCategories';

/** Mounted only by the opt-in shared router; does not grant access to Category writes. */
export function PublicCategoriesPage({ client }: { client: ApiClient }) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const query = useQuery({
    queryKey: ['public-categories'],
    queryFn: ({ signal }) => loadPublicCategories(client, signal),
    retry: false,
  });
  return <section aria-label="Danh mục công khai"><h1>Danh mục sản phẩm</h1>
    <CategoryBrowser selectedId={selectedId} onSelect={category => setSelectedId(category.id)}
      state={query.isPending ? { status: 'loading' }
        : query.isError ? { status: 'error', onRetry: () => { void query.refetch(); } }
        : { status: 'ready', categories: query.data }} />
  </section>;
}
