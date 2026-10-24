import { Fragment, useId, useMemo, useRef, useState } from 'react';
import type { CSSProperties, KeyboardEvent } from 'react';
import type { CategoryBrowserState, CategoryDto } from './categoryTypes';
import { buildCategoryTree, filterCategoryRows, visibleCategoryRows } from './categoryTree';
import type { CategoryRow } from './categoryTree';
import './CategoryBrowser.css';

export interface CategoryBrowserProps {
  readonly state: CategoryBrowserState;
  readonly selectedId: string | null;
  readonly onSelect: (category: CategoryDto) => void;
}

const EMPTY: readonly CategoryDto[] = [];

export function CategoryBrowser({ state, selectedId, onSelect }: CategoryBrowserProps) {
  const prefix = useId();
  const [query, setQuery] = useState('');
  const [expandedIds, setExpandedIds] = useState<ReadonlySet<string>>(new Set());
  const [searchCollapsed, setSearchCollapsed] = useState<ReadonlySet<string>>(new Set());
  const [focusedId, setFocusedId] = useState<string | null>(null);
  const buttons = useRef(new Map<string, HTMLButtonElement>());
  const searchRef = useRef<HTMLInputElement>(null);
  const categories = state.status === 'ready' ? state.categories : EMPTY;
  const tree = useMemo(() => buildCategoryTree(categories), [categories]);
  const rows = tree.ok ? tree.rows : [];
  const filtered = filterCategoryRows(rows, query);
  const searching = query.trim().length > 0;
  const expanded = searching
    ? new Set(filtered.filter((row) => row.hasChildren && !searchCollapsed.has(row.id)).map((row) => row.id))
    : expandedIds;
  const visible = visibleCategoryRows(filtered, expanded);
  const selected = rows.find((row) => row.id === selectedId);
  const tabId = visible.find((row) => row.id === focusedId)?.id
    ?? visible.find((row) => row.id === selectedId)?.id ?? visible[0]?.id;
  const itemId = (id: string) => `${prefix}-item-${encodeURIComponent(id)}`;
  const groupId = (id: string) => `${prefix}-group-${encodeURIComponent(id)}`;
  const childIds = new Map<string | null, string[]>();
  for (const row of visible) {
    const siblings = childIds.get(row.parentId) ?? [];
    siblings.push(itemId(row.id));
    childIds.set(row.parentId, siblings);
  }

  function toggle(id: string) {
    const update = (before: ReadonlySet<string>) => {
      const next = new Set(before);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    };
    if (searching) setSearchCollapsed(update); else setExpandedIds(update);
  }

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>, row: CategoryRow, index: number) {
    let target: string | undefined;
    switch (event.key) {
      case 'ArrowDown': target = visible[Math.min(index + 1, visible.length - 1)]?.id; break;
      case 'ArrowUp': target = visible[Math.max(index - 1, 0)]?.id; break;
      case 'Home': target = visible[0]?.id; break;
      case 'End': target = visible.at(-1)?.id; break;
      case 'ArrowRight':
        if (row.hasChildren) {
          if (!expanded.has(row.id)) toggle(row.id); else target = visible[index + 1]?.id;
        }
        break;
      case 'ArrowLeft':
        if (row.hasChildren && expanded.has(row.id)) toggle(row.id); else target = row.parentId ?? undefined;
        break;
      default: return;
    }
    event.preventDefault();
    if (target) buttons.current.get(target)?.focus();
  }

  return (
    <section className="tv2-category-browser" aria-labelledby={`${prefix}-title`} aria-busy={state.status === 'loading'}>
      <header className="tv2-category-browser__header">
        <span className="tv2-category-browser__eyebrow">Khám phá theo danh mục</span>
        <h2 id={`${prefix}-title`}>Bạn đang tìm gì?</h2>
        <p>Chọn một danh mục để bắt đầu khám phá.</p>
      </header>

      {state.status === 'loading' ? (
        <div className="tv2-category-browser__state" role="status">
          <p>Đang tải danh mục…</p>
          <div className="tv2-category-browser__skeleton" aria-hidden="true"><span /><span /><span /></div>
        </div>
      ) : state.status === 'error' ? (
        <div className="tv2-category-browser__state tv2-category-browser__error" role="alert">
          <h3>Không thể tải danh mục</h3><p>Vui lòng thử lại để lấy dữ liệu mới.</p>
          <button type="button" className="tv2-category-browser__retry" onClick={state.onRetry}>Thử lại</button>
        </div>
      ) : !tree.ok ? (
        <div className="tv2-category-browser__state tv2-category-browser__error" role="alert">
          <h3>Dữ liệu danh mục không nhất quán</h3><p>Không thể hiển thị danh mục. Vui lòng quay lại sau.</p>
        </div>
      ) : rows.length === 0 ? (
        <div className="tv2-category-browser__state" role="status">
          <h3>Chưa có danh mục</h3><p>Danh mục sẽ xuất hiện tại đây khi có dữ liệu.</p>
        </div>
      ) : (
        <>
          <div className="tv2-category-browser__search">
            <label htmlFor={`${prefix}-search`}>Tìm danh mục</label>
            <input ref={searchRef} id={`${prefix}-search`} type="search" value={query} placeholder="Nhập tên danh mục…"
              onChange={(event) => { setQuery(event.target.value); setSearchCollapsed(new Set()); }} />
            {query && <button type="button" onClick={() => {
              setQuery(''); setSearchCollapsed(new Set()); searchRef.current?.focus();
            }}>Xóa tìm kiếm</button>}
          </div>
          <p id={`${prefix}-help`} className="tv2-category-browser__help">
            Dùng phím ↑ ↓ để di chuyển, → ← để mở hoặc thu nhánh. Enter hoặc Space để chọn.
            Chọn một nhánh cũng mở hoặc thu nhánh đó.
          </p>
          {visible.length === 0 ? (
            <div className="tv2-category-browser__state" role="status">
              <h3>Không tìm thấy danh mục</h3><p>Thử tên khác hoặc xóa tìm kiếm.</p>
            </div>
          ) : (
            <div className="tv2-category-browser__tree" role="tree" aria-labelledby={`${prefix}-title`}
              aria-describedby={`${prefix}-help`} aria-owns={childIds.get(null)?.join(' ')}>
              {visible.map((row, index) => {
                const open = row.hasChildren && expanded.has(row.id);
                return (
                  <Fragment key={row.id}>
                    <button type="button" role="treeitem" id={itemId(row.id)}
                      ref={(button) => { if (button) buttons.current.set(row.id, button); else buttons.current.delete(row.id); }}
                      className="tv2-category-browser__item"
                      style={{ '--category-indent': Math.min(row.depth - 1, 5) } as CSSProperties}
                      aria-level={row.depth} aria-posinset={row.position} aria-setsize={row.siblingCount}
                      aria-expanded={row.hasChildren ? open : undefined} aria-selected={row.id === selectedId}
                      aria-owns={open ? groupId(row.id) : undefined} tabIndex={row.id === tabId ? 0 : -1}
                      onFocus={() => setFocusedId(row.id)} onKeyDown={(event) => onKeyDown(event, row, index)}
                      onClick={() => {
                        if (row.hasChildren) toggle(row.id);
                        onSelect({ id: row.id, name: row.name, parentId: row.parentId });
                      }}>
                      <span className="tv2-category-browser__indicator" aria-hidden="true">
                        {row.hasChildren ? <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
                          className={open ? 'tv2-category-browser__chevron-open' : undefined}>
                          <path d="m9 5 7 7-7 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                        </svg> : <span className="tv2-category-browser__dot" />}
                      </span>
                      <span className="tv2-category-browser__name">{row.depth > 6 && <small>Cấp {row.depth} · </small>}{row.name}</span>
                      {row.id === selectedId && <span className="tv2-category-browser__selected" aria-hidden="true">Đã chọn</span>}
                    </button>
                    {/* ARIA ownership preserves hierarchy while flat DOM avoids deeply recursive rendering. */}
                    {open && <div role="group" id={groupId(row.id)} aria-owns={childIds.get(row.id)?.join(' ')} />}
                  </Fragment>
                );
              })}
            </div>
          )}
          <p className="tv2-category-browser__selection" role="status" aria-live="polite" aria-atomic="true">
            {selected ? <>Đang chọn: <strong>{selected.name}</strong></> : 'Chưa chọn danh mục'}
          </p>
        </>
      )}
    </section>
  );
}
