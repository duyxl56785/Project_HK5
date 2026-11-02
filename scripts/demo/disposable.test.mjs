import assert from 'node:assert/strict';
import { test } from 'node:test';
import { newProject, requireDisposable } from './disposable.mjs';

test('the kept demo stack is refused', () => {
  assert.throws(() => requireDisposable('hk5-demo', []), /not a disposable/);
});

test('a project that already has containers is refused', () => {
  assert.throws(() => requireDisposable(newProject(), ['abc123']), /already has containers/);
});

test('a new project is accepted', () => {
  assert.doesNotThrow(() => requireDisposable(newProject(), []));
});
