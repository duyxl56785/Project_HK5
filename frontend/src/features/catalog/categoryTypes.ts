/** GET /api/categories: CategoryResponse(UUID id, String name, UUID parentId). */
export interface CategoryDto {
  readonly id: string;
  readonly name: string;
  readonly parentId: string | null;
}

export type CategoryBrowserState =
  | { readonly status: 'loading' }
  | { readonly status: 'error'; readonly onRetry: () => void }
  | { readonly status: 'ready'; readonly categories: readonly CategoryDto[] };
