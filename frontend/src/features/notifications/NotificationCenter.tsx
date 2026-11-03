import { useId } from 'react';
import type { ReactNode } from 'react';
import type { NotificationState } from './notificationTypes';
import { readNotifications } from './notificationModel';
import './NotificationCenter.css';

export interface NotificationCenterProps { readonly state: NotificationState }

export function NotificationCenter({ state }: NotificationCenterProps) {
  const id = useId();
  let content: ReactNode;
  if (state.status === 'ready') {
    const items = readNotifications(state.items);
    if (!items) {
      content = <div className="tv4-notifications__notice" role="alert"><h3>Dữ liệu thông báo không hợp lệ</h3><p>Không thể hiển thị danh sách chưa được xác thực.</p></div>;
    } else if (!items.length) {
      content = <div className="tv4-notifications__notice" role="status"><h3>Chưa có thông báo</h3><p>Các cập nhật phù hợp sẽ xuất hiện tại đây.</p></div>;
    } else {
      content = <ul className="tv4-notifications__list" aria-label="Danh sách thông báo">{items.map((item) => {
        const pending = state.pendingReadIds?.has(item.id) ?? false;
        return <li className={`tv4-notifications__item ${item.read ? '' : 'tv4-notifications__item--unread'}`} key={item.id}>
          <div className="tv4-notifications__item-head"><h3>{item.title}</h3><time>{item.createdAtLabel}</time></div>
          <p>{item.body}</p>
          {!item.read && <button type="button" disabled={pending} onClick={() => state.onMarkRead?.(item.id)}>
            {pending ? 'Đang cập nhật…' : 'Đánh dấu đã đọc'}
          </button>}
        </li>;
      })}</ul>;
    }
  } else if (state.status === 'unauthorized') {
    content = <div className="tv4-notifications__notice" role="alert"><h3>Không có quyền xem thông báo</h3><p>Vui lòng dùng tài khoản có quyền truy cập.</p></div>;
  } else if (state.status === 'error') {
    content = <div className="tv4-notifications__notice" role="alert"><h3>Không thể tải thông báo</h3><p>Vui lòng thử lại để lấy dữ liệu mới.</p><button type="button" onClick={state.onRetry}>Thử lại</button></div>;
  } else if (state.status === 'loading') {
    content = <div className="tv4-notifications__notice" role="status"><h3>Đang tải thông báo…</h3><p>Đang lấy dữ liệu mới nhất.</p></div>;
  } else {
    content = <div className="tv4-notifications__notice" role="status"><h3>Chưa có thông báo</h3><p>Các cập nhật phù hợp sẽ xuất hiện tại đây.</p></div>;
  }
  return <section className="tv4-notifications" aria-labelledby={`${id}-title`} aria-busy={state.status === 'loading'}>
    <header><span className="tv4-notifications__eyebrow">TV4 · Notifications</span><h2 id={`${id}-title`}>Thông báo</h2>
      <p>Chỉ hiển thị thông điệp do dịch vụ đã được phê duyệt cung cấp. Không suy đoán event, người nhận hay thời gian ở trình duyệt.</p></header>
    {content}
  </section>;
}
