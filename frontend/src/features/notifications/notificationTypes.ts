export interface NotificationItem {
  readonly id: string;
  readonly title: string;
  readonly body: string;
  /** Display-ready value supplied by the approved API; no client clock inference. */
  readonly createdAtLabel: string;
  readonly read: boolean;
}

export interface NotificationState {
  readonly status: 'loading' | 'empty' | 'ready' | 'error' | 'unauthorized';
  readonly items?: readonly NotificationItem[];
  readonly pendingReadIds?: ReadonlySet<string>;
  readonly onMarkRead?: (notificationId: string) => void;
  readonly onRetry?: () => void;
}
