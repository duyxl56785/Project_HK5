export const domains = [
  { owner: 'TV1', label: 'Tài khoản & nền tảng', path: '/auth', note: 'Auth, User, API client, router và layout' },
  { owner: 'TV2', label: 'Seller & Product', path: '/catalog', note: 'Store, catalog, ảnh và kiểm duyệt' },
  { owner: 'TV3', label: 'Giỏ hàng & Checkout', path: '/checkout', note: 'Cart, tạo đơn và chống đặt trùng' },
  { owner: 'TV4', label: 'Giao hàng & COD', path: '/fulfillment', note: 'Inventory, đơn con và thông báo' },
  { owner: 'TV5', label: 'Review & Finance', path: '/finance', note: 'Đánh giá, phí sàn và đối soát' }
] as const;
