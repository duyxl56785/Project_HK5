import { useEffect, useId, useRef, useState } from 'react';
import { ProductForm } from './ProductForm.tsx';
import { canEditProduct, formatProductPrice, PRODUCT_STATUS_LABELS, readProductManagement } from './productManagementModel.ts';
import type { ProductManagementActions, ProductManagementData, ProductManagementState, ProductMutationResult, ProductReloadHandler, ProductReloadRequest, ProductReloadResult, ProductSummary, ValidProductFields } from './productManagementTypes.ts';
import './ProductManagement.css';

export interface ProductManagementPageProps {
  readonly state?: ProductManagementState;
  /** Host-owned protected adapter. Missing methods never simulate a successful write. */
  readonly actions?: ProductManagementActions;
  /** Opaque nonsecret lifetime key, rotated by the host when account/access/session changes. */
  readonly sessionKey?: string;
  readonly onRetry?: () => void;
  readonly onReload?: ProductReloadHandler;
}
const unavailable = { status: 'unavailable' } as const;
interface WriteBarrier { scope: string; blocked: boolean }
export function ProductManagementPage({ state = unavailable, actions, sessionKey, onRetry, onReload }: ProductManagementPageProps) {
  const id = useId();
  const barrier = useRef<WriteBarrier | null>(null);
  if (state.status === 'ready') {
    const data = readProductManagement(state.store, state.products, state.categories);
    const scope = typeof sessionKey === 'string' && sessionKey.trim() ? sessionKey : undefined;
    const storeId = typeof state.store?.id === 'string' ? state.store.id : '';
    const scopeId = JSON.stringify([storeId, scope]);
    if (barrier.current?.scope !== scopeId) barrier.current = { scope: scopeId, blocked: false };
    // Keep the same workspace/lock for invalid data in the same Store/session.
    return <ProductWorkspace key={scopeId} barrier={barrier.current} data={data} storeId={storeId} sessionKey={scope} actions={actions} onRetry={onRetry} onReload={onReload} />;
  }
  const messages = {
    loading: ['Đang tải sản phẩm', 'Vui lòng chờ trong giây lát.'],
    unauthorized: ['Không có quyền truy cập', 'Vui lòng đăng nhập bằng tài khoản có quyền quản lý cửa hàng.'],
    unavailable: ['Chức năng chưa sẵn sàng', 'Chưa kết nối API được bảo vệ. Sản phẩm sẽ hiển thị khi kết nối sẵn sàng.'],
    error: ['Không tải được sản phẩm', 'Vui lòng thử lại. Thông tin đang có không được sử dụng để thay thế dữ liệu mới.'],
  };
  const [title, message] = messages[state.status] ?? messages.unavailable;
  return <section className="tv2-product" aria-labelledby={id} aria-busy={state.status === 'loading'}><p className="tv2-product__eyebrow">KHÔNG GIAN NGƯỜI BÁN</p><h2 id={id}>Quản lý sản phẩm</h2><div className="tv2-product__notice" role={state.status === 'error' || state.status === 'unauthorized' ? 'alert' : 'status'}><h3>{title}</h3><p>{message}</p>{state.status === 'loading' && <div className="tv2-product__skeleton" aria-hidden="true" />}{state.status === 'error' && onRetry && <button onClick={onRetry}>Thử lại</button>}</div></section>;
}

function ProductWorkspace({ barrier, data, storeId, sessionKey, actions, onRetry, onReload }: {
  readonly barrier: WriteBarrier;
  readonly data: ProductManagementData | null;
  readonly storeId: string;
  readonly sessionKey?: string;
  readonly actions?: ProductManagementActions;
  readonly onRetry?: () => void;
  readonly onReload?: ProductReloadHandler;
}) {
  const id = useId(), alive = useRef(true), running = useRef(false), latest = useRef(data);
  const pendingReload = useRef<ProductReloadRequest | null>(null);
  const returnFocus = useRef<HTMLButtonElement | null>(null), confirmationHeading = useRef<HTMLHeadingElement>(null), feedback = useRef<HTMLDivElement>(null);
  const [editor, setEditor] = useState<ProductSummary | 'new' | null>(null);
  const [confirmation, setConfirmation] = useState<ProductSummary | null>(null);
  const [busy, setBusy] = useState(false), [denied, setDenied] = useState(false), [needsRefresh, setNeedsRefresh] = useState(barrier.blocked);
  const [notice, setNotice] = useState<{ text: string; error: boolean } | null>(null);
  const [completion, setCompletion] = useState<{ request: ProductReloadRequest; snapshot: string } | null>(null);
  const [query, setQuery] = useState(''), [status, setStatus] = useState('');
  latest.current = data;
  const snapshot = data ? JSON.stringify(data) : null;
  useEffect(() => { alive.current = true; return () => { alive.current = false; pendingReload.current = null; }; }, []);
  useEffect(() => {
    if (snapshot === null) {
      barrier.blocked = true; pendingReload.current = null; setCompletion(null); setNeedsRefresh(true);
    }
  }, [snapshot]);
  useEffect(() => {
    if (completion && pendingReload.current === completion.request && snapshot !== null && snapshot === completion.snapshot) {
      // A correlated verified response AND its validated rendered snapshot are required.
      pendingReload.current = null; barrier.blocked = false; setNeedsRefresh(false); setCompletion(null);
      setNotice({ text: 'Danh sách đã được tải lại. Kiểm tra thông tin trước khi tiếp tục.', error: false });
    }
  }, [completion, snapshot]);
  useEffect(() => { if (confirmation) confirmationHeading.current?.focus(); }, [confirmation]);
  useEffect(() => { if (notice) feedback.current?.focus(); }, [notice]);
  const isCurrent = (product: ProductSummary) => latest.current?.products.some((current) => current.id === product.id && current.version === product.version && current.status === product.status) ?? false;
  const stale = editor !== null && editor !== 'new' && !isCurrent(editor);
  const mutationLocked = busy || needsRefresh;
  function closePanel() {
    setEditor(null); setConfirmation(null);
    requestAnimationFrame(() => returnFocus.current?.focus());
  }
  function requireReload() {
    barrier.blocked = true; pendingReload.current = null; setCompletion(null); setNeedsRefresh(true);
  }
  async function reload() {
    if (!onReload || !sessionKey || !latest.current || !barrier.blocked || running.current || denied) return;
    const request: ProductReloadRequest = Object.freeze({ requestId: crypto.randomUUID(), storeId, sessionKey });
    pendingReload.current = request; setCompletion(null);
    let result: ProductReloadResult;
    try { result = await onReload(request); } catch { result = { status: 'error' }; }
    if (!alive.current || pendingReload.current !== request) return;
    if (result?.status === 'unauthorized') {
      requireReload(); setDenied(true); setEditor(null); setConfirmation(null); return;
    }
    if (result?.status === 'confirmed' && result.receipt?.requestId === request.requestId
      && result.receipt.storeId === storeId && result.receipt.sessionKey === sessionKey) {
      const fresh = readProductManagement(result.snapshot?.store, result.snapshot?.products, result.snapshot?.categories);
      if (fresh && fresh.store.id === storeId) {
        setCompletion({ request, snapshot: JSON.stringify(fresh) });
        return;
      }
    }
    pendingReload.current = null;
    setNotice((current) => ({ text: `${current && !current.error ? `${current.text} ` : ''}Chưa xác nhận được danh sách mới. Thao tác ghi tiếp tục bị khóa; vui lòng tải lại.`, error: true }));
  }
  async function run(operation: () => Promise<ProductMutationResult>) {
    if (running.current || barrier.blocked || denied || !sessionKey || !latest.current) return;
    barrier.blocked = true;
    running.current = true; setBusy(true); setNotice(null);
    let result: ProductMutationResult;
    try { result = await operation(); } catch { result = { status: 'error' }; }
    running.current = false;
    if (!alive.current) return;
    setBusy(false);
    if (result?.status === 'unauthorized') { requireReload(); setDenied(true); setEditor(null); setConfirmation(null); return; }
    if (result?.status === 'confirmed') {
      setEditor(null); setConfirmation(null); requireReload();
      setNotice({ text: 'Máy chủ đã xác nhận thao tác. Tải lại danh sách để xem dữ liệu mới.', error: false });
      // The host fetches authoritative data; this feature never invents IDs/status/version.
      void reload();
      return;
    }
    if (result?.status === 'conflict') {
      requireReload();
      setNotice({ text: 'Sản phẩm đã thay đổi. Tải lại danh sách và kiểm tra phiên bản mới trước khi tiếp tục.', error: true });
    } else if (result?.status === 'unavailable') {
      requireReload();
      setNotice({ text: 'Kết nối chưa sẵn sàng. Chưa xác nhận lưu dữ liệu; tải lại danh sách trước khi thử lại.', error: true });
    } else {
      requireReload();
      setNotice({ text: 'Chưa xác nhận được thao tác. Tải lại danh sách để kiểm tra kết quả trước khi thử lại.', error: true });
    }
  }
  function save(fields: ValidProductFields) {
    if (editor === 'new' && actions?.create) {
      void run(() => actions.create!({ ...fields, storeId }));
    } else if (editor && editor !== 'new' && isCurrent(editor) && canEditProduct(editor.status) && fields.sku === editor.sku && actions?.update) {
      const { categoryId, name, unitPrice, currency } = fields;
      void run(() => actions.update!({ productId: editor.id, expectedVersion: editor.version, categoryId, name, unitPrice, currency }));
    }
  }
  if (denied) return <ProductManagementPage state={{ status: 'unauthorized' }} />;
  if (!data) return <section className="tv2-product" aria-labelledby={id}><h2 id={id}>Quản lý sản phẩm</h2><div role="alert" className="tv2-product__alert"><h3>Dữ liệu sản phẩm không nhất quán</h3><p>Vui lòng tải lại dữ liệu đúng cửa hàng.</p>{onRetry && <button onClick={onRetry}>Thử lại</button>}</div></section>;
  const term = query.trim().toLocaleLowerCase('vi');
  const visible = data.products.filter((product) => (!status || product.status === status) && (!term || product.name.toLocaleLowerCase('vi').includes(term) || product.sku.toLocaleLowerCase('vi').includes(term)));
  const categories = new Map(data.categories.map((category) => [category.id, category.name]));
  const connected = !!sessionKey && !!(actions?.create || actions?.update || actions?.submitForReview);
  const allConnected = !!sessionKey && !!(actions?.create && actions?.update && actions?.submitForReview);
  return <section className="tv2-product" aria-labelledby={`${id}-title`}>
    <header className="tv2-product__header"><div><p className="tv2-product__eyebrow">KHÔNG GIAN NGƯỜI BÁN</p><h2 id={`${id}-title`}>Sản phẩm của bạn</h2><p className="tv2-product__store">{data.store.name}</p><p className="tv2-product__subtle">Quản lý thông tin và theo dõi trạng thái duyệt.</p></div><button type="button" disabled={mutationLocked} onClick={(event) => { returnFocus.current = event.currentTarget; setEditor('new'); setConfirmation(null); setNotice(null); }}>+ Thêm sản phẩm</button></header>
    {!allConnected && <p className="tv2-product__notice">{connected ? 'Một số thao tác chưa kết nối API được bảo vệ; các nút tương ứng chưa khả dụng.' : 'Chưa kết nối API được bảo vệ. Bạn có thể xem và chuẩn bị thông tin; thao tác lưu, gửi duyệt chưa khả dụng.'}</p>}
    {notice && <div ref={feedback} tabIndex={-1} role={notice.error ? 'alert' : 'status'} className={notice.error ? 'tv2-product__alert' : 'tv2-product__notice'}>{notice.text}</div>}
    {needsRefresh && onReload && <button className="tv2-product__secondary" onClick={() => { void reload(); }}>Tải lại danh sách</button>}
    {editor && <div className="tv2-product__panel">{stale && <p role="alert" className="tv2-product__alert">Dữ liệu đang sửa đã cũ. Đóng form và tải lại sản phẩm trước khi lưu.</p>}<ProductForm key={editor === 'new' ? 'new' : `${editor.id}:${editor.version}`} product={editor === 'new' ? undefined : editor} categories={data.categories} busy={busy} canSave={!!sessionKey && !needsRefresh && !stale && !!(editor === 'new' ? actions?.create : actions?.update)} onSave={save} onCancel={closePanel} /></div>}
    {confirmation && <section className="tv2-product__panel" aria-labelledby={`${id}-confirm`}><h3 id={`${id}-confirm`} ref={confirmationHeading} tabIndex={-1}>Gửi sản phẩm để xét duyệt?</h3><p><strong>{confirmation.name}</strong> sẽ được gửi tới người có quyền duyệt. Bạn không thể sửa khi sản phẩm đang chờ duyệt.</p><div className="tv2-product__actions"><button disabled={mutationLocked || !sessionKey || !isCurrent(confirmation) || !actions?.submitForReview} onClick={() => {
      if (!isCurrent(confirmation) || !canEditProduct(confirmation.status) || !actions?.submitForReview) return;
      void run(() => actions.submitForReview!({ productId: confirmation.id, expectedVersion: confirmation.version }));
    }}>{busy ? 'Đang gửi…' : 'Xác nhận gửi duyệt'}</button><button className="tv2-product__secondary" disabled={busy} onClick={closePanel}>Hủy</button></div>{!isCurrent(confirmation) && <p role="alert">Sản phẩm đã thay đổi. Tải lại trước khi gửi duyệt.</p>}</section>}
    <div className="tv2-product__toolbar"><div><label htmlFor={`${id}-search`}>Tìm trong danh sách đã tải</label><input id={`${id}-search`} type="search" placeholder="Tên sản phẩm hoặc SKU" value={query} onChange={(event) => setQuery(event.target.value)} /></div><div><label htmlFor={`${id}-status`}>Trạng thái</label><select id={`${id}-status`} value={status} onChange={(event) => setStatus(event.target.value)}><option value="">Tất cả trạng thái</option>{Object.entries(PRODUCT_STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div><p className="tv2-product__count" aria-live="polite">{visible.length}/{data.products.length} sản phẩm đã tải</p></div>
    {!data.products.length ? <div className="tv2-product__empty"><span aria-hidden="true" className="tv2-product__empty-symbol">+</span><h3>Chưa có sản phẩm</h3><p>Thêm sản phẩm đầu tiên cho cửa hàng của bạn.</p></div> : !visible.length ? <div className="tv2-product__empty"><h3>Không tìm thấy sản phẩm</h3><p>Thử tên, SKU hoặc trạng thái khác trong danh sách đã tải.</p><button className="tv2-product__secondary" onClick={() => { setQuery(''); setStatus(''); }}>Xóa bộ lọc</button></div> : <ul className="tv2-product__list" aria-label="Sản phẩm theo cửa hàng">{visible.map((product) => <li key={product.id} className="tv2-product__card"><div className="tv2-product__card-top"><span className={`tv2-product__badge tv2-product__badge--${product.status.toLowerCase()}`}>{PRODUCT_STATUS_LABELS[product.status]}</span><span className="tv2-product__category">{categories.get(product.categoryId)}</span></div><h3>{product.name}</h3><p className="tv2-product__sku">SKU <code>{product.sku}</code></p><p className="tv2-product__price">{formatProductPrice(product.unitPrice)} <span>VND</span></p><div className="tv2-product__card-actions">{canEditProduct(product.status) ? <><button className="tv2-product__secondary" aria-label={`Sửa ${product.name}`} disabled={mutationLocked} onClick={(event) => { returnFocus.current = event.currentTarget; setEditor(product); setConfirmation(null); setNotice(null); }}>Chỉnh sửa</button><button disabled={mutationLocked || !sessionKey || !actions?.submitForReview} onClick={(event) => { returnFocus.current = event.currentTarget; setConfirmation(product); setEditor(null); setNotice(null); }}>Gửi duyệt</button></> : <p className="tv2-product__subtle">{product.status === 'PENDING_REVIEW' ? 'Đang chờ người có quyền duyệt.' : 'Không thể sửa ở trạng thái này.'}</p>}</div></li>)}</ul>}
    <p className="tv2-product__footnote">Danh sách quản lý theo cửa hàng. Trạng thái “Đang bán” không thay thế việc xác minh điều kiện hiển thị trên Catalog công khai.</p>
  </section>;
}
