import type { NotificationItem } from './notificationTypes';

/** Rejects malformed/repeated messages instead of rendering an ambiguous notification history. */
export function readNotifications(items: readonly NotificationItem[] | undefined): readonly NotificationItem[] | null {
  if (!items) return null;
  const ids = new Set<string>();
  for (const item of items) {
    if (!item || !item.id || !item.title || !item.body || !item.createdAtLabel || ids.has(item.id)) return null;
    ids.add(item.id);
  }
  return items;
}
