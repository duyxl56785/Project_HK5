import type { CategoryDto } from '../features/catalog/categoryTypes';
import type { SellerSummary, StoreSummary } from '../features/seller/sellerStoreTypes';
import type { CreateProductCommand, ProductSummary, SubmitProductCommand, UpdateProductCommand } from '../features/product/productManagementTypes';

export type DemoRole = 'customer' | 'seller' | 'admin';
export interface DemoSession { role: DemoRole; accountId: string }
export interface DemoSeller extends SellerSummary { accountId: string; reviewReason: string | null }
export interface DemoImage { id: string; url: string; name: string; mediaType: string; sizeBytes: number }
export type ProductArt = 'vase' | 'cup' | 'lamp' | 'bag' | 'linen' | 'plant';
export interface DemoProduct extends ProductSummary {
  description: string;
  art: ProductArt;
  reviewReason: string | null;
  images: readonly DemoImage[];
}
export interface DemoState {
  revision: number;
  categories: readonly CategoryDto[];
  sellers: readonly DemoSeller[];
  stores: readonly StoreSummary[];
  products: readonly DemoProduct[];
}
export type DemoAction =
  | { type: 'seller.register' }
  | { type: 'seller.resubmit'; sellerId: string; expectedVersion: string }
  | { type: 'seller.moderate'; sellerId: string; expectedVersion: string; decision: 'approve' | 'reject' | 'suspend' | 'reinstate'; reason?: string }
  | { type: 'store.save'; storeId?: string; expectedVersion?: string; name: string; slug: string }
  | { type: 'store.toggle'; storeId: string; expectedVersion: string; open: boolean }
  | { type: 'product.create'; fields: CreateProductCommand }
  | { type: 'product.update'; fields: UpdateProductCommand }
  | { type: 'product.submit'; fields: SubmitProductCommand }
  | { type: 'product.deactivate'; productId: string; expectedVersion: string }
  | { type: 'product.moderate'; productId: string; expectedVersion: string; decision: 'approve' | 'reject' | 'suspend'; reason?: string }
  | { type: 'product.images'; productId: string; expectedVersion: string; images: readonly DemoImage[] }
  | { type: 'category.save'; id?: string; name: string; parentId: string | null }
  | { type: 'category.delete'; id: string };
export interface DemoPageProps {
  state: DemoState;
  session: DemoSession;
  dispatch: (action: DemoAction, message?: string) => boolean;
  navigate: (path: string) => void;
}

export const DEMO_ACCOUNTS = [
  { id: 'seller-a', label: 'Seller A · Nếp Nhà' },
  { id: 'seller-b', label: 'Seller B · Mộc Studio' },
  { id: 'seller-new', label: 'Người bán mới' },
] as const;
