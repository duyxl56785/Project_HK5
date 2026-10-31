import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../../shared/api';
import { dateTime, ErrorNote, Loaded, useAction } from '../../shared/ui';

interface Notice {
  readonly id: string; readonly title: string; readonly message: string; readonly link: string | null;
  readonly createdAt: string; readonly read: boolean;
}

export function NotificationBell() {
  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => api<{ count: number }>('/api/notifications/unread-count'),
    refetchInterval: 30_000,
  });
  const count = unread.data?.count ?? 0;
  return <Link to="/notifications" aria-label={`Thông báo, ${count} chưa đọc`}>Thông báo{count > 0 && <span className="count">{count}</span>}</Link>;
}

export function NotificationsPage() {
  const list = useQuery({ queryKey: ['notifications', 'list'], queryFn: () => api<Notice[]>('/api/notifications') });
  const read = useAction((id: string) => api(`/api/notifications/${id}/read`, { method: 'POST' }), ['notifications']);
  const readAll = useAction(() => api('/api/notifications/read-all', { method: 'POST' }), ['notifications']);
  return <div className="stack">
    <div className="row row--between"><h1>Thông báo</h1>
      <button type="button" className="btn" disabled={readAll.isPending} onClick={() => readAll.mutate(undefined)}>Đánh dấu đã đọc tất cả</button></div>
    <ErrorNote error={read.error ?? readAll.error} />
    <Loaded query={list} empty={(rows) => rows.length === 0}>{(rows) => <ul className="list">{rows.map((n) => <li key={n.id} className={n.read ? 'muted' : 'unread'}>
      <div className="row row--between">
        <div><strong>{n.title}</strong><br />{n.message}<br /><small className="muted">{dateTime(n.createdAt)}</small></div>
        <div className="row">
          {n.link && <Link className="btn" to={n.link} onClick={() => { if (!n.read) read.mutate(n.id); }}>Xem</Link>}
          {!n.read && <button type="button" className="btn" onClick={() => read.mutate(n.id)}>Đã đọc</button>}
        </div>
      </div>
    </li>)}</ul>}</Loaded>
  </div>;
}
