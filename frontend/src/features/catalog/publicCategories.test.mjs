import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicCategories } from './publicCategories.ts';
const id = '11111111-1111-4111-8111-111111111111';
const item = { id, name: 'Gốm', parentId: null };
test('requests exact real public list via shared client and projects only Category DTO', async () => {
  const calls = [];
  const signal = new AbortController().signal;
  const categories = await loadPublicCategories({ request: async (...args) => {
    calls.push(args); return Response.json([{ ...item, private: 'hidden' }]);
  } }, signal);
  assert.deepEqual(calls, [['/api/categories', { method: 'GET', signal }]]);
  assert.deepEqual(categories, [item]);
});
test('empty real collection remains empty', async () => {
  assert.deepEqual(await loadPublicCategories({ request: async () => Response.json([]) }), []);
});
for (const input of [{ items: [item] }, [item, item], [{ ...item, id: 'bad' }],
  [{ ...item, parentId: id }], [{ ...item, parentId: '22222222-2222-4222-8222-222222222222' }]]) {
  test('reject malformed, duplicated or inconsistent categories', async () => {
    await assert.rejects(loadPublicCategories({ request: async () => Response.json(input) }), /CATEGORY_RESPONSE_INVALID/);
  });
}
test('shared client error is propagated without retry or fake empty fallback', async () => {
  const failure = new Error('upstream unavailable'); let calls = 0;
  await assert.rejects(loadPublicCategories({ request: async () => { calls++; throw failure; } }),
    (error) => error === failure);
  assert.equal(calls, 1);
});
