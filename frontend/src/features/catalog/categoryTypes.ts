/** GET /api/categories: CategoryResponse(UUID id, String name, UUID parentId, String slug, CategoryStatus status). */
export interface CategoryDto {
  readonly id: string;
  readonly name: string;
  readonly parentId: string | null;
  readonly slug?: string;
  readonly status?: CategoryStatus;
}

export type CategoryStatus = 'ACTIVE' | 'HIDDEN';

export type CategoryBrowserState =
  | { readonly status: 'loading' }
  | { readonly status: 'error'; readonly onRetry: () => void }
  | { readonly status: 'ready'; readonly categories: readonly CategoryDto[] };
