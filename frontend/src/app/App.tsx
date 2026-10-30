import type { ReactElement } from 'react';
import { BrowserRouter, Link, NavLink, Outlet, Route, Routes, useNavigate } from 'react-router-dom';
import { useAuth, Guard } from '../shared/auth';
import { AccountPage } from '../features/account/AccountPage';
import { LoginPage, RegisterPage } from '../features/account/AuthPages';
import { AdminCategoriesPage, AdminFinancePage, AdminOrdersPage, AdminProductsPage, AdminReviewsPage, AdminSellersPage } from '../features/admin/AdminPages';
import { CartPage } from '../features/cart/CartPage';
import { useCart } from '../features/cart/cartApi';
import { CatalogPage } from '../features/catalog/CatalogPage';
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
  return <NavLink to="/cart" aria-label={`Giỏ hàng, ${count} sản phẩm`}>Giỏ hàng{count > 0 && <span className="count">{count}</span>}</NavLink>;
}

function Layout() {
  const { account, has, logout } = useAuth();
  const navigate = useNavigate();
  return <>
    <header className="topbar">
      <nav className="topbar__inner" aria-label="Điều hướng chính">
        <Link to="/" className="brand">HK5 Market</Link>
        {has('CUSTOMER') && <><CartLink /><NavLink to="/orders">Đơn hàng</NavLink></>}
        {account && !has('ADMIN') && <NavLink to="/seller" end>Người bán</NavLink>}
        {has('SELLER') && <><NavLink to="/seller/products">Sản phẩm</NavLink><NavLink to="/seller/orders">Đơn cửa hàng</NavLink><NavLink to="/seller/finance">Tài chính</NavLink></>}
        {has('ADMIN') && <>
          <NavLink to="/admin/sellers">Người bán</NavLink><NavLink to="/admin/products">Sản phẩm</NavLink>
          <NavLink to="/admin/categories">Danh mục</NavLink><NavLink to="/admin/reviews">Đánh giá</NavLink>
          <NavLink to="/admin/orders">COD</NavLink><NavLink to="/admin/finance">Tài chính</NavLink>
        </>}
        <span className="grow" />
        {account ? <>
          <NotificationBell />
          <NavLink to="/account">Tài khoản</NavLink>
          <button type="button" className="link" onClick={() => void logout().finally(() => navigate('/'))}>Đăng xuất</button>
        </> : <><NavLink to="/login">Đăng nhập</NavLink><NavLink to="/register">Đăng ký</NavLink></>}
      </nav>
    </header>
    <main className="container"><Outlet /></main>
  </>;
}

const guarded = (role: string | undefined, page: ReactElement) => <Guard role={role}>{page}</Guard>;

export function App() {
  return <BrowserRouter><Routes>
    <Route element={<Layout />}>
      <Route index element={<CatalogPage />} />
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
      <Route path="admin/orders" element={guarded('ADMIN', <AdminOrdersPage />)} />
      <Route path="admin/finance" element={guarded('ADMIN', <AdminFinancePage />)} />
      <Route path="*" element={<p>Không tìm thấy trang. <Link to="/">Về trang chủ</Link></p>} />
    </Route>
  </Routes></BrowserRouter>;
}
