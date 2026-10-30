import type { ApiClient } from '../../shared/apiClient';
import type { CategoryDto } from './categoryTypes';
import { buildCategoryTree } from './categoryTree.ts';

/** Existing GET /api/categories contract; no Product eligibility or pagination inferred. */
export async function loadPublicCategories(client: Pick<ApiClient, 'request'>, signal?: AbortSignal): Promise<readonly CategoryDto[]> {
  const response = await client.request('/api/categories', { method: 'GET', signal });
  let input: unknown;
  try { input = await response.json(); }
  catch { throw new Error('CATEGORY_RESPONSE_INVALID'); }
  const tree = buildCategoryTree(input);
  const uuid = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
  if (!tree.ok || tree.rows.some(row => !uuid.test(row.id) || row.id.length !== 36)) {
    throw new Error('CATEGORY_RESPONSE_INVALID');
  }
  return tree.rows.map(({ id, name, parentId }) => ({ id, name, parentId }));
}
