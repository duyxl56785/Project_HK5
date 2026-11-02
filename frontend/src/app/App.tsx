import { Component, lazy, Suspense, useDeferredValue, useEffect, useState } from 'react';
import type { ComponentType, FormEvent, ReactElement, ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { BrowserRouter, Link, NavLink, Outlet, Route, Routes, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../shared/api';
import { useAuth, Guard } from '../shared/auth';
import { useCart } from '../features/cart/cartApi';
import { NotificationBell, NotificationsPage } from '../features/notifications/Notifications';

const page = <K extends string>(load: () => Promise<Record<K, ComponentType>>, name: K) =>
  lazy(() => load().then((module) => ({ default: module[name] })));

const accountPages = () => import('../features/account/AccountPage');
const authPages = () => import('../features/account/AuthPages');
const adminPages = () => import('../features/admin/AdminPages');
const orderPages = () => import('../features/orders/OrdersPage');
const sellerOrderPages = () => import('../features/seller/SellerOrdersPage');
const sellerProductPages = () => import('../features/seller/SellerProductsPage');

const AccountPage = page(accountPages, 'AccountPage');
const LoginPage = page(authPages, 'LoginPage');
const RegisterPage = page(authPages, 'RegisterPage');
const AdminAccountsPage = page(adminPages, 'AdminAccountsPage');
const AdminCategoriesPage = page(adminPages, 'AdminCategoriesPage');
const AdminFinancePage = page(adminPages, 'AdminFinancePage');
const AdminOrdersPage = page(adminPages, 'AdminOrdersPage');
const AdminProductsPage = page(adminPages, 'AdminProductsPage');
const AdminReviewsPage = page(adminPages, 'AdminReviewsPage');
const AdminSellersPage = page(adminPages, 'AdminSellersPage');
const CartPage = page(() => import('../features/cart/CartPage'), 'CartPage');
const CatalogPage = page(() => import('../features/catalog/CatalogPage'), 'CatalogPage');
const HomePage = page(() => import('../features/catalog/HomePage'), 'HomePage');
const ProductPage = page(() => import('../features/catalog/ProductPage'), 'ProductPage');
const StorePage = page(() => import('../features/catalog/StorePage'), 'StorePage');
const CheckoutPage = page(() => import('../features/checkout/CheckoutPage'), 'CheckoutPage');
const OrderDetailPage = page(orderPages, 'OrderDetailPage');
const OrdersPage = page(orderPages, 'OrdersPage');
const SellerHomePage = page(() => import('../features/seller/SellerHomePage'), 'SellerHomePage');
const SellerFinancePage = page(sellerOrderPages, 'SellerFinancePage');
const SellerOrdersPage = page(sellerOrderPages, 'SellerOrdersPage');
const SellerProductPage = page(sellerProductPages, 'SellerProductPage');
const SellerProductsPage = page(sellerProductPages, 'SellerProductsPage');

/** A page chunk can fail to load, for example after a new deploy; one broken page must not blank the app. */
class PageBoundary extends Component<{ readonly children: ReactNode }, { readonly failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() {
    if (!this.state.failed) return this.props.children;
    return <div role="alert" className="stack">
      <p>Không tải được trang này.</p>
      <div><button type="button" className="btn btn--primary" onClick={() => location.reload()}>Tải lại</button></div>
    </div>;
  }
}

function CartLink() {
  const cart = useCart();
  const count = cart.data?.snapshot.items.length ?? 0;
  return <NavLink to="/cart" className="cartlink" aria-label={`Giỏ hàng, ${count} sản phẩm`}>
    <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="9" cy="20" r="1.5" /><circle cx="18" cy="20" r="1.5" /><path d="M2 3h3l2.7 12.5h11L21 7H6" />
    </svg>
    {count > 0 && <span className="count">{count}</span>}
  </NavLink>;
}

function SearchBar() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const current = params.get('q') ?? '';
  const [term, setTerm] = useState(current);
  useEffect(() => setTerm(current), [current]);
  const deferred = useDeferredValue(term.trim());
  const suggestions = useQuery({
    queryKey: ['suggestions', deferred], enabled: deferred.length >= 2,
    queryFn: () => api<{ items: { id: string; name: string }[] }>(`/api/catalog/products?size=5&q=${encodeURIComponent(deferred)}`),
  });
  function submit(event: FormEvent) {
    event.preventDefault();
    navigate(term.trim() ? `/products?q=${encodeURIComponent(term.trim())}` : '/products');
  }
  return <form className="search" role="search" onSubmit={submit}>
    <input aria-label="Tìm sản phẩm" placeholder="Tìm sản phẩm trên Soopi" maxLength={100} list="search-suggestions" value={term} onChange={(e) => setTerm(e.currentTarget.value)} />
    <datalist id="search-suggestions">{suggestions.data?.items.map((p) => <option key={p.id} value={p.name} />)}</datalist>
    <button aria-label="Tìm kiếm">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" aria-hidden="true">
        <circle cx="11" cy="11" r="7" /><path d="m20 20-3.8-3.8" />
      </svg>
    </button>
  </form>;
}

function Layout() {
  const { account, has, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  return <>
    <header className="topbar">
      <nav className="topbar__strip" aria-label="Điều hướng chính">
        {has('CUSTOMER') && <NavLink to="/orders">Đơn hàng</NavLink>}
        {!has('ADMIN') && <NavLink to="/seller" end>Kênh người bán</NavLink>}
        {has('SELLER') && <><NavLink to="/seller/products">Sản phẩm</NavLink><NavLink to="/seller/orders">Đơn cửa hàng</NavLink><NavLink to="/seller/finance">Tài chính</NavLink></>}
        {has('ADMIN') && <>
          <NavLink to="/admin/sellers">Người bán</NavLink><NavLink to="/admin/products">Sản phẩm</NavLink>
          <NavLink to="/admin/categories">Danh mục</NavLink><NavLink to="/admin/reviews">Đánh giá</NavLink>
          <NavLink to="/admin/accounts">Tài khoản</NavLink><NavLink to="/admin/orders">COD</NavLink><NavLink to="/admin/finance">Tài chính</NavLink>
        </>}
        <span className="grow" />
        {account ? <>
          <NotificationBell />
          <NavLink to="/account">Tài khoản</NavLink>
          <button type="button" className="link" onClick={() => void logout().finally(() => navigate('/'))}>Đăng xuất</button>
        </> : <><NavLink to="/login">Đăng nhập</NavLink><NavLink to="/register">Đăng ký</NavLink></>}
      </nav>
      <div className="topbar__main">
        <Link to="/" className="brand" aria-label="Soopi, trang chủ">
          <svg width="34" height="34" viewBox="0 0 32 32" aria-hidden="true">
            <path d="M6 11h20l-1.6 17H7.6z" fill="currentColor" /><path d="M11 11c0-8 10-8 10 0" fill="none" stroke="currentColor" strokeWidth="2.4" />
          </svg>
          Soopi
        </Link>
        <SearchBar />
        {has('CUSTOMER') && <CartLink />}
      </div>
    </header>
    <main className="container">
      <PageBoundary key={pathname}><Suspense fallback={<p role="status">Đang tải…</p>}><Outlet /></Suspense></PageBoundary>
    </main>
    <footer className="footer">© 2026 Soopi · Sàn thương mại điện tử nhiều người bán · Thanh toán khi nhận hàng</footer>
  </>;
}

const guarded = (role: string | undefined, page: ReactElement) => <Guard role={role}>{page}</Guard>;

export function App() {
  return <BrowserRouter><Routes>
    <Route element={<Layout />}>
      <Route index element={<HomePage />} />
      <Route path="products" element={<CatalogPage />} />
      <Route path="products/:id" element={<ProductPage />} />
      <Route path="stores/:slug" element={<StorePage />} />
      <Route path="login" element={<LoginPage />} />
      <Route path="register" element={<RegisterPage />} />
      <Route path="account" element={guarded(undefined, <AccountPage />)} />
      <Route path="notifications" element={guarded(undefined, <NotificationsPage />)} />
      <Route path="cart" element={guarded('CUSTOMER', <CartPage />)} />
      <Route path="checkout" element={guarded('CUSTOMER', <CheckoutPage />)} />
      <Route path="orders" element={guarded('CUSTOMER', <OrdersPage />)} />
      <Route path="orders/:id" element={guarded('CUSTOMER', <OrderDetailPage />)} />
      <Route path="seller" element={guarded(undefined, <SellerHomePage />)} />
      <Route path="seller/products" element={guarded('SELLER', <SellerProductsPage />)} />
      <Route path="seller/products/new" element={guarded('SELLER', <SellerProductPage />)} />
      <Route path="seller/products/:id" element={guarded('SELLER', <SellerProductPage />)} />
      <Route path="seller/orders" element={guarded('SELLER', <SellerOrdersPage />)} />
      <Route path="seller/finance" element={guarded('SELLER', <SellerFinancePage />)} />
      <Route path="admin/sellers" element={guarded('ADMIN', <AdminSellersPage />)} />
      <Route path="admin/products" element={guarded('ADMIN', <AdminProductsPage />)} />
      <Route path="admin/categories" element={guarded('ADMIN', <AdminCategoriesPage />)} />
      <Route path="admin/reviews" element={guarded('ADMIN', <AdminReviewsPage />)} />
      <Route path="admin/accounts" element={guarded('ADMIN', <AdminAccountsPage />)} />
      <Route path="admin/orders" element={guarded('ADMIN', <AdminOrdersPage />)} />
      <Route path="admin/finance" element={guarded('ADMIN', <AdminFinancePage />)} />
      <Route path="*" element={<p>Không tìm thấy trang. <Link to="/">Về trang chủ</Link></p>} />
    </Route>
  </Routes></BrowserRouter>;
}
