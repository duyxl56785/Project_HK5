import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let server, NotificationCenter;
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  ({ NotificationCenter } = await server.ssrLoadModule('/src/features/notifications/NotificationCenter.tsx'));
});
after(async () => { await server?.close(); });

const unread = { id: 'event-1', title: 'Đơn hàng cần chuẩn bị', body: 'SellerOrder đã được xác nhận.', createdAtLabel: '10:30 · 29/10', read: false };
const read = { id: 'event-2', title: 'Đơn đã giao', body: 'Giao hàng đã hoàn tất.', createdAtLabel: '11:00 · 29/10', read: true };
const render = (state) => renderToStaticMarkup(createElement(NotificationCenter, { state }));

test('renders supplied messages and only offers read action for unread items', () => {
  const html = render({ status: 'ready', items: [unread, read] });
  assert.match(html, /Đơn hàng cần chuẩn bị/);
  assert.match(html, /Đơn đã giao/);
  assert.equal((html.match(/Đánh dấu đã đọc/g) ?? []).length, 1);
});
test('pending mark-read prevents duplicate submission', () => {
  const html = render({ status: 'ready', items: [unread], pendingReadIds: new Set([unread.id]) });
  assert.match(html, /Đang cập nhật…/);
  assert.match(html, /disabled/);
});
test('loading, unauthorized and error states hide stale message data', () => {
  for (const status of ['loading', 'unauthorized', 'error']) {
    const html = render({ status, items: [unread], onRetry() {} });
    assert.doesNotMatch(html, /Đơn hàng cần chuẩn bị|SellerOrder đã được xác nhận/);
  }
});
test('invalid or repeated notification identifiers fail closed', () => {
  for (const items of [[{ ...unread, title: '' }], [unread, { ...unread }]]) {
    const html = render({ status: 'ready', items });
    assert.match(html, /Dữ liệu thông báo không hợp lệ/);
    assert.doesNotMatch(html, /SellerOrder đã được xác nhận/);
  }
});
