import assert from 'node:assert/strict';
import test from 'node:test';
import { buildCategoryTree, filterCategoryRows, visibleCategoryRows } from './categoryTree.ts';

const category = (id, name = id, parentId = null) => ({ id, name, parentId });
function rows(input) {
  const result = buildCategoryTree(input);
  assert.equal(result.ok, true);
  return result.rows;
}
const ids = (values) => values.map((value) => value.id);

test('empty category collection is valid', () => assert.deepEqual(rows([]), []));
test('one root retains the exact DTO and accessible position', () => {
  assert.deepEqual(rows([category('a', 'Điện tử')]), [
    { id: 'a', name: 'Điện tử', parentId: null, depth: 1, position: 1, siblingCount: 1, hasChildren: false },
  ]);
});
test('multiple roots preserve backend order and sibling positions', () => {
  const result = rows([category('b'), category('a')]);
  assert.deepEqual(ids(result), ['b', 'a']);
  assert.deepEqual(result.map((r) => [r.position, r.siblingCount]), [[1, 2], [2, 2]]);
});
test('unordered parent/child records become a complete preorder hierarchy', () => {
  const result = rows([category('leaf', 'Ốp lưng', 'phone'), category('root'), category('phone', 'Điện thoại', 'root')]);
  assert.deepEqual(ids(result), ['root', 'phone', 'leaf']);
  assert.deepEqual(result.map((r) => [r.depth, r.hasChildren]), [[1, true], [2, true], [3, false]]);
});
test('input objects and arrays are never changed or normalized', () => {
  const source = Object.freeze([Object.freeze(category('exact-ID', '  ĐIỆN tử  '))]);
  const result = rows(source);
  assert.equal(result[0].name, '  ĐIỆN tử  ');
  assert.equal(result[0].id, 'exact-ID');
  assert.deepEqual(source, [category('exact-ID', '  ĐIỆN tử  ')]);
});
for (const [name, input] of [
  ['duplicate IDs', [category('a'), category('a')]],
  ['missing parent', [category('a', 'A', 'absent')]],
  ['self-parent', [category('a', 'A', 'a')]],
  ['cycle without roots', [category('a', 'A', 'b'), category('b', 'B', 'a')]],
  ['cycle beside valid root', [category('root'), category('a', 'A', 'b'), category('b', 'B', 'a')]],
  ['null collection', null],
  ['non-array collection', {}],
  ['null record', [null]],
  ['wrong ID type', [{ id: 7, name: 'A', parentId: null }]],
  ['blank name', [category('a', '   ')]],
  ['missing parent field', [{ id: 'a', name: 'A' }]],
]) {
  test(`inconsistent input fails as a whole: ${name}`, () => assert.deepEqual(buildCategoryTree(input), { ok: false }));
}
test('a deep hierarchy is built without recursive stack growth', () => {
  const input = Array.from({ length: 10000 }, (_, i) => category(String(i), String(i), i ? String(i - 1) : null));
  const result = rows(input);
  assert.equal(result.length, 10000);
  assert.equal(result.at(-1).depth, 10000);
});
test('search is case-insensitive and retains all ancestors without changing names', () => {
  const source = rows([category('r', 'Điện tử'), category('p', 'Điện Thoại', 'r'), category('x', 'Sách')]);
  assert.deepEqual(ids(filterCategoryRows(source, '  ĐIỆN THOẠI ')), ['r', 'p']);
  assert.equal(source[1].name, 'Điện Thoại');
});
test('search with no match returns no rows; blank query restores all rows', () => {
  const source = rows([category('a', 'Sách')]);
  assert.deepEqual(filterCategoryRows(source, 'máy ảnh'), []);
  assert.deepEqual(filterCategoryRows(source, '  '), source);
});
test('filtered sibling positions and branch flags describe the filtered tree', () => {
  const source = rows([category('r', 'Gốc'), category('a', 'Sách', 'r'), category('b', 'Điện thoại', 'r')]);
  const found = filterCategoryRows(source, 'điện');
  assert.deepEqual(ids(found), ['r', 'b']);
  assert.equal(found[1].position, 1);
  assert.equal(found[1].siblingCount, 1);
  assert.equal(filterCategoryRows(source, 'Gốc')[0].hasChildren, false);
});
test('collapsed and expanded branches never reveal hidden descendants', () => {
  const source = rows([category('r'), category('a', 'A', 'r'), category('b', 'B', 'a'), category('other')]);
  assert.deepEqual(ids(visibleCategoryRows(source, new Set())), ['r', 'other']);
  const expanded = new Set(['r']);
  assert.deepEqual(ids(visibleCategoryRows(source, expanded)), ['r', 'a', 'other']);
  assert.deepEqual([...expanded], ['r']);
  assert.deepEqual(ids(visibleCategoryRows(source, new Set(['r', 'a']))), ['r', 'a', 'b', 'other']);
});
