import type { DemoAction, DemoImage, DemoProduct, DemoSession, DemoState } from './demoTypes';
import { createDemoFixtures } from './demoFixtures';
import { canEditProduct, validateProductFields } from '../features/product/productManagementModel';
import type { ProductFields, ValidProductFields } from '../features/product/productManagementTypes';
import { isCanonicalVersion } from '../features/seller/sellerStoreModel';

export function createDemoState(): DemoState { return createDemoFixtures(); }
export function publicProducts(state: DemoState): readonly DemoProduct[] {
  return state.products.filter((row) => {
    const store = state.stores.find((entry) => entry.id === row.storeId);
    const seller = state.sellers.find((entry) => entry.id === store?.sellerId);
    return row.status === 'ACTIVE' && store?.open === true && seller?.status === 'APPROVED'
      && state.categories.some((entry) => entry.id === row.categoryId);
  });
}
export function ownedSeller(state: DemoState, accountId: string) { return state.sellers.find((row) => row.accountId === accountId); }
export function ownedStore(state: DemoState, accountId: string) { return state.stores.find((row) => row.sellerId === ownedSeller(state, accountId)?.id); }

const javaBoundary = /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/gu;
const stripJava = (value: string) => value.replace(javaBoundary, '');
function requireRule(condition: unknown, message: string): asserts condition { if (!condition) throw new Error(message); }
function validUnicode(value: string): boolean {
  for (let i = 0; i < value.length; i++) {
    const code = value.charCodeAt(i);
    if (code === 0 || code >= 0xdc00 && code <= 0xdfff) return false;
    if (code >= 0xd800 && code <= 0xdbff) {
      const next = value.charCodeAt(++i);
      if (!(next >= 0xdc00 && next <= 0xdfff)) return false;
    }
  }
  return true;
}
function nextVersion(actual: string, expected: string | undefined): string {
  requireRule(isCanonicalVersion(actual) && isCanonicalVersion(expected) && actual === expected, 'Dữ liệu đã thay đổi. Vui lòng tải lại rồi thử lại.');
  requireRule(BigInt(actual) < 9223372036854775807n, 'Phiên bản đã đạt giới hạn của bản demo.');
  return String(BigInt(actual) + 1n);
}
function nextId(state: DemoState, prefix: number): string {
  const used = new Set([...state.categories, ...state.sellers, ...state.stores, ...state.products].map((row) => row.id));
  let id = '', index = 1;
  do { id = `${prefix}0000000-0000-4000-8000-${String(index++).padStart(12, '0')}`; } while (used.has(id));
  return id;
}
function requireRole(session: DemoSession, role: DemoSession['role']) {
  requireRule(session.role === role && typeof session.accountId === 'string' && stripJava(session.accountId).length > 0, 'Vai trò hiện tại không được phép thực hiện thao tác này.');
}
function reason(value: string | undefined, product = false): string {
  requireRule(typeof value === 'string' && validUnicode(value), 'Nhập lý do hợp lệ, tối đa 500 ký tự.');
  const stripped = stripJava(value);
  requireRule(stripped.length > 0 && [...(product ? value : stripped)].length <= 500, 'Nhập lý do hợp lệ, tối đa 500 ký tự.');
  return stripped;
}
function approvedOwner(state: DemoState, session: DemoSession) {
  requireRole(session, 'seller');
  const seller = ownedSeller(state, session.accountId);
  requireRule(seller?.status === 'APPROVED', 'Tài khoản người bán cần được duyệt trước khi quản lý cửa hàng.');
  return seller;
}
function managedStore(state: DemoState, session: DemoSession, storeId: string) {
  const seller = approvedOwner(state, session);
  const store = state.stores.find((row) => row.id === storeId);
  requireRule(store && store.sellerId === seller.id, 'Bạn không sở hữu cửa hàng này.');
  return store;
}
function managedProduct(state: DemoState, session: DemoSession, item: DemoProduct) {
  requireRule(managedStore(state, session, item.storeId).open, 'Mở cửa hàng trước khi quản lý sản phẩm.');
}
function validatedFields(state: DemoState, fields: ProductFields): ValidProductFields {
  const result = validateProductFields(fields, state.categories);
  requireRule(result.value, Object.values(result.errors)[0] ?? 'Thông tin sản phẩm không hợp lệ.');
  return result.value;
}
function validatedImages(images: readonly DemoImage[]): readonly DemoImage[] {
  requireRule(Array.isArray(images) && images.length <= 5, 'Mỗi sản phẩm có tối đa 5 ảnh.');
  const ids = new Set<string>();
  return images.map((image) => {
    const extension = typeof image.name === 'string' ? image.name.split('.').at(-1)?.toLowerCase() : '';
    const media = extension === 'jpg' || extension === 'jpeg' ? 'image/jpeg' : extension === 'png' ? 'image/png' : extension === 'webp' ? 'image/webp' : '';
    requireRule(typeof image.id === 'string' && image.id.length > 0 && !ids.has(image.id)
      && typeof image.url === 'string' && /^blob:\S+$/.test(image.url)
      && typeof image.name === 'string' && image.name.length > 0 && image.name.length <= 255
      && validUnicode(image.name) && !/[\\/\u0000-\u001f\u007f]/u.test(image.name)
      && !!media && image.mediaType === media && Number.isInteger(image.sizeBytes)
      && image.sizeBytes > 0 && image.sizeBytes <= 2 * 1024 * 1024, 'Ảnh cần có tên hợp lệ, định dạng JPEG/PNG/WebP và dung lượng tối đa 2 MiB.');
    ids.add(image.id);
    return { id: image.id, url: image.url, name: image.name, mediaType: image.mediaType, sizeBytes: image.sizeBytes };
  });
}

export function transition(state: DemoState, session: DemoSession, action: DemoAction): DemoState {
  const result = { ...state, revision: state.revision + 1 };
  switch (action.type) {
    case 'seller.register': {
      requireRole(session, 'seller');
      requireRule(!ownedSeller(state, session.accountId), 'Tài khoản này đã có hồ sơ người bán.');
      return { ...result, sellers: [...state.sellers, { id: nextId(state, 2), accountId: session.accountId, version: '0', status: 'PENDING_REVIEW', reviewReason: null }] };
    }
    case 'seller.resubmit': {
      requireRole(session, 'seller');
      const seller = state.sellers.find((row) => row.id === action.sellerId);
      requireRule(seller && seller.accountId === session.accountId, 'Bạn không sở hữu hồ sơ người bán này.');
      const version = nextVersion(seller.version, action.expectedVersion);
      requireRule(seller.status === 'REJECTED', 'Chỉ hồ sơ bị từ chối mới được gửi lại.');
      return { ...result, sellers: state.sellers.map((row) => row.id === seller.id ? { ...row, version, status: 'PENDING_REVIEW', reviewReason: null } : row) };
    }
    case 'seller.moderate': {
      requireRole(session, 'admin');
      const seller = state.sellers.find((row) => row.id === action.sellerId);
      requireRule(seller, 'Không tìm thấy hồ sơ người bán.');
      requireRule(seller.accountId !== session.accountId, 'Không được tự kiểm duyệt hồ sơ của mình.');
      const version = nextVersion(seller.version, action.expectedVersion);
      const source = { approve: 'PENDING_REVIEW', reject: 'PENDING_REVIEW', suspend: 'APPROVED', reinstate: 'SUSPENDED' } as const;
      const target = { approve: 'APPROVED', reject: 'REJECTED', suspend: 'SUSPENDED', reinstate: 'APPROVED' } as const;
      requireRule(seller.status === source[action.decision], 'Trạng thái hồ sơ không cho phép thao tác này.');
      const reviewReason = action.decision === 'reject' || action.decision === 'suspend' ? reason(action.reason) : null;
      return { ...result, sellers: state.sellers.map((row) => row.id === seller.id ? { ...row, version, status: target[action.decision], reviewReason } : row) };
    }
    case 'store.save': {
      const seller = approvedOwner(state, session);
      const name = stripJava(action.name);
      requireRule(name.length > 0 && name.length <= 255 && validUnicode(name), 'Nhập tên cửa hàng từ 1–255 ký tự.');
      requireRule(action.slug.length <= 128 && /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(action.slug), 'Đường dẫn chỉ gồm chữ thường không dấu, số và dấu gạch nối; tối đa 128 ký tự.');
      requireRule(!state.stores.some((row) => row.slug === action.slug && row.id !== action.storeId), 'Đường dẫn cửa hàng đã được sử dụng.');
      if (action.storeId) {
        const store = managedStore(state, session, action.storeId);
        const version = nextVersion(store.version, action.expectedVersion);
        return { ...result, stores: state.stores.map((row) => row.id === store.id ? { ...row, name, slug: action.slug, version } : row) };
      }
      requireRule(!state.stores.some((row) => row.sellerId === seller.id), 'Mỗi người bán chỉ được tạo một cửa hàng.');
      return { ...result, stores: [...state.stores, { id: nextId(state, 3), sellerId: seller.id, name, slug: action.slug, open: true, version: '0' }] };
    }
    case 'store.toggle': {
      const store = managedStore(state, session, action.storeId);
      const version = nextVersion(store.version, action.expectedVersion);
      requireRule(typeof action.open === 'boolean', 'Trạng thái cửa hàng không hợp lệ.');
      return { ...result, stores: state.stores.map((row) => row.id === store.id ? { ...row, open: action.open, version } : row) };
    }
    case 'product.create': {
      const store = managedStore(state, session, action.fields.storeId);
      requireRule(store.open, 'Mở cửa hàng trước khi tạo sản phẩm.');
      const fields = validatedFields(state, action.fields);
      requireRule(!state.products.some((row) => row.sku === fields.sku), 'SKU đã được sử dụng. SKU phân biệt chữ hoa và chữ thường.');
      const item: DemoProduct = { id: nextId(state, 4), storeId: store.id, categoryId: fields.categoryId, sku: fields.sku,
        name: fields.name, unitPrice: fields.unitPrice, currency: fields.currency, status: 'DRAFT', version: '0',
        description: 'Sản phẩm mới của tiệm, được tạo trong bản demo. Hãy hoàn thiện thông tin và gửi duyệt khi sẵn sàng.', art: 'vase', images: [], reviewReason: null };
      return { ...result, products: [...state.products, item] };
    }
    case 'product.update':
    case 'product.submit':
    case 'product.deactivate':
    case 'product.moderate':
    case 'product.images': {
      const id = action.type === 'product.update' || action.type === 'product.submit' ? action.fields.productId : action.productId;
      const expectedVersion = action.type === 'product.update' || action.type === 'product.submit' ? action.fields.expectedVersion : action.expectedVersion;
      const item = state.products.find((row) => row.id === id);
      requireRule(item, 'Không tìm thấy sản phẩm.');
      const version = nextVersion(item.version, expectedVersion);
      let changed: DemoProduct = { ...item, version };
      if (action.type === 'product.moderate') {
        requireRole(session, 'admin');
        const store = state.stores.find((row) => row.id === item.storeId);
        const seller = state.sellers.find((row) => row.id === store?.sellerId);
        requireRule(store && seller, 'Không thể xác nhận cửa hàng của sản phẩm.');
        requireRule(seller.accountId !== session.accountId, 'Không được tự kiểm duyệt sản phẩm của mình.');
        requireRule(item.status === (action.decision === 'suspend' ? 'ACTIVE' : 'PENDING_REVIEW'), 'Trạng thái sản phẩm không cho phép thao tác này.');
        if (action.decision === 'approve') {
          requireRule(state.categories.some((row) => row.id === item.categoryId), 'Danh mục sản phẩm không còn tồn tại.');
          changed = { ...changed, status: 'ACTIVE', reviewReason: null };
        } else changed = { ...changed, status: action.decision === 'reject' ? 'REJECTED' : 'SUSPENDED', reviewReason: reason(action.reason, true) };
      } else {
        managedProduct(state, session, item);
        if (action.type === 'product.deactivate') {
          requireRule(item.status === 'ACTIVE', 'Chỉ sản phẩm đang bán mới được ngừng bán.');
          changed = { ...changed, status: 'INACTIVE', reviewReason: null };
        } else {
          requireRule(canEditProduct(item.status), 'Chỉ sản phẩm nháp, bị từ chối hoặc ngừng bán mới được chỉnh sửa và gửi duyệt.');
          if (action.type === 'product.update') {
            requireRule(!('sku' in action.fields) && !('storeId' in action.fields), 'Không được thay đổi SKU hoặc cửa hàng của sản phẩm.');
            const fields = validatedFields(state, { ...action.fields, sku: item.sku });
            changed = { ...changed, name: fields.name, categoryId: fields.categoryId, unitPrice: fields.unitPrice, currency: fields.currency };
          } else if (action.type === 'product.images') changed = { ...changed, images: validatedImages(action.images) };
          else {
            requireRule(state.categories.some((row) => row.id === item.categoryId), 'Danh mục sản phẩm không còn tồn tại.');
            changed = { ...changed, status: 'PENDING_REVIEW', reviewReason: null };
          }
        }
      }
      return { ...result, products: state.products.map((row) => row.id === id ? changed : row) };
    }
    case 'category.save': {
      requireRole(session, 'admin');
      requireRule(typeof action.name === 'string' && action.name.length <= 255 && stripJava(action.name).length > 0 && validUnicode(action.name), 'Nhập tên danh mục từ 1–255 ký tự.');
      requireRule(!action.id || state.categories.some((row) => row.id === action.id), 'Không tìm thấy danh mục.');
      const id = action.id ?? nextId(state, 1), visited = new Set<string>();
      let parentId = action.parentId;
      while (parentId !== null) {
        requireRule(parentId !== id && !visited.has(parentId), 'Danh mục cha không được tạo vòng lặp.');
        visited.add(parentId);
        const parent = state.categories.find((row) => row.id === parentId);
        requireRule(parent, 'Danh mục cha không tồn tại.');
        parentId = parent.parentId;
      }
      const category = { id, name: stripJava(action.name), parentId: action.parentId };
      return { ...result, categories: action.id ? state.categories.map((row) => row.id === id ? category : row) : [...state.categories, category] };
    }
    case 'category.delete': {
      requireRole(session, 'admin');
      requireRule(state.categories.some((row) => row.id === action.id), 'Không tìm thấy danh mục.');
      requireRule(!state.categories.some((row) => row.parentId === action.id), 'Xóa hoặc chuyển danh mục con trước khi xóa danh mục này.');
      requireRule(!state.products.some((row) => row.categoryId === action.id), 'Danh mục đang được sản phẩm sử dụng nên chưa thể xóa.');
      return { ...result, categories: state.categories.filter((row) => row.id !== action.id) };
    }
    default: throw new Error('Thao tác không được hỗ trợ trong bản demo.');
  }
}
