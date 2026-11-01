import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { ApiError } from './api';

const vnd = new Intl.NumberFormat('vi-VN');
export const money = (value: number | string | null | undefined) => `${vnd.format(Number(value ?? 0))} ₫`;
export const dateTime = (value: string | null | undefined) => value ? new Date(value).toLocaleString('vi-VN') : '—';
export const shortId = (id: string) => id.slice(0, 8).toUpperCase();

export function errorText(error: unknown) {
  if (error instanceof ApiError) return error.message;
  return 'Không kết nối được máy chủ. Vui lòng thử lại.';
}

export function ErrorNote({ error }: { readonly error: unknown }) {
  return error ? <p className="note note--error" role="alert">{errorText(error)}</p> : null;
}

export function Field({ label, error, children }: {
  readonly label: ReactNode; readonly error?: { readonly message?: string }; readonly children: ReactNode;
}) {
  return <label className="field">{label}{children}
    {error?.message && <small className="field__error" role="alert">{error.message}</small>}
  </label>;
}

/** One place for the three states every data screen has. */
export function Loaded<T>({ query, empty, children }: {
  readonly query: { data: T | undefined; isPending: boolean; error: unknown; refetch: () => unknown };
  readonly empty?: (data: T) => boolean;
  readonly children: (data: T) => ReactNode;
}) {
  if (query.isPending) return <p role="status">Đang tải…</p>;
  if (query.error || query.data === undefined) return <div>
    <ErrorNote error={query.error} />
    <button type="button" className="btn" onClick={() => query.refetch()}>Thử lại</button>
  </div>;
  if (empty?.(query.data)) return <p className="muted">Chưa có dữ liệu.</p>;
  return <>{children(query.data)}</>;
}

/** Mutation that refreshes the listed query prefixes on success. */
export function useAction<V, R = unknown>(run: (value: V) => Promise<R>, invalidate: readonly string[], onDone?: (result: R) => void) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: run,
    onSuccess: async (result) => {
      await Promise.all(invalidate.map((key) => client.invalidateQueries({ queryKey: [key] })));
      onDone?.(result);
    },
  });
}

const labels: Record<string, string> = {
  PLACED: 'Chờ xác nhận', ACCEPTED: 'Đã xác nhận', PREPARING: 'Đang chuẩn bị', READY_FOR_HANDOFF: 'Sẵn sàng bàn giao',
  DISPATCHED: 'Đang giao', DELIVERED: 'Đã giao', CANCELLED: 'Đã hủy', REJECTED: 'Bị từ chối',
  PENDING: 'Chờ thu', COLLECTED: 'Đã thu', VOID: 'Không thu',
  PENDING_REVIEW: 'Chờ duyệt', APPROVED: 'Đã duyệt', SUSPENDED: 'Tạm ngừng', DRAFT: 'Bản nháp', ACTIVE: 'Đang bán',
  INACTIVE: 'Ngừng bán', ARCHIVED: 'Lưu trữ',
};

export function Badge({ value }: { readonly value: string }) {
  return <span className={`badge badge--${value.toLowerCase()}`}>{labels[value] ?? value}</span>;
}

export function StatusFilter({ value, options, onChange, allLabel }: {
  readonly value: string; readonly options: readonly string[]; readonly onChange: (next: string) => void; readonly allLabel?: string;
}) {
  return <label className="field field--inline">Trạng thái
    <select value={value} onChange={(event) => onChange(event.currentTarget.value)}>
      {allLabel && <option value="">{allLabel}</option>}
      {options.map((option) => <option key={option} value={option}>{labels[option] ?? option}</option>)}
    </select>
  </label>;
}
