import type { CategoryDto } from '../catalog/categoryTypes.ts';
import type { StoreSummary } from '../seller/sellerStoreTypes.ts';

/** UI projection only. These types do not define an approved HTTP contract. */
export type ProductStatus = 'DRAFT' | 'PENDING_REVIEW' | 'ACTIVE' | 'REJECTED' | 'SUSPENDED' | 'INACTIVE';
export interface ProductSummary {
  readonly id: string;
  readonly storeId: string;
  readonly categoryId: string;
  readonly sku: string;
  readonly name: string;
  readonly unitPrice: string;
  readonly currency: 'VND';
  readonly status: ProductStatus;
  /** Exact nonnegative Java long decimal string; never convert to Number. */
  readonly version: string;
}
export type ProductStore = Pick<StoreSummary, 'id' | 'name'>;
export interface ProductManagementData {
  readonly store: ProductStore;
  readonly products: readonly ProductSummary[];
  readonly categories: readonly CategoryDto[];
}
export type ProductManagementState =
  | { readonly status: 'loading' | 'error' | 'unauthorized' | 'unavailable' }
  | ({ readonly status: 'ready' } & ProductManagementData);
export interface ProductFields {
  readonly name: string;
  readonly sku: string;
  readonly categoryId: string;
  readonly unitPrice: string;
  readonly currency: string;
}
export type ValidProductFields = ProductFields & { readonly currency: 'VND' };
export type ProductFieldErrors = Partial<Record<keyof ProductFields, string>>;
export type ProductMutationResult = { readonly status: 'confirmed' | 'error' | 'conflict' | 'unauthorized' | 'unavailable' };
export interface CreateProductCommand extends ValidProductFields { readonly storeId: string }
/** Store and SKU cannot be edited. Identity/roles are supplied by the verified host adapter. */
export interface UpdateProductCommand extends Omit<ValidProductFields, 'sku'> {
  readonly productId: string;
  readonly expectedVersion: string;
}
export interface SubmitProductCommand { readonly productId: string; readonly expectedVersion: string }
export interface ProductManagementActions {
  readonly create?: (command: CreateProductCommand) => Promise<ProductMutationResult>;
  readonly update?: (command: UpdateProductCommand) => Promise<ProductMutationResult>;
  readonly submitForReview?: (command: SubmitProductCommand) => Promise<ProductMutationResult>;
}
