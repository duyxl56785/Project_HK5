// Test/preview only: never exported from the feature entry or used by the app.
export const store = { id: '11111111-1111-4111-8111-111111111111', name: 'Gốm Việt Studio' };
export const categories = [
  { id: '22222222-2222-4222-8222-222222222222', name: 'Đồ gia dụng', parentId: null },
  { id: '33333333-3333-4333-8333-333333333333', name: 'Gốm thủ công', parentId: '22222222-2222-4222-8222-222222222222' },
];
export const product = { id: '44444444-4444-4444-8444-444444444444', storeId: store.id,
  categoryId: categories[1].id, name: 'Cốc gốm men mây', sku: 'GOM-MAY-01', unitPrice: '125000', currency: 'VND', status: 'DRAFT', version: '9007199254740993' };
export const ready = { status: 'ready', store, products: [product], categories };
