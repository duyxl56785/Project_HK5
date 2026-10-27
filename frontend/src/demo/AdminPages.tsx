import { useId, useState } from 'react';
import { CategoryBrowser } from '../features/catalog/CategoryBrowser';
import { buildCategoryTree } from '../features/catalog/categoryTree';
import type { CategoryDto } from '../features/catalog/categoryTypes';
import { formatProductPrice } from '../features/product/productManagementModel';
import { DEMO_ACCOUNTS } from './demoTypes';
import type { DemoPageProps, DemoProduct, DemoSeller } from './demoTypes';

const statuses: Record<string, { label: string; tone: string }> = {
  PENDING_REVIEW: { label: 'Chờ xét duyệt', tone: 'warning' }, APPROVED: { label: 'Đã duyệt', tone: 'success' },
  REJECTED: { label: 'Bị từ chối', tone: 'danger' }, SUSPENDED: { label: 'Tạm ngừng', tone: 'danger' },
  DRAFT: { label: 'Bản nháp', tone: 'neutral' }, ACTIVE: { label: 'Đang bán', tone: 'success' }, INACTIVE: { label: 'Ngừng bán', tone: 'neutral' },
};
const sellerLabel = (seller: DemoSeller) => DEMO_ACCOUNTS.find((account) => account.id === seller.accountId)?.label ?? seller.accountId;

function StatusBadge({ status }: { status: string }) {
  return <span className="demo-badge" data-tone={statuses[status]?.tone ?? 'neutral'}>{statuses[status]?.label ?? status}</span>;
}
function AdminOnly() {
  return <div className="demo-empty"><h2>Khu vực quản trị</h2><p>Chuyển sang vai trò Quản trị để truy cập màn hình này.</p></div>;
}
function AdminHead({ title, detail }: { title: string; detail: string }) {
  return <header className="demo-page-head"><span className="demo-eyebrow">Bàn làm việc quản trị</span><h1>{title}</h1><p>{detail}</p>
    <p className="demo-note">Xét duyệt trong phiên demo; dữ liệu không gửi đến máy chủ.</p></header>;
}
function ReasonForm({ title, onConfirm, onCancel }: { title: string; onConfirm: (reason: string) => boolean; onCancel: () => void }) {
  const [reason, setReason] = useState('');
  const id = useId();
  return <form className="demo-callout demo-form" aria-labelledby={`${id}-title`} onSubmit={(event) => {
    event.preventDefault();
    if (reason && onConfirm(reason)) setReason('');
  }}><h3 id={`${id}-title`}>{title}</h3>
    <label className="demo-field"><span>Lý do xét duyệt</span><textarea className="demo-input" required rows={4} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Ghi rõ lý do để người bán có thể xử lý…" /></label>
    <p className="demo-note">Tối đa 500 ký tự Unicode theo quy tắc miền; lý do không hợp lệ sẽ được giữ lại để sửa.</p>
    <div className="demo-actions"><button className="demo-button danger" disabled={!reason}>Xác nhận</button><button className="demo-button secondary" type="button" onClick={onCancel}>Hủy</button></div>
  </form>;
}

export function AdminSellersPage(props: DemoPageProps) {
  if (props.session.role !== 'admin') return <AdminOnly />;
  return <AdminSellers key={props.session.accountId} {...props} />;
}
function AdminSellers(props: DemoPageProps) {
  const [filter, setFilter] = useState('ALL');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const sellers = props.state.sellers.filter((seller) => filter === 'ALL' || seller.status === filter);
  const selected = sellers.find((seller) => seller.id === selectedId);
  return <>
    <AdminHead title="Xét duyệt người bán" detail="Xem hồ sơ và giữ nhịp hoạt động cho cộng đồng cửa hàng." />
    <div className="demo-stats">{['PENDING_REVIEW', 'APPROVED', 'SUSPENDED'].map((status) => <article className="demo-stat" key={status}><span>{statuses[status].label}</span><strong>{props.state.sellers.filter((seller) => seller.status === status).length}</strong></article>)}</div>
    <section className="demo-panel"><label className="demo-field"><span>Lọc trạng thái người bán</span><select className="demo-input" value={filter} onChange={(event) => { setFilter(event.target.value); setSelectedId(null); }}>
      <option value="ALL">Tất cả trạng thái</option>{['PENDING_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED'].map((status) => <option key={status} value={status}>{statuses[status].label}</option>)}
    </select></label>
      {sellers.length ? <div className="demo-table-wrap"><table className="demo-table"><caption>Hồ sơ người bán · {sellers.length} hồ sơ</caption><thead><tr><th scope="col">Người bán</th><th scope="col">Cửa hàng</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
        <tbody>{sellers.map((seller) => <tr key={seller.id}><th scope="row">{sellerLabel(seller)}</th><td>{props.state.stores.find((store) => store.sellerId === seller.id)?.name ?? 'Chưa có cửa hàng'}</td><td><StatusBadge status={seller.status} /></td>
          <td><button className="demo-button secondary" aria-expanded={selected?.id === seller.id} onClick={() => setSelectedId(selectedId === seller.id ? null : seller.id)}>Xem hồ sơ<span className="demo-sr-only"> {sellerLabel(seller)}</span></button></td></tr>)}</tbody></table></div>
        : <div className="demo-empty"><h2>Không có hồ sơ phù hợp</h2><p>Thử một trạng thái khác hoặc đăng ký hồ sơ từ góc người bán.</p></div>}
    </section>
    {selected && <SellerDetail key={`${selected.id}:${selected.version}:${props.session.accountId}`} {...props} seller={selected} />}
  </>;
}
function SellerDetail({ seller, ...props }: DemoPageProps & { seller: DemoSeller }) {
  const [decision, setDecision] = useState<'reject' | 'suspend' | null>(null);
  const store = props.state.stores.find((item) => item.sellerId === seller.id);
  const self = seller.accountId === props.session.accountId;
  function moderate(action: 'approve' | 'reject' | 'suspend' | 'reinstate', reason?: string) {
    const confirmed = props.dispatch({ type: 'seller.moderate', sellerId: seller.id, expectedVersion: seller.version, decision: action, reason }, 'Đã cập nhật trạng thái người bán.');
    if (confirmed) setDecision(null);
    return confirmed;
  }
  return <section className="demo-panel demo-detail"><span className="demo-eyebrow">Chi tiết hồ sơ</span><h2>{sellerLabel(seller)}</h2><StatusBadge status={seller.status} />
    <dl><div><dt>Cửa hàng</dt><dd>{store?.name ?? 'Chưa có cửa hàng'}</dd></div><div><dt>Mã hồ sơ</dt><dd><code>{seller.id}</code></dd></div><div><dt>Phiên bản</dt><dd>{seller.version}</dd></div></dl>
    {seller.reviewReason && <div className="demo-callout"><h3>Phản hồi hiện tại</h3><p>{seller.reviewReason}</p></div>}
    {self ? <p className="demo-callout">Bạn không thể tự xét duyệt hồ sơ của chính mình.</p> : <div className="demo-actions">
      {seller.status === 'PENDING_REVIEW' && <><button className="demo-button" onClick={() => moderate('approve')}>Duyệt người bán</button><button className="demo-button danger" onClick={() => setDecision('reject')}>Từ chối hồ sơ</button></>}
      {seller.status === 'APPROVED' && <button className="demo-button danger" onClick={() => setDecision('suspend')}>Tạm ngừng người bán</button>}
      {seller.status === 'SUSPENDED' && <button className="demo-button" onClick={() => moderate('reinstate')}>Khôi phục người bán</button>}
      {seller.status === 'REJECTED' && <p>Người bán cần gửi lại hồ sơ trước khi xét duyệt lần tiếp theo.</p>}
    </div>}
    {!self && decision && <ReasonForm key={decision} title={decision === 'reject' ? 'Từ chối hồ sơ người bán' : 'Tạm ngừng người bán'} onConfirm={(reason) => moderate(decision, reason)} onCancel={() => setDecision(null)} />}
  </section>;
}

export function AdminProductsPage(props: DemoPageProps) {
  if (props.session.role !== 'admin') return <AdminOnly />;
  return <AdminProducts key={props.session.accountId} {...props} />;
}
function AdminProducts(props: DemoPageProps) {
  const [status, setStatus] = useState('PENDING_REVIEW');
  const [storeId, setStoreId] = useState('ALL');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const products = props.state.products.filter((product) => (status === 'ALL' || product.status === status) && (storeId === 'ALL' || product.storeId === storeId));
  const selected = products.find((product) => product.id === selectedId);
  return <>
    <AdminHead title="Xét duyệt sản phẩm" detail="Mỗi sản phẩm được xem kỹ trước khi đến với khách hàng." />
    <section className="demo-panel"><div className="demo-grid">
      <label className="demo-field"><span>Lọc trạng thái sản phẩm</span><select className="demo-input" value={status} onChange={(event) => { setStatus(event.target.value); setSelectedId(null); }}><option value="ALL">Tất cả trạng thái</option>
        {['PENDING_REVIEW', 'ACTIVE', 'DRAFT', 'REJECTED', 'SUSPENDED', 'INACTIVE'].map((value) => <option value={value} key={value}>{statuses[value].label}</option>)}</select></label>
      <label className="demo-field"><span>Lọc cửa hàng</span><select className="demo-input" value={storeId} onChange={(event) => { setStoreId(event.target.value); setSelectedId(null); }}><option value="ALL">Tất cả cửa hàng</option>{props.state.stores.map((store) => <option key={store.id} value={store.id}>{store.name}</option>)}</select></label>
    </div>
      {products.length ? <div className="demo-table-wrap"><table className="demo-table"><caption>Sản phẩm phù hợp · {products.length} sản phẩm</caption><thead><tr><th scope="col">Sản phẩm</th><th scope="col">Cửa hàng</th><th scope="col">Giá</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
        <tbody>{products.map((product) => <tr key={product.id}><th scope="row">{product.name}<small className="demo-note">{product.sku}</small></th><td>{props.state.stores.find((store) => store.id === product.storeId)?.name ?? 'Cửa hàng không khả dụng'}</td>
          <td>{formatProductPrice(product.unitPrice)}</td><td><StatusBadge status={product.status} /></td><td><button className="demo-button secondary" aria-expanded={selected?.id === product.id} onClick={() => setSelectedId(selectedId === product.id ? null : product.id)}>Xem sản phẩm<span className="demo-sr-only"> {product.name}</span></button></td></tr>)}</tbody></table></div>
        : <div className="demo-empty"><h2>Không có sản phẩm phù hợp</h2><p>Sản phẩm mới gửi duyệt từ người bán sẽ xuất hiện ở trạng thái Chờ xét duyệt.</p></div>}
    </section>
    {selected && <ProductDetail key={`${selected.id}:${selected.version}:${props.session.accountId}`} {...props} product={selected} />}
  </>;
}
function ProductDetail({ product, ...props }: DemoPageProps & { product: DemoProduct }) {
  const [decision, setDecision] = useState<'reject' | 'suspend' | null>(null);
  const store = props.state.stores.find((item) => item.id === product.storeId);
  const seller = props.state.sellers.find((item) => item.id === store?.sellerId);
  const self = seller?.accountId === props.session.accountId;
  function moderate(action: 'approve' | 'reject' | 'suspend', reason?: string) {
    const confirmed = props.dispatch({ type: 'product.moderate', productId: product.id, expectedVersion: product.version, decision: action, reason }, 'Đã cập nhật trạng thái sản phẩm.');
    if (confirmed) setDecision(null);
    return confirmed;
  }
  return <section className="demo-panel demo-detail"><span className="demo-eyebrow">Chi tiết sản phẩm</span><h2>{product.name}</h2><StatusBadge status={product.status} />
    <dl><div><dt>SKU</dt><dd>{product.sku}</dd></div><div><dt>Cửa hàng</dt><dd>{store?.name ?? 'Không khả dụng'}</dd></div><div><dt>Danh mục</dt><dd>{props.state.categories.find((category) => category.id === product.categoryId)?.name ?? 'Không khả dụng'}</dd></div>
      <div><dt>Giá bán</dt><dd>{formatProductPrice(product.unitPrice)}</dd></div><div><dt>Phiên bản</dt><dd>{product.version}</dd></div></dl>
    <h3>Mô tả sản phẩm</h3><p>{product.description || 'Người bán chưa thêm mô tả.'}</p>
    {product.images.length > 0 && <div className="demo-review-images">{product.images.map((image) => <img key={image.id} src={image.url} alt={image.name} />)}</div>}
    {product.reviewReason && <div className="demo-callout"><h3>Phản hồi hiện tại</h3><p>{product.reviewReason}</p></div>}
    {self ? <p className="demo-callout">Bạn không thể tự xét duyệt sản phẩm của mình.</p> : <div className="demo-actions">
      {product.status === 'PENDING_REVIEW' && <><button className="demo-button" onClick={() => moderate('approve')}>Duyệt sản phẩm</button><button className="demo-button danger" onClick={() => setDecision('reject')}>Từ chối sản phẩm</button></>}
      {product.status === 'ACTIVE' && <button className="demo-button danger" onClick={() => setDecision('suspend')}>Tạm ngừng sản phẩm</button>}
      {product.status !== 'PENDING_REVIEW' && product.status !== 'ACTIVE' && <p>Không có thao tác xét duyệt ở trạng thái này.</p>}
    </div>}
    {!self && decision && <ReasonForm key={decision} title={decision === 'reject' ? 'Từ chối sản phẩm' : 'Tạm ngừng sản phẩm'} onConfirm={(reason) => moderate(decision, reason)} onCancel={() => setDecision(null)} />}
  </section>;
}

export function AdminCategoriesPage(props: DemoPageProps) {
  if (props.session.role !== 'admin') return <AdminOnly />;
  return <AdminCategories key={props.session.accountId} {...props} />;
}
function AdminCategories(props: DemoPageProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [createKey, setCreateKey] = useState(0);
  const selected = props.state.categories.find((category) => category.id === selectedId);
  return <>
    <AdminHead title="Quản lý danh mục" detail="Sắp xếp những nhóm sản phẩm thành một hệ thống dễ tìm, dễ khám phá." />
    <div className="demo-grid">
      <CategoryBrowser state={{ status: 'ready', categories: props.state.categories }} selectedId={selected?.id ?? null} onSelect={(category) => setSelectedId(category.id)} />
      <CategoryForm key={`new:${createKey}`} {...props} onConfirmed={() => setCreateKey((value) => value + 1)} />
    </div>
    {selected && <CategoryForm key={`${selected.id}:${selected.name}:${selected.parentId ?? ''}`} {...props} category={selected} onConfirmed={() => undefined} />}
  </>;
}
function CategoryForm({ category, onConfirmed, ...props }: DemoPageProps & { category?: CategoryDto; onConfirmed: () => void }) {
  const [name, setName] = useState(category?.name ?? '');
  const [parentId, setParentId] = useState(category?.parentId ?? '');
  const [deleting, setDeleting] = useState(false);
  const tree = buildCategoryTree(props.state.categories);
  const descendants = new Set<string>();
  if (category && tree.ok) {
    for (const row of tree.rows) if (row.id === category.id || (row.parentId !== null && descendants.has(row.parentId))) descendants.add(row.id);
  }
  const childCount = category ? props.state.categories.filter((item) => item.parentId === category.id).length : 0;
  const productCount = category ? props.state.products.filter((item) => item.categoryId === category.id).length : 0;
  const deleteBlocked = childCount > 0 || productCount > 0;
  return <section className="demo-panel"><form className="demo-form" onSubmit={(event) => {
    event.preventDefault();
    if (props.dispatch({ type: 'category.save', id: category?.id, name, parentId: parentId || null }, category ? 'Đã cập nhật danh mục.' : 'Đã tạo danh mục.')) onConfirmed();
  }}><h2>{category ? `Chỉnh sửa: ${category.name}` : 'Thêm danh mục mới'}</h2>
    <label className="demo-field"><span>Tên danh mục</span><input className="demo-input" required maxLength={255} value={name} onChange={(event) => setName(event.target.value)} placeholder="Ví dụ: Đồ gốm" /></label>
    <label className="demo-field"><span>Danh mục cha</span><select className="demo-input" value={parentId} onChange={(event) => setParentId(event.target.value)}><option value="">Không có · Danh mục gốc</option>
      {tree.ok && tree.rows.map((row) => <option key={row.id} value={row.id} disabled={descendants.has(row.id)}>{'— '.repeat(Math.min(row.depth - 1, 8))}{row.name}</option>)}
    </select></label>
    <p>Danh mục không thể nằm bên trong chính nó hoặc danh mục con của nó.</p>
    <div className="demo-actions"><button className="demo-button">{category ? 'Lưu danh mục' : 'Thêm danh mục'}</button>
      {category && <button type="button" className="demo-button danger" disabled={deleteBlocked} onClick={() => setDeleting(true)}>Xóa danh mục</button>}</div>
  </form>
    {category && <p className="demo-note">{childCount} danh mục con · {productCount} sản phẩm tham chiếu. Chỉ xóa được danh mục không có danh mục con và không được sản phẩm sử dụng.</p>}
    {category && deleting && <div className="demo-callout" role="group" aria-label="Xác nhận xóa danh mục"><h3>Xóa “{category.name}”?</h3><p>Danh mục sẽ được gỡ khỏi phiên demo. Thao tác này không thể hoàn tác riêng lẻ.</p>
      <div className="demo-actions"><button className="demo-button danger" disabled={deleteBlocked} onClick={() => {
        if (props.dispatch({ type: 'category.delete', id: category.id }, 'Đã xóa danh mục.')) { setDeleting(false); onConfirmed(); }
      }}>Xác nhận xóa</button><button className="demo-button secondary" onClick={() => setDeleting(false)}>Giữ danh mục</button></div></div>}
  </section>;
}
