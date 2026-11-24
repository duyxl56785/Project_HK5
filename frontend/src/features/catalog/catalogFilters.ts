/** Catalog query params that narrow the results. The search text and the sort order are not filters. */
const FILTERS = ['minPrice', 'maxPrice', 'categoryId', 'storeId'] as const;

export const hasFilters = (params: URLSearchParams) => FILTERS.some((key) => params.has(key));

/** The same query without filters, back on the first page. */
export function clearFilters(params: URLSearchParams) {
  const next = new URLSearchParams(params);
  for (const key of [...FILTERS, 'page']) next.delete(key);
  return next;
}
