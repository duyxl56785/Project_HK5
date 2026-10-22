import assert from 'node:assert/strict';
import test from 'node:test';
import { isCanonicalVersion, readOverview } from './sellerStoreModel.ts';

const seller = { id: '11111111-1111-4111-8111-111111111111', version: '9007199254740993', status: 'APPROVED' };
const store = { id: '22222222-2222-4222-8222-222222222222', sellerId: seller.id, name: 'Cửa hàng Việt',
  slug: 'cua-hang-viet', open: true, version: '9223372036854775807' };

test('canonical decimal versions remain exact beyond JS safe integer range', () => {
  for (const version of ['0', '1', '9007199254740993', '9223372036854775807']) assert.equal(isCanonicalVersion(version), true);
  const result = readOverview(seller, store);
  assert.ok(result);
  assert.equal(result.seller.version, '9007199254740993');
  assert.equal(result.store.version, '9223372036854775807');
});
test('noncanonical, numeric and overflowing versions fail without normalization', () => {
  for (const version of [0, null, undefined, '', '01', '+1', '-1', ' 1', '1 ', '1.0', '1e3', '１', '9223372036854775808', '99999999999999999999']) {
    assert.equal(isCanonicalVersion(version), false, String(version));
  }
});
test('all four actual Seller statuses are supported', () => {
  for (const status of ['PENDING_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED']) assert.ok(readOverview({ ...seller, status }, store));
});
test('unknown and client role strings are not Seller statuses', () => {
  for (const status of ['ADMIN', 'SELLER', 'ACTIVE', '', null]) assert.equal(readOverview({ ...seller, status }, store), null);
});
test('private/unknown fields never enter the display projection', () => {
  const result = readOverview({ ...seller, reviewReason: 'PRIVATE_REASON', ownerReference: 'UNTRUSTED_OWNER', role: 'ADMIN' },
    { ...store, createdAt: '2026-10-22T00:00:00Z', updatedAt: '2026-10-22T00:00:00Z', jwt: 'UNTRUSTED_TOKEN_FIELD' });
  assert.deepEqual(result, { seller, store });
  assert.doesNotMatch(JSON.stringify(result), /PRIVATE_REASON|UNTRUSTED/);
});
test('frozen input is not mutated and the display model owns its copies', () => {
  const frozenSeller = Object.freeze({ ...seller });
  const frozenStore = Object.freeze({ ...store });
  const result = readOverview(frozenSeller, frozenStore);
  assert.deepEqual(result, { seller, store });
  assert.notEqual(result.seller, frozenSeller);
  assert.notEqual(result.store, frozenStore);
});
test('only explicit null represents a proven absent Store', () => {
  assert.deepEqual(readOverview(seller, null), { seller, store: null });
  assert.equal(readOverview(seller, undefined), null);
  assert.equal(readOverview(seller, {}), null);
});
test('cross-Seller Store data rejects the entire overview', () => {
  assert.equal(readOverview(seller, { ...store, sellerId: '33333333-3333-4333-8333-333333333333' }), null);
});
test('malformed Seller data cannot create a ready overview', () => {
  for (const value of [null, undefined, [], {}, { ...seller, id: 'not-a-uuid' }, { ...seller, version: 1 }]) {
    assert.equal(readOverview(value, store), null);
  }
});
test('Store identities, boolean and version are required', () => {
  for (const value of [{ ...store, id: null }, { ...store, id: 'unknown' }, { ...store, sellerId: null },
    { ...store, open: 'true' }, { ...store, version: '01' }]) assert.equal(readOverview(seller, value), null);
});
test('Store names and slugs retain source values and existing length boundaries', () => {
  assert.ok(readOverview(seller, { ...store, name: 'ĐIỆN tử Việt Nam' }));
  assert.ok(readOverview(seller, { ...store, name: 'a'.repeat(255), slug: 'a'.repeat(128) }));
  for (const value of [{ ...store, name: '' }, { ...store, name: '  ' }, { ...store, name: 'a'.repeat(256) },
    { ...store, slug: 'a'.repeat(129) }, { ...store, slug: 'Upper' }, { ...store, slug: ' a' }, { ...store, slug: 'a--b' }]) {
    assert.equal(readOverview(seller, value), null);
  }
});
test('closed Store and unapproved Seller are displayed facts, not a selling grant', () => {
  const result = readOverview({ ...seller, status: 'SUSPENDED' }, { ...store, open: false });
  assert.ok(result);
  assert.equal(result.seller.status, 'SUSPENDED');
  assert.equal(result.store.open, false);
});
test('canonical versions reject trailing line terminators rather than matching before them', () => {
  for (const ending of ['\n', '\r', '\r\n', '\u2028', '\u2029']) assert.equal(isCanonicalVersion(`1${ending}`), false);
});
test('UUID display keys reject trailing line terminators even when the relationship matches', () => {
  for (const ending of ['\n', '\r', '\u2028', '\u2029']) {
    assert.equal(readOverview({ ...seller, id: seller.id + ending }, { ...store, sellerId: seller.id + ending }), null);
  }
});
test('Store slug rejects trailing line terminators', () => {
  for (const ending of ['\n', '\r', '\u2028', '\u2029']) assert.equal(readOverview(seller, { ...store, slug: store.slug + ending }), null);
});
