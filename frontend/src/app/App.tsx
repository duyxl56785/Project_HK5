import { useDeferredValue, useEffect, useState } from 'react';
import type { FormEvent, ReactElement } from 'react';
import { useQuery } from '@tanstack/react-query';
import { BrowserRouter, Link, NavLink, Outlet, Route, Routes, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../shared/api';
import { useAuth, Guard } from '../shared/auth';
import { AccountPage } from '../features/account/AccountPage';
import { LoginPage, RegisterPage } from '../features/account/AuthPages';
import { AdminAccountsPage, AdminCategoriesPage, AdminFinancePage, AdminOrdersPage, AdminProductsPage, AdminReviewsPage, AdminSellersPage } from '../features/admin/AdminPages';
import { CartPage } from '../features/cart/CartPage';
import { useCart } from '../features/cart/cartApi';
import { CatalogPage } from '../features/catalog/CatalogPage';
import { HomePage } from '../features/catalog/HomePage';
import { ProductPage } from '../features/catalog/ProductPage';
import { StorePage } from '../features/catalog/StorePage';
import { CheckoutPage } from '../features/checkout/CheckoutPage';
import { NotificationBell, NotificationsPage } from '../features/notifications/Notifications';
import { OrderDetailPage, OrdersPage } from '../features/orders/OrdersPage';
import { SellerHomePage } from '../features/seller/SellerHomePage';
import { SellerFinancePage, SellerOrdersPage } from '../features/seller/SellerOrdersPage';
import { SellerProductPage, SellerProductsPage } from '../features/seller/SellerProductsPage';

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
    <main className="container"><Outlet /></main>
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
