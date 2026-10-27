import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { ProductManagementPage } from '../features/product/ProductManagementPage';
import { PRODUCT_STATUS_LABELS } from '../features/product/productManagementModel';
import type { ProductManagementActions, ProductManagementData, ProductMutationResult, ProductReloadHandler } from '../features/product/productManagementTypes';
import { AdminCategoriesPage, AdminProductsPage, AdminSellersPage } from './AdminPages';
import { SellerDashboard, SellerProfilePage, StorePage } from './SellerPages';
import { CatalogPage, ProductDetail, ProductPicture } from './CatalogPages';
import { DemoImagePicker } from './DemoImagePicker';
import { createDemoState, ownedSeller, ownedStore, transition } from './demoModel';
import { DEMO_ACCOUNTS } from './demoTypes';
import type { DemoAction, DemoPageProps, DemoRole, DemoSession, DemoState } from './demoTypes';
import { DemoIcon } from './DemoVisuals';

const homes = { customer: '/catalog', seller: '/seller', admin: '/admin/sellers' };
const links = {
  customer: [['/catalog', 'Khám phá', 'grid'], ['/categories', 'Danh mục', 'tree']],
  seller: [['/seller', 'Tổng quan', 'grid'], ['/seller/products', 'Sản phẩm', 'box'], ['/seller/images', 'Ảnh sản phẩm', 'image'], ['/seller/store', 'Cửa hàng', 'store'], ['/seller/profile', 'Hồ sơ người bán', 'users']],
  admin: [['/admin/sellers', 'Người bán', 'users'], ['/admin/products', 'Kiểm duyệt sản phẩm', 'box'], ['/admin/categories', 'Danh mục', 'tree']],
} as const;
type ScreenState = 'ready' | 'loading' | 'empty' | 'error' | 'unauthorized' | 'unavailable';

function Dialog({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => { ref.current?.showModal(); }, []);
  return <dialog ref={ref} className="demo-dialog" aria-label={title} onCancel={onClose}><h2>{title}</h2>{children}<button className="demo-button secondary" onClick={onClose}>Đóng</button></dialog>;
}

function StateExample({ status, onRetry }: { status: ScreenState; onRetry: () => void }) {
  const content = {
    loading: ['Đang tải dữ liệu mẫu', 'Đây là ví dụ trạng thái tải, không có yêu cầu API đang chạy.'],
    empty: ['Chưa có dữ liệu để hiển thị', 'Dữ liệu mẫu vẫn được bảo toàn. Trở về trạng thái bình thường để tiếp tục.'],
    error: ['Không thể hiển thị dữ liệu', 'Ví dụ lỗi kết nối. Bạn có thể thử lại mà không mất thay đổi trong phiên demo.'],
    unauthorized: ['Bạn chưa có quyền truy cập', 'Ví dụ trạng thái từ chối truy cập. Không có thông tin riêng tư được hiển thị.'],
    unavailable: ['Chức năng chưa sẵn sàng', 'Ứng dụng production đang chờ API và xác thực thật; demo không thay thế các điều kiện này.'],
    ready: ['', ''],
  };
  return <div className="demo-empty" role={status === 'error' ? 'alert' : 'status'} aria-busy={status === 'loading'}>{status === 'loading' ? <span className="demo-spinner" /> : <DemoIcon name={status === 'empty' ? 'box' : 'help'} size={40} />}<h1>{content[status][0]}</h1><p>{content[status][1]}</p><button className="demo-button" onClick={onRetry}>Trở về dữ liệu mẫu</button></div>;
}

export function DemoApp() {
  const [state, setState] = useState<DemoState>(createDemoState);
  const [session, setSession] = useState<DemoSession>({ role: 'customer', accountId: 'seller-a' });
  const [route, setRoute] = useState(() => typeof window === 'undefined' ? '/catalog' : window.location.hash.slice(1) || '/catalog');
  const [screen, setScreen] = useState<ScreenState>('ready');
  const [toast, setToast] = useState<{ message: string; error: boolean } | null>(null);
  const [dialog, setDialog] = useState<'help' | 'reset' | null>(null);
  const [menuOpen, setMenuOpen] = useState(false), [epoch, setEpoch] = useState(0);
  const current = useRef(state), currentSession = useRef(session), lifetime = useRef(epoch), urls = useRef(new Set<string>());
  const selectedSeller = useRef('seller-a');
  current.current = state; currentSession.current = session; lifetime.current = epoch;
  useEffect(() => { const changed = () => { setRoute(window.location.hash.slice(1) || '/catalog'); setMenuOpen(false); }; window.addEventListener('hashchange', changed); return () => window.removeEventListener('hashchange', changed); }, []);
  useEffect(() => { if (!toast) return; const timer = window.setTimeout(() => setToast(null), 6500); return () => window.clearTimeout(timer); }, [toast]);
  useEffect(() => () => { for (const url of urls.current) URL.revokeObjectURL(url); urls.current.clear(); }, []);
  function navigate(path: string) { window.location.hash = path; setRoute(path); setMenuOpen(false); window.scrollTo({ top: 0, behavior: 'instant' }); }
  function assign(next: DemoState) {
    const retained = new Set(next.products.flatMap(product => product.images.map(image => image.url)));
    for (const url of urls.current) if (!retained.has(url)) URL.revokeObjectURL(url);
    urls.current = retained; current.current = next; setState(next);
  }
  function dispatch(action: DemoAction, message = 'Đã cập nhật dữ liệu trong phiên demo.'): boolean {
    if (currentSession.current.role !== session.role || currentSession.current.accountId !== session.accountId || lifetime.current !== epoch) return false;
    try { assign(transition(current.current, session, action)); setToast({ message, error: false }); return true; }
    catch (error) { setToast({ message: error instanceof Error ? error.message : 'Không thể xác nhận thao tác demo.', error: true }); return false; }
  }
  function changeSession(role: DemoRole, accountId: string) {
    if (role === 'seller' && accountId !== 'demo-admin') selectedSeller.current = accountId;
    const next = { role, accountId: role === 'admin' ? 'demo-admin' : selectedSeller.current };
    currentSession.current = next; lifetime.current += 1; setSession(next); setEpoch(lifetime.current); setScreen('ready'); setToast(null); navigate(homes[role]);
  }
  const scope = `${session.role}:${session.accountId}:${epoch}`;
  const pageProps: DemoPageProps = { state, session, dispatch, navigate };
  const activeLabel = links[session.role].find(([path]) => path === route)?.[1] ?? (route.startsWith('/product/') ? 'Chi tiết sản phẩm' : 'Không tìm thấy trang');
  const authorized = session.role === 'customer' ? route === '/catalog' || route === '/categories' || route.startsWith('/product/')
    : session.role === 'seller' ? route.startsWith('/seller') : route.startsWith('/admin/');
  let content: ReactNode;
  if (!authorized) content = <StateExample status="unauthorized" onRetry={() => navigate(homes[session.role])} />;
  else if (screen !== 'ready') content = <StateExample status={screen} onRetry={() => setScreen('ready')} />;
  else if (route === '/catalog' || route === '/categories') content = <CatalogPage key={route} state={state} navigate={navigate} categoriesOnly={route === '/categories'} />;
  else if (route.startsWith('/product/')) content = <ProductDetail key={route} id={route.slice('/product/'.length)} state={state} navigate={navigate} />;
  else if (route === '/seller') content = <SellerDashboard {...pageProps} />;
  else if (route === '/seller/profile') content = <SellerProfilePage {...pageProps} />;
  else if (route === '/seller/store') content = <StorePage {...pageProps} />;
  else if (route === '/seller/products') content = <DemoProducts key={scope} {...pageProps} scope={scope} epoch={epoch} current={current} currentSession={currentSession} lifetime={lifetime} />;
  else if (route === '/seller/images') content = <DemoImages key={scope} {...pageProps} />;
  else if (route === '/admin/sellers') content = <AdminSellersPage {...pageProps} />;
  else if (route === '/admin/products') content = <AdminProductsPage {...pageProps} />;
  else if (route === '/admin/categories') content = <AdminCategoriesPage {...pageProps} />;
  else content = <div className="demo-empty"><h1>Không tìm thấy trang</h1><button className="demo-button" onClick={() => navigate(homes[session.role])}>Về trang chính</button></div>;
  const account = DEMO_ACCOUNTS.find(item => item.id === session.accountId);
  return <div className={`tv2-demo ${session.role === 'customer' ? 'demo-public' : 'demo-workspace'}`}>
    <a className="demo-skip" href="#demo-content" onClick={event => { event.preventDefault(); document.getElementById('demo-content')?.focus(); }}>Đến nội dung chính</a>
    <div className="demo-mode"><div><span className="demo-mode-dot" /><strong>CHẾ ĐỘ DEMO — DỮ LIỆU MẪU</strong><span className="demo-mode-description">Không kết nối API, không phát sinh giao dịch.</span></div><div><button onClick={() => setDialog('help')}><DemoIcon name="help" size={16} /> Hướng dẫn</button><button onClick={() => setDialog('reset')}><DemoIcon name="reset" size={16} /> Reset demo</button></div></div>
    <header className="demo-header"><a className="demo-brand" href={`#${homes[session.role]}`} aria-label="Nếp, trang chính"><span>nếp<span className="demo-brand-dot">.</span></span><small>MARKETPLACE</small></a>{session.role === 'customer' && <nav className="demo-top-nav" aria-label="Điều hướng khách hàng">{links.customer.map(([path, label]) => <a key={path} href={`#${path}`} aria-current={route === path ? 'page' : undefined}>{label}</a>)}</nav>}<div className="demo-switches"><label>Vai trò demo<select aria-label="Vai trò demo" value={session.role} onChange={event => changeSession(event.target.value as DemoRole, session.accountId)}><option value="customer">Khách hàng</option><option value="seller">Người bán</option><option value="admin">Quản trị viên</option></select></label>{session.role === 'seller' && <label>Tài khoản mẫu<select aria-label="Tài khoản người bán demo" value={session.accountId} onChange={event => changeSession('seller', event.target.value)}>{DEMO_ACCOUNTS.map(item => <option key={item.id} value={item.id}>{item.label}</option>)}</select></label>}<span className="demo-avatar" aria-hidden="true">{session.role === 'admin' ? 'QT' : session.role === 'seller' ? 'NB' : 'K'}</span></div></header>
    <div className="demo-body">{session.role !== 'customer' && <aside className={`demo-sidebar ${menuOpen ? 'is-open' : ''}`}><p className="demo-sidebar-label">{session.role === 'admin' ? 'KHÔNG GIAN QUẢN TRỊ' : 'KHÔNG GIAN NGƯỜI BÁN'}</p><nav aria-label="Điều hướng quản lý">{links[session.role].map(([path, label, icon]) => <a key={path} href={`#${path}`} aria-current={route === path ? 'page' : undefined}><DemoIcon name={icon} />{label}{route === path && <DemoIcon name="chevron" size={16} />}</a>)}</nav><div className="demo-sidebar-note"><DemoIcon name="leaf" size={27} /><strong>Chuyện nhỏ, làm chỉn chu.</strong><p>Mọi thao tác được lưu trong bộ nhớ phiên demo.</p><button className="demo-link-button" onClick={() => setDialog('help')}>Xem kịch bản demo <span>↗</span></button></div></aside>}
      <main id="demo-content" tabIndex={-1} className="demo-main"><div className="demo-breadcrumb"><div>{session.role !== 'customer' && <button className="demo-mobile-menu" aria-label="Mở menu quản lý" aria-expanded={menuOpen} onClick={() => setMenuOpen(!menuOpen)}><DemoIcon name="menu" /></button>}<span>{session.role === 'customer' ? 'Nếp Marketplace' : session.role === 'admin' ? 'Quản trị' : account?.label}</span><DemoIcon name="chevron" size={13} /><strong>{activeLabel}</strong></div><label className="demo-state-control">Trạng thái UI<select aria-label="Trạng thái giao diện demo" value={screen} onChange={event => setScreen(event.target.value as ScreenState)}><option value="ready">Bình thường</option><option value="loading">Đang tải</option><option value="empty">Trống</option><option value="error">Lỗi</option><option value="unauthorized">Không có quyền</option><option value="unavailable">Chưa sẵn sàng</option></select></label></div><div key={scope} className="demo-page-content">{content}</div></main></div>
    <footer className="demo-footer"><a className="demo-brand" href={`#${homes[session.role]}`}>nếp<span className="demo-brand-dot">.</span></a><p>Những điều giản dị, được làm chỉn chu.</p><span>TV2 · Seller / Store / Product / Category · Demo UI</span><button className="demo-link-button" onClick={() => setDialog('help')}>Về bản demo</button></footer>
    {toast && <div className={`demo-toast ${toast.error ? 'is-error' : ''}`} role={toast.error ? 'alert' : 'status'}><DemoIcon name={toast.error ? 'help' : 'check'} /><span>{toast.message}</span><button aria-label="Đóng thông báo" onClick={() => setToast(null)}>×</button></div>}
    {dialog === 'help' && <Dialog title="Một vòng khám phá Nếp" onClose={() => setDialog(null)}><p>Đây là demo UI độc lập. Các vai trò là tình huống trình diễn, không phải quyền truy cập production.</p><ol><li>Chọn <strong>Người bán → Seller A</strong>, vào Sản phẩm để tạo bản nháp. Nhập SKU duy nhất, giá VND nguyên và danh mục.</li><li>Vào Ảnh sản phẩm, chọn sản phẩm mới và tối đa 5 ảnh JPEG/PNG/WebP, mỗi ảnh ≤ 2 MiB.</li><li>Quay lại Sản phẩm, gửi duyệt và xác nhận.</li><li>Chuyển <strong>Quản trị viên → Kiểm duyệt sản phẩm</strong>, xem rồi phê duyệt.</li><li>Chuyển <strong>Khách hàng</strong>, tìm sản phẩm trong Catalog. Seller phải APPROVED và Store phải mở.</li><li>Quay lại Người bán để kiểm tra trạng thái. Dùng Người bán mới để thử đăng ký → Admin duyệt → tạo Store.</li></ol><p className="demo-note">Dữ liệu chỉ ở bộ nhớ tab. Tải lại trình duyệt hoặc Reset sẽ trở về seed. Không có upload server, JWT, đơn hàng hay E2E API thật.</p></Dialog>}
    {dialog === 'reset' && <Dialog title="Khôi phục dữ liệu mẫu?" onClose={() => setDialog(null)}><p>Sản phẩm, ảnh local và mọi thay đổi trong phiên demo này sẽ được thay bằng dữ liệu ban đầu.</p><button className="demo-button danger" onClick={() => { assign(createDemoState()); lifetime.current += 1; setEpoch(lifetime.current); setScreen('ready'); setDialog(null); navigate(homes[session.role]); setToast({ message: 'Đã khôi phục dữ liệu demo ban đầu.', error: false }); }}>Khôi phục demo</button></Dialog>}
  </div>;
}

function managementData(state: DemoState, accountId: string): ProductManagementData | null {
  const seller = ownedSeller(state, accountId), store = ownedStore(state, accountId);
  if (!seller || seller.status !== 'APPROVED' || !store?.open) return null;
  return { store: { id: store.id, name: store.name }, products: state.products.filter(product => product.storeId === store.id), categories: state.categories };
}

function DemoProducts({ state, session, dispatch, navigate, scope, epoch, current, currentSession, lifetime }: DemoPageProps & {
  scope: string; epoch: number; current: React.RefObject<DemoState>; currentSession: React.RefObject<DemoSession>; lifetime: React.RefObject<number>;
}) {
  const data = managementData(state, session.accountId);
  const validSession = () => currentSession.current.role === 'seller' && currentSession.current.accountId === session.accountId && lifetime.current === epoch;
  const invoke = async (action: DemoAction): Promise<ProductMutationResult> => !validSession() ? { status: 'unauthorized' }
    : dispatch(action, 'Đã lưu vào dữ liệu mẫu của phiên demo.') ? { status: 'confirmed' } : { status: 'conflict' };
  const actions: ProductManagementActions = {
    create: fields => invoke({ type: 'product.create', fields }), update: fields => invoke({ type: 'product.update', fields }), submitForReview: fields => invoke({ type: 'product.submit', fields }),
  };
  const onReload: ProductReloadHandler = async request => {
    const fresh = validSession() ? managementData(current.current, session.accountId) : null;
    return fresh && fresh.store.id === request.storeId && request.sessionKey === scope
      ? { status: 'confirmed', receipt: request, snapshot: fresh } : { status: 'unauthorized' };
  };
  if (!data) return <div className="demo-empty"><DemoIcon name="store" size={40} /><h1>Chuẩn bị cửa hàng của bạn</h1><p>Cần hồ sơ APPROVED và cửa hàng đang mở để quản lý sản phẩm.</p><button className="demo-button" onClick={() => navigate('/seller/store')}>Kiểm tra cửa hàng</button></div>;
  return <><p className="demo-callout">Bộ điều hợp tại đây chỉ đọc/ghi state demo local. Không gọi máy chủ hoặc API production.</p><ProductManagementPage state={{ status: 'ready', ...data }} actions={actions} onReload={onReload} sessionKey={scope} confirmationMessage="Đã xác nhận trong state demo local. Đang cập nhật danh sách mẫu." pageSize={6} /><div className="demo-panel demo-product-extra"><h2>Ảnh và trạng thái bán</h2><p>Ảnh chỉ là preview local. Sản phẩm ACTIVE có thể ngừng bán; Seller không thể tự phê duyệt.</p><div className="demo-actions"><button className="demo-button secondary" onClick={() => navigate('/seller/images')}>Quản lý ảnh demo</button></div>{data.products.filter(product => product.status === 'ACTIVE').map(product => <div className="demo-row" key={product.id}><span>{product.name}</span><button className="demo-button secondary" onClick={() => dispatch({ type: 'product.deactivate', productId: product.id, expectedVersion: product.version }, 'Đã ngừng bán sản phẩm trong demo.')}>Ngừng bán</button></div>)}</div></>;
}

function DemoImages({ state, session, dispatch }: DemoPageProps) {
  const store = ownedStore(state, session.accountId), seller = ownedSeller(state, session.accountId);
  const [selected, setSelected] = useState('');
  const products = state.products.filter(product => product.storeId === store?.id);
  const product = products.find(item => item.id === selected) ?? products[0];
  const editable = !!product && seller?.status === 'APPROVED' && store?.open === true && ['DRAFT', 'REJECTED', 'INACTIVE'].includes(product.status);
  return <><div className="demo-page-head"><div><p className="demo-eyebrow">KHÔNG GIAN NGƯỜI BÁN</p><h1>Ảnh sản phẩm</h1><p>Mỗi chi tiết nhỏ đều làm nên một câu chuyện.</p></div></div><section className="demo-panel"><label className="demo-field">Sản phẩm<select aria-label="Chọn sản phẩm quản lý ảnh" value={product?.id ?? ''} onChange={event => setSelected(event.target.value)}>{!products.length && <option value="">Chưa có sản phẩm</option>}{products.map(item => <option key={item.id} value={item.id}>{item.name} · {PRODUCT_STATUS_LABELS[item.status]}</option>)}</select></label>{product ? <><div className="demo-image-product"><ProductPicture product={product} /><div><strong>{product.name}</strong><p>{product.sku}</p><span className="demo-badge">{PRODUCT_STATUS_LABELS[product.status]}</span></div></div>{!editable && <p className="demo-callout">Chỉ sửa ảnh khi sản phẩm ở trạng thái Nháp, Bị từ chối hoặc Ngừng bán, với Seller đã duyệt và Store mở.</p>}<DemoImagePicker key={`${session.accountId}:${product.id}`} images={product.images} disabled={!editable} onChange={images => dispatch({ type: 'product.images', productId: product.id, expectedVersion: product.version, images }, 'Đã cập nhật ảnh xem trước local. Chưa upload lên máy chủ.')} /></> : <div className="demo-empty"><h2>Tạo sản phẩm trước khi chọn ảnh</h2><p>Bạn có thể tạo bản nháp tại mục Sản phẩm.</p></div>}</section></>;
}
