import { useId } from 'react';
import type { ReactNode } from 'react';
import type { SellerStatus, SellerStoreState } from './sellerStoreTypes';
import { readOverview } from './sellerStoreModel';
import './SellerStoreOverview.css';

export interface SellerStoreOverviewProps { readonly state: SellerStoreState }

const sellerStatuses: Record<SellerStatus, { label: string; detail: string; tone: string }> = {
  PENDING_REVIEW: { label: 'Chờ xét duyệt', detail: 'Hồ sơ đang chờ được xem xét.', tone: 'pending' },
  APPROVED: { label: 'Đã duyệt', detail: 'Hồ sơ người bán đã được phê duyệt.', tone: 'approved' },
  REJECTED: { label: 'Bị từ chối', detail: 'Hồ sơ chưa được phê duyệt. Vui lòng kiểm tra thông báo từ bộ phận xét duyệt.', tone: 'rejected' },
  SUSPENDED: { label: 'Tạm ngừng', detail: 'Hoạt động người bán đang tạm ngừng.', tone: 'suspended' },
};
const messages = {
  loading: { title: 'Đang tải hồ sơ…', detail: 'Vui lòng đợi trong giây lát.' },
  empty: { title: 'Chưa có hồ sơ người bán', detail: 'Thông tin sẽ xuất hiện tại đây khi bạn có hồ sơ.' },
  unauthorized: { title: 'Không có quyền xem hồ sơ', detail: 'Vui lòng sử dụng tài khoản được phép truy cập hồ sơ này.' },
  unavailable: { title: 'Chức năng chưa sẵn sàng', detail: 'Hiện chưa thể kết nối hồ sơ. Vui lòng quay lại sau.' },
};

export function SellerStoreOverview({ state }: SellerStoreOverviewProps) {
  const id = useId();
  let content: ReactNode;
  if (state.status === 'ready') {
    const data = readOverview(state.seller, state.store);
    if (!data) {
      content = <div className="tv2-seller-overview__notice" role="alert">
        <h3>Dữ liệu hồ sơ không nhất quán</h3><p>Không thể hiển thị thông tin. Vui lòng quay lại sau.</p>
      </div>;
    } else {
      const status = sellerStatuses[data.seller.status];
      content = <div className="tv2-seller-overview__grid">
        <article className="tv2-seller-overview__card" aria-labelledby={`${id}-seller`}>
          <span className="tv2-seller-overview__eyebrow">Hồ sơ của bạn</span>
          <h3 id={`${id}-seller`}>Người bán</h3>
          <span className={`tv2-seller-overview__badge tv2-seller-overview__badge--${status.tone}`}>{status.label}</span>
          <p>{status.detail}</p>
          <dl><div><dt>Phiên bản hồ sơ</dt><dd><code>{data.seller.version}</code></dd></div></dl>
        </article>
        <article className="tv2-seller-overview__card" aria-labelledby={`${id}-store`}>
          <span className="tv2-seller-overview__eyebrow">Thông tin cửa hàng</span>
          <h3 id={`${id}-store`}>{data.store ? data.store.name : 'Chưa có cửa hàng'}</h3>
          {data.store ? <>
            <span className={`tv2-seller-overview__badge tv2-seller-overview__badge--${data.store.open ? 'approved' : 'closed'}`}>
              {data.store.open ? 'Đang mở cửa' : 'Đang đóng cửa'}
            </span>
            <dl>
              <div><dt>Định danh đường dẫn (slug)</dt><dd><code>{data.store.slug}</code></dd></div>
              <div><dt>Phiên bản cửa hàng</dt><dd><code>{data.store.version}</code></dd></div>
            </dl>
            <p className="tv2-seller-overview__note">Trạng thái mở cửa không thay thế việc xét duyệt hồ sơ người bán.</p>
          </> : <p>Chưa ghi nhận cửa hàng gắn với hồ sơ của bạn.</p>}
        </article>
      </div>;
    }
  } else if (state.status === 'error') {
    content = <div className="tv2-seller-overview__notice" role="alert">
      <h3>Không thể tải hồ sơ</h3><p>Vui lòng thử lại để lấy thông tin mới.</p>
      <button type="button" onClick={state.onRetry}>Thử lại</button>
    </div>;
  } else {
    const message = messages[state.status];
    content = <div className="tv2-seller-overview__notice" role="status">
      <h3>{message.title}</h3><p>{message.detail}</p>
    </div>;
  }
  return <section className="tv2-seller-overview" aria-labelledby={`${id}-title`} aria-busy={state.status === 'loading'}>
    <header><span className="tv2-seller-overview__eyebrow">Góc người bán</span>
      <h2 id={`${id}-title`}>Hồ sơ &amp; cửa hàng</h2><p>Theo dõi hồ sơ người bán và thông tin cửa hàng của bạn.</p>
    </header>
    {content}
  </section>;
}
