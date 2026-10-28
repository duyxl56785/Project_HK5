import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { CategoryBrowser } from '../index';
import type { CategoryBrowserState, CategoryDto } from '../index';
import '../../../styles.css';

// Preview/test fixtures only. This entry is not mounted by the application or exported by the feature.
const categories: readonly CategoryDto[] = [
  { id: 'preview-electronics', name: 'Điện tử', parentId: null },
  { id: 'preview-phone', name: 'Điện thoại', parentId: 'preview-electronics' },
  { id: 'preview-case', name: 'Ốp lưng', parentId: 'preview-phone' },
  { id: 'preview-book', name: 'Sách', parentId: null },
  ...Array.from({ length: 10 }, (_, depth) => ({
    id: `preview-depth-${depth}`,
    name: depth === 9 ? 'Phụ kiện thiết kế dành riêng cho không gian sống và làm việc hiện đại của gia đình Việt Nam'
      : `Danh mục cấp ${depth + 1}`,
    parentId: depth ? `preview-depth-${depth - 1}` : null,
  })),
];

function Preview() {
  const mode = new URLSearchParams(window.location.search).get('state');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [retried, setRetried] = useState(false);
  let state: CategoryBrowserState = { status: 'ready', categories };
  if (mode === 'loading') state = { status: 'loading' };
  if (mode === 'error' && !retried) state = { status: 'error', onRetry: () => setRetried(true) };
  if (mode === 'empty') state = { status: 'ready', categories: [] };
  if (mode === 'invalid') state = { status: 'ready', categories: [...categories, categories[0]] };
  return (
    <main style={{ maxWidth: '56rem', margin: '0 auto', padding: '1.25rem', boxSizing: 'border-box' }}>
      <h1>Category Browser — kiểm thử UI</h1>
      <p>Dữ liệu mẫu phục vụ kiểm thử. Không kết nối API hoặc tài khoản người dùng.</p>
      <CategoryBrowser state={state} selectedId={selectedId} onSelect={(category) => setSelectedId(category.id)} />
    </main>
  );
}

createRoot(document.getElementById('root')!).render(<Preview />);
