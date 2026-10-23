import type { CategoryDto } from './categoryTypes.ts';

export interface CategoryRow extends CategoryDto {
  readonly depth: number;
  readonly position: number;
  readonly siblingCount: number;
  readonly hasChildren: boolean;
}

export type CategoryTreeResult =
  | { readonly ok: true; readonly rows: readonly CategoryRow[] }
  | { readonly ok: false };

export function buildCategoryTree(input: unknown): CategoryTreeResult {
  if (!Array.isArray(input)) return { ok: false };
  const byId = new Map<string, CategoryDto>();
  const children = new Map<string | null, CategoryDto[]>();
  for (const item of input) {
    if (item === null || typeof item !== 'object'
      || typeof item.id !== 'string' || !item.id.trim()
      || typeof item.name !== 'string' || !item.name.trim()
      || !(item.parentId === null || typeof item.parentId === 'string')
      || byId.has(item.id)) return { ok: false };
    const copy: CategoryDto = { id: item.id, name: item.name, parentId: item.parentId };
    byId.set(copy.id, copy);
    const siblings = children.get(copy.parentId) ?? [];
    siblings.push(copy);
    children.set(copy.parentId, siblings);
  }
  for (const item of byId.values()) {
    if (item.parentId !== null && (!byId.has(item.parentId) || item.parentId === item.id)) return { ok: false };
  }
  const rows: CategoryRow[] = [];
  const stack: CategoryRow[] = [];
  function pushChildren(parentId: string | null, depth: number) {
    const siblings = children.get(parentId) ?? [];
    for (let i = siblings.length - 1; i >= 0; i--) {
      const item = siblings[i];
      stack.push({ ...item, depth, position: i + 1, siblingCount: siblings.length, hasChildren: children.has(item.id) });
    }
  }
  pushChildren(null, 1);
  while (stack.length) {
    const item = stack.pop()!;
    rows.push(item);
    pushChildren(item.id, item.depth + 1);
  }
  // Every record has one parent: records unreachable from roots belong to a cycle.
  return rows.length === byId.size ? { ok: true, rows } : { ok: false };
}

export function filterCategoryRows(rows: readonly CategoryRow[], query: string): readonly CategoryRow[] {
  const term = query.trim().toLocaleLowerCase('vi');
  if (!term) return rows;
  const retained = new Set<string>();
  for (let i = rows.length - 1; i >= 0; i--) {
    const row = rows[i];
    if (row.name.toLocaleLowerCase('vi').includes(term) || retained.has(row.id)) {
      retained.add(row.id);
      if (row.parentId !== null) retained.add(row.parentId);
    }
  }
  const found = rows.filter((row) => retained.has(row.id));
  const counts = new Map<string | null, number>();
  const positions = new Map<string | null, number>();
  for (const row of found) counts.set(row.parentId, (counts.get(row.parentId) ?? 0) + 1);
  return found.map((row) => {
    const position = (positions.get(row.parentId) ?? 0) + 1;
    positions.set(row.parentId, position);
    return { ...row, position, siblingCount: counts.get(row.parentId)!, hasChildren: counts.has(row.id) };
  });
}

export function visibleCategoryRows(rows: readonly CategoryRow[], expanded: ReadonlySet<string>): readonly CategoryRow[] {
  let collapsedDepth = Infinity;
  return rows.filter((row) => {
    if (row.depth > collapsedDepth) return false;
    collapsedDepth = row.hasChildren && !expanded.has(row.id) ? row.depth : Infinity;
    return true;
  });
}
