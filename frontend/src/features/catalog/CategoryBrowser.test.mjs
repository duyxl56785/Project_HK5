import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let server;
let CategoryBrowser;
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  ({ CategoryBrowser } = await server.ssrLoadModule('/src/features/catalog/CategoryBrowser.tsx'));
});
after(async () => { await server?.close(); });
const source = [
  { id: 'root', name: 'Điện tử', parentId: null },
  { id: 'child', name: 'Điện thoại', parentId: 'root' },
  { id: 'other', name: 'Sách', parentId: null },
];
const ready = { status: 'ready', categories: source };
function render(state = ready, selectedId = null) {
  return renderToStaticMarkup(createElement(CategoryBrowser, { state, selectedId, onSelect() {} }));
}

test('loading is announced without an empty or usable tree', () => {
  const html = render({ status: 'loading' });
  assert.match(html, /aria-busy="true"/);
  assert.match(html, /role="status"/);
  assert.match(html, /Đang tải danh mục/);
  assert.doesNotMatch(html, /role="tree"|Chưa có danh mục/);
});
test('source error has an accessible retry action and no tree', () => {
  const html = render({ status: 'error', onRetry() {} });
  assert.match(html, /role="alert"/);
  assert.match(html, /<button[^>]*>Thử lại<\/button>/);
  assert.doesNotMatch(html, /role="tree"/);
});
test('a valid empty result has its own state', () => {
  const html = render({ status: 'ready', categories: [] });
  assert.match(html, /Chưa có danh mục/);
  assert.doesNotMatch(html, /role="alert"|role="tree"/);
});
for (const [name, categories] of [
  ['duplicate', [...source, source[0]]],
  ['orphan', [{ id: 'a', name: 'Không được hiển thị', parentId: 'missing' }]],
  ['cycle', [{ id: 'a', name: 'A', parentId: 'b' }, { id: 'b', name: 'B', parentId: 'a' }]],
]) {
  test(`invalid ${name} data displays an error instead of a partial tree`, () => {
    const html = render({ status: 'ready', categories });
    assert.match(html, /role="alert"/);
    assert.match(html, /Dữ liệu danh mục không nhất quán/);
    assert.doesNotMatch(html, /role="tree"|Không được hiển thị/);
  });
}
test('initial tree shows roots with collapsed branch and explicit ARIA positions', () => {
  const html = render();
  assert.match(html, /role="tree"/);
  assert.equal((html.match(/role="treeitem"/g) ?? []).length, 2);
  assert.match(html, /aria-expanded="false"/);
  assert.match(html, /aria-level="1"/);
  assert.match(html, /aria-setsize="2"/);
  assert.equal((html.match(/tabindex="0"/g) ?? []).length, 1);
});
test('selection is controlled and can describe a hidden descendant', () => {
  const selected = render(ready, 'root');
  assert.match(selected, /aria-selected="true"/);
  assert.match(selected, /Đang chọn:/);
  assert.match(render(ready, 'child'), /Điện thoại/);
  assert.match(render(ready, 'absent'), /Chưa chọn danh mục/);
  assert.doesNotMatch(render(ready, 'absent'), /aria-selected="true"/);
});
test('category names are escaped, never interpreted as markup', () => {
  const html = render({ status: 'ready', categories: [{ id: 'x', name: '<script>alert(1)</script>', parentId: null }] });
  assert.match(html, /&lt;script&gt;/);
  assert.doesNotMatch(html, /<script>/);
});
test('each browser has distinct labels and search control IDs', () => {
  const html = renderToStaticMarkup(createElement('div', null,
    createElement(CategoryBrowser, { state: ready, selectedId: null, onSelect() {} }),
    createElement(CategoryBrowser, { state: ready, selectedId: null, onSelect() {} })));
  const inputIds = [...html.matchAll(/<input[^>]*id="([^"]+)"/g)].map((match) => match[1]);
  assert.equal(inputIds.length, 2);
  assert.notEqual(inputIds[0], inputIds[1]);
  for (const id of inputIds) assert.ok(html.includes(`for="${id}"`));
});
