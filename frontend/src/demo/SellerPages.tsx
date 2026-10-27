import { useState } from 'react';
import { SellerStoreOverview } from '../features/seller/SellerStoreOverview';
import { ownedSeller, ownedStore } from './demoModel';
import { DEMO_ACCOUNTS } from './demoTypes';
import type { DemoPageProps } from './demoTypes';
import type { StoreSummary } from '../features/seller/sellerStoreTypes';

function SellerOnly() {
  return <div className="demo-empty"><h2>Khu vực người bán</h2><p>Chuyển sang vai trò Người bán để xem hồ sơ của bạn.</p></div>;
}

export function SellerDashboard(props: DemoPageProps) {
  if (props.session.role !== 'seller') return <SellerOnly />;
  const seller = ownedSeller(props.state, props.session.accountId);
  const store = ownedStore(props.state, props.session.accountId);
  const products = store ? props.state.products.filter((product) => product.storeId === store.id) : [];
  return <>
    <header className="demo-page-head"><span className="demo-eyebrow">Góc người bán</span><h1>Một góc nhỏ, nhiều điều hay</h1>
      <p>Chăm chút cửa hàng và theo dõi sản phẩm của riêng bạn.</p>
      <div className="demo-actions"><button className="demo-button" onClick={() => props.navigate('/seller/products')}>Quản lý sản phẩm</button>
        <button className="demo-button secondary" onClick={() => props.navigate('/seller/store')}>Cửa hàng của tôi</button></div>
    </header>
    <div className="demo-stats" aria-label="Thống kê cửa hàng hiện tại">
      {[['Tổng sản phẩm', products.length], ['Đang bán', products.filter((item) => item.status === 'ACTIVE').length],
        ['Chờ duyệt', products.filter((item) => item.status === 'PENDING_REVIEW').length],
        ['Cần xử lý', products.filter((item) => item.status === 'REJECTED' || item.status === 'SUSPENDED').length],
        ['Bản nháp', products.filter((item) => item.status === 'DRAFT').length]].map(([label, value]) =>
        <article className="demo-stat" key={label}><span>{label}</span><strong>{value}</strong></article>)}
    </div>
    <SellerStoreOverview state={seller ? { status: 'ready', seller, store: store ?? null } : { status: 'empty' }} />
    {!seller && <div className="demo-callout"><h2>Bắt đầu hành trình bán hàng</h2><p>Đăng ký hồ sơ, đợi quản trị viên duyệt rồi tạo cửa hàng của bạn.</p>
      <button className="demo-button" onClick={() => props.navigate('/seller/profile')}>Đăng ký người bán</button></div>}
    {seller && seller.status !== 'APPROVED' && <div className="demo-callout"><p>Hồ sơ cần được duyệt trước khi tạo cửa hàng hoặc quản lý sản phẩm.</p>
      <button className="demo-button secondary" onClick={() => props.navigate('/seller/profile')}>Xem hồ sơ</button></div>}
    <p className="demo-note">Đây là dữ liệu demo trong trình duyệt. Các thao tác không gửi đến máy chủ.</p>
  </>;
}

export function SellerProfilePage(props: DemoPageProps) {
  if (props.session.role !== 'seller') return <SellerOnly />;
  return <SellerProfile key={props.session.accountId} {...props} />;
}

function SellerProfile(props: DemoPageProps) {
  const [consent, setConsent] = useState(false);
  const seller = ownedSeller(props.state, props.session.accountId);
  const store = ownedStore(props.state, props.session.accountId);
  const account = DEMO_ACCOUNTS.find((item) => item.id === props.session.accountId);
  return <>
    <header className="demo-page-head"><span className="demo-eyebrow">Thông tin người bán</span><h1>Hồ sơ của bạn</h1>
      <p>Mỗi cửa hàng bắt đầu bằng một hồ sơ được xét duyệt.</p></header>
    <section className="demo-panel"><h2>Tài khoản demo</h2><p><strong>{account?.label ?? props.session.accountId}</strong></p>
      <p>Hồ sơ dùng tài khoản đang chọn. Bản demo không thu thập thêm thông tin cá nhân.</p></section>
    <SellerStoreOverview state={seller ? { status: 'ready', seller, store: store ?? null } : { status: 'empty' }} />
    {seller?.reviewReason && <div className="demo-callout"><h2>Phản hồi xét duyệt</h2><p>{seller.reviewReason}</p></div>}
    {(!seller || seller.status === 'REJECTED') && <form className="demo-panel demo-form" onSubmit={(event) => {
      event.preventDefault();
      if (!consent) return;
      const confirmed = props.dispatch(seller ? { type: 'seller.resubmit', sellerId: seller.id, expectedVersion: seller.version }
        : { type: 'seller.register' }, seller ? 'Đã gửi lại hồ sơ để xét duyệt.' : 'Đã gửi đăng ký người bán.');
      if (confirmed) setConsent(false);
    }}><h2>{seller ? 'Gửi lại hồ sơ' : 'Đăng ký người bán'}</h2>
      <label className="demo-field"><span><input type="checkbox" required checked={consent} onChange={(event) => setConsent(event.target.checked)} /> Tôi đồng ý gửi hồ sơ demo để quản trị viên xét duyệt.</span></label>
      <div><button className="demo-button" disabled={!consent}>{seller ? 'Gửi lại để xét duyệt' : 'Gửi đăng ký'}</button></div>
    </form>}
    {seller?.status === 'PENDING_REVIEW' && <p className="demo-callout" role="status">Hồ sơ đang chờ xét duyệt. Bạn có thể chuyển sang Quản trị để trải nghiệm bước duyệt hồ sơ.</p>}
    {seller?.status === 'APPROVED' && <div className="demo-actions"><button className="demo-button" onClick={() => props.navigate('/seller/store')}>{store ? 'Quản lý cửa hàng' : 'Tạo cửa hàng'}</button></div>}
    {seller?.status === 'SUSPENDED' && <p className="demo-callout">Hồ sơ đang tạm ngừng. Quản trị viên cần khôi phục hồ sơ trước khi bạn tiếp tục bán hàng.</p>}
  </>;
}

export function StorePage(props: DemoPageProps) {
  if (props.session.role !== 'seller') return <SellerOnly />;
  const seller = ownedSeller(props.state, props.session.accountId);
  const store = ownedStore(props.state, props.session.accountId);
  return <>
    <header className="demo-page-head"><span className="demo-eyebrow">Không gian của bạn</span><h1>Cửa hàng của tôi</h1><p>Một cái tên đáng nhớ cho những món đồ bạn yêu thích.</p></header>
    {seller?.status === 'APPROVED' ? <StoreForm key={`${props.session.accountId}:${store?.id ?? 'new'}:${store?.version ?? ''}`} {...props} store={store} />
      : <div className="demo-empty"><h2>Hồ sơ cần được phê duyệt</h2><p>Chỉ người bán đã duyệt mới có thể tạo, chỉnh sửa hoặc mở cửa hàng.</p>
        <button className="demo-button" onClick={() => props.navigate('/seller/profile')}>Xem hồ sơ người bán</button></div>}
  </>;
}

function StoreForm({ store, ...props }: DemoPageProps & { store: StoreSummary | undefined | null }) {
  const [name, setName] = useState(store?.name ?? '');
  const [slug, setSlug] = useState(store?.slug ?? '');
  return <div className="demo-grid">
    <form className="demo-panel demo-form" onSubmit={(event) => {
      event.preventDefault();
      props.dispatch({ type: 'store.save', storeId: store?.id, expectedVersion: store?.version, name, slug }, store ? 'Đã cập nhật cửa hàng.' : 'Đã tạo cửa hàng.');
    }}><h2>{store ? 'Thông tin cửa hàng' : 'Tạo cửa hàng đầu tiên'}</h2>
      <label className="demo-field"><span>Tên cửa hàng</span><input className="demo-input" required maxLength={255} value={name} onChange={(event) => setName(event.target.value)} placeholder="Ví dụ: Nếp Nhà" /></label>
      <label className="demo-field"><span>Định danh đường dẫn</span><input className="demo-input" required maxLength={128} pattern="[a-z0-9]+(-[a-z0-9]+)*" value={slug} onChange={(event) => setSlug(event.target.value)} placeholder="vi-du-nep-nha" aria-describedby="store-slug-help" /></label>
      <p id="store-slug-help">Dùng chữ thường không dấu, số và dấu gạch ngang. Ví dụ: nep-nha.</p>
      <div><button className="demo-button">{store ? 'Lưu thay đổi' : 'Tạo cửa hàng'}</button></div>
    </form>
    <section className="demo-panel"><h2>Trạng thái hoạt động</h2>{store ? <>
      <span className="demo-badge" data-tone={store.open ? 'success' : 'neutral'}>{store.open ? 'Đang mở cửa' : 'Đang đóng cửa'}</span>
      <p>{store.open ? 'Sản phẩm đang bán có thể xuất hiện trong danh mục khách hàng khi hồ sơ người bán hợp lệ.' : 'Cửa hàng đang đóng. Sản phẩm sẽ không xuất hiện trong danh mục khách hàng.'}</p>
      <button className={`demo-button ${store.open ? 'secondary' : ''}`} onClick={() => props.dispatch({ type: 'store.toggle', storeId: store.id, expectedVersion: store.version, open: !store.open }, store.open ? 'Đã đóng cửa hàng.' : 'Đã mở cửa hàng.')}>
        {store.open ? 'Đóng cửa hàng' : 'Mở cửa hàng'}</button>
      <p className="demo-note">Thay đổi chỉ áp dụng cho phiên demo này.</p>
    </> : <p>Lưu thông tin để tạo cửa hàng, sau đó mở cửa và thêm sản phẩm.</p>}</section>
  </div>;
}
