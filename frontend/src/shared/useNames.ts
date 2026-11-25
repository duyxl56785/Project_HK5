import { useQuery } from '@tanstack/react-query';
import { api } from './api';

const byId = (rows: readonly { readonly id: string; readonly name: string }[]) =>
  Object.fromEntries(rows.map((row) => [row.id, row.name]));

/** Names of the Stores shoppers can see. A closed Store is missing and shows a short id instead. */
export function useStoreNames() {
  return useQuery({ queryKey: ['stores'], queryFn: () => api<{ id: string; name: string }[]>('/api/stores'), select: byId }).data;
}

/** Login emails by account id, for admin pages. The service returns the first 50 accounts. */
export function useAccountEmails() {
  return useQuery({ queryKey: ['admin-accounts', ''], queryFn: () => api<{ id: string; email: string }[]>('/api/admin/accounts?term='),
    select: (rows) => Object.fromEntries(rows.map((row) => [row.id, row.email])) }).data;
}
