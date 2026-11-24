import assert from 'node:assert/strict';
import test from 'node:test';
import { nameOr } from './names.ts';

test('a known id shows its name', () => {
  assert.equal(nameOr({ 'a1b2c3d4-0000': 'Tiệm Áo Sài Gòn' }, 'a1b2c3d4-0000', 'Cửa hàng'), 'Tiệm Áo Sài Gòn');
});

test('an unknown id shows a short label instead of the whole UUID', () => {
  assert.equal(nameOr(undefined, '702bb0c4-1111-2222-3333-444455556666', 'Cửa hàng'), 'Cửa hàng #702BB0C4');
  assert.equal(nameOr({}, '702bb0c4-1111-2222-3333-444455556666', 'Tài khoản'), 'Tài khoản #702BB0C4');
});
