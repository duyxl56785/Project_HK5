// Optional DOM interaction suite; no test-only dependency is shipped by the feature.
import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { pathToFileURL } from 'node:url';
import { act, createElement, useState } from 'react';
import { createServer } from 'vite';

let window, server, CategoryBrowser, createRoot, root, container;
let calls;
before(async () => {
  const modulePath = process.env.TV2_DOM_MODULE;
  const { Window } = await import(modulePath ? pathToFileURL(modulePath).href : 'happy-dom');
  window = new Window({ url: 'http://localhost/' });
  for (const name of ['window', 'document', 'navigator', 'HTMLElement', 'HTMLInputElement', 'Event', 'MouseEvent', 'KeyboardEvent']) {
    Object.defineProperty(globalThis, name, { value: name === 'window' ? window : window[name], configurable: true });
  }
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  ({ CategoryBrowser } = await server.ssrLoadModule('/src/features/catalog/CategoryBrowser.tsx'));
});
beforeEach(() => {
  calls = [];
  container = window.document.createElement('div');
  window.document.body.append(container);
  root = createRoot(container);
});
afterEach(async () => { await act(() => root.unmount()); container.remove(); });
after(async () => { await server?.close(); await window?.happyDOM.abort(); });

const categories = [
  { id: 'r', name: 'Điện tử', parentId: null },
  { id: 'p', name: 'Điện thoại', parentId: 'r' },
  { id: 'g', name: 'Ốp lưng', parentId: 'p' },
  { id: 's', name: 'Sách', parentId: null },
];
async function mount(state = { status: 'ready', categories }) {
  function Harness() {
    const [selectedId, setSelectedId] = useState(null);
    return createElement(CategoryBrowser, { state, selectedId, onSelect(category) { calls.push(category); setSelectedId(category.id); } });
  }
  await act(() => root.render(createElement(Harness)));
}
const items = () => [...container.querySelectorAll('[role="treeitem"]')];
const item = (name) => items().find((button) => button.textContent.startsWith(name));
const click = (element) => act(() => element.click());
const key = (element, value) => act(() => element.dispatchEvent(new window.KeyboardEvent('keydown', { key: value, bubbles: true })));
const searchInput = () => container.querySelector('input[type="search"]');
function assertFocused(element) {
  assert.equal(window.document.activeElement === element, true,
    `Expected focus on ${element.tagName}; actual ${window.document.activeElement?.tagName}`);
}
async function search(value) {
  const input = searchInput();
  await act(() => {
    Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(input, value);
    input.dispatchEvent(new window.Event('input', { bubbles: true }));
  });
}

test('clearing search restores focus to the still-mounted search input', async () => {
  await mount();
  await search('điện');
  const clear = container.querySelector('.tv2-category-browser__search button');
  assert.ok(clear, 'search event must update React state');
  await act(() => clear.focus());
  assertFocused(clear);
  await click(clear);
  assert.equal(searchInput().value, '');
  assertFocused(searchInput());
});
test('click selects only the exact DTO and toggles a branch', async () => {
  await mount();
  await click(item('Điện tử'));
  assert.equal(item('Điện tử').getAttribute('aria-expanded'), 'true');
  assert.ok(item('Điện thoại'));
  assert.deepEqual(calls, [categories[0]]);
  assert.deepEqual(Object.keys(calls[0]).sort(), ['id', 'name', 'parentId']);
  assert.equal(item('Điện tử').getAttribute('aria-selected'), 'true');
  await click(item('Điện tử'));
  assert.equal(item('Điện thoại'), undefined);
});
test('Arrow keys, Home and End navigate focus without changing selection', async () => {
  await mount();
  await act(() => item('Điện tử').focus());
  await key(item('Điện tử'), 'ArrowRight');
  assertFocused(item('Điện tử'));
  await key(item('Điện tử'), 'ArrowRight');
  assertFocused(item('Điện thoại'));
  await key(item('Điện thoại'), 'ArrowLeft');
  assertFocused(item('Điện tử'));
  await key(item('Điện tử'), 'End');
  assertFocused(item('Sách'));
  await key(item('Sách'), 'ArrowUp');
  assertFocused(item('Điện thoại'));
  await key(item('Điện thoại'), 'Home');
  assertFocused(item('Điện tử'));
  assert.deepEqual(calls, []);
});
test('search opens matching ancestry, supports collapse and no-match recovery', async () => {
  await mount();
  await search('ỐP LƯNG');
  assert.deepEqual(items().map((button) => button.textContent), ['Điện tử', 'Điện thoại', 'Ốp lưng']);
  await click(item('Điện tử'));
  assert.equal(items().length, 1);
  await click(item('Điện tử'));
  assert.equal(items().length, 3);
  await search('không có');
  assert.equal(items().length, 0);
  assert.match(container.textContent, /Không tìm thấy danh mục/);
  await search('');
  assert.equal(items().length, 2);
});
test('expanded ARIA groups own exactly their direct children', async () => {
  await mount();
  await search('ốp');
  const tree = container.querySelector('[role="tree"]');
  assert.equal(tree.getAttribute('aria-owns'), item('Điện tử').id);
  for (const [parent, child] of [['Điện tử', 'Điện thoại'], ['Điện thoại', 'Ốp lưng']]) {
    const group = window.document.getElementById(item(parent).getAttribute('aria-owns'));
    assert.equal(group.getAttribute('role'), 'group');
    assert.equal(group.getAttribute('aria-owns'), item(child).id);
  }
  assert.equal(item('Ốp lưng').hasAttribute('aria-expanded'), false);
  assert.equal(items().filter((button) => button.tabIndex === 0).length, 1);
});
test('retry invokes the supplied callback exactly once', async () => {
  let retries = 0;
  await mount({ status: 'error', onRetry() { retries++; } });
  await click(container.querySelector('button'));
  assert.equal(retries, 1);
  assert.equal(container.querySelector('[role="tree"]'), null);
});
