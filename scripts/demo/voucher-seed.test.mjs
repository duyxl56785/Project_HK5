// Run with: node --test scripts/demo/voucher-seed.test.mjs
import assert from 'node:assert/strict';
import test from 'node:test';
import { expectedStatus, seedLimitRequest, seedVouchers } from './voucher-seed.mjs';

const day = 24 * 60 * 60 * 1000;
const items = [
  ['TAMDUNG10', 'Paused', 'PERCENT', 10, 100000, 30000, ['publish', 'pause']],
  ['DAHUY50K', 'Cancelled', 'FIXED', 50000, 300000, null, ['cancel']],
  ['SAPMO15', 'Scheduled', 'PERCENT', 15, 200000, 50000, ['publish'], 7 * day],
  ['GTI01', 'Active', 'FIXED', 5000, 200000, null, ['publish']],
  ['THEM21', 'Draft', 'FIXED', 20000, 150000, null, []],
];

/** In-memory Store: records every write and moves status the way the server does. */
function fakeStore(existing = [], { refuse = () => false } = {}) {
  const vouchers = existing.map((voucher) => ({ ...voucher }));
  const writes = [];
  const api = {
    list: async (page, size) => vouchers.slice(page * size, page * size + size).map((voucher) => ({ ...voucher })),
    create: async (body) => {
      writes.push(['create', body.code]);
      const voucher = { id: `id-${body.code}`, code: body.code, status: 'DRAFT', version: '0', startsIn: Date.parse(body.startsAt) - Date.now() };
      vouchers.push(voucher);
      return { ...voucher };
    },
    act: async (id, action, version) => {
      const voucher = vouchers.find((row) => row.id === id);
      writes.push([action, voucher.code]);
      if (refuse(voucher.code, action)) return { ok: false, status: 409, json: { code: 'VOUCHER_QUOTA_EXCEEDED' } };
      assert.equal(version, voucher.version, 'the seed sends the version it last read');
      voucher.status = action === 'publish' ? (voucher.startsIn > 0 ? 'SCHEDULED' : 'ACTIVE') : { pause: 'PAUSED', cancel: 'CANCELLED' }[action];
      voucher.version = String(Number(voucher.version) + 1);
      return { ok: true, status: 200, json: { ...voucher } };
    },
  };
  return { api, vouchers, writes };
}

test('expected status follows the last action and the start time', () => {
  assert.equal(expectedStatus([], -day), 'DRAFT');
  assert.equal(expectedStatus(['publish'], -day), 'ACTIVE');
  assert.equal(expectedStatus(['publish'], day), 'SCHEDULED');
  assert.equal(expectedStatus(['publish', 'pause'], -day), 'PAUSED');
  assert.equal(expectedStatus(['cancel'], -day), 'CANCELLED');
});

test('a fresh Store gets every sample voucher in its expected state', async () => {
  const store = fakeStore();
  const result = await seedVouchers(store.api, 'S', items);
  assert.deepEqual(result, { blocked: [], differs: [] });
  assert.deepEqual(Object.fromEntries(store.vouchers.map((voucher) => [voucher.code, voucher.status])),
    { TAMDUNG10: 'PAUSED', DAHUY50K: 'CANCELLED', SAPMO15: 'SCHEDULED', GTI01: 'ACTIVE', THEM21: 'DRAFT' });
});

test('a re-run on a complete Store writes nothing', async () => {
  const store = fakeStore();
  await seedVouchers(store.api, 'S', items);
  store.writes.length = 0;
  assert.deepEqual(await seedVouchers(store.api, 'S', items), { blocked: [], differs: [] });
  assert.deepEqual(store.writes, []);
});

test('existing vouchers with a sample code in another state keep status and version, and are reported', async () => {
  const before = [
    { id: 'u1', code: 'SAPMO15', status: 'DRAFT', version: '4' },
    { id: 'u2', code: 'TAMDUNG10', status: 'ACTIVE', version: '7' },
    { id: 'u3', code: 'THEM21', status: 'EXPIRED', version: '2' },
  ];
  const store = fakeStore(before);
  const result = await seedVouchers(store.api, 'S', items);
  assert.deepEqual(store.vouchers.slice(0, 3), before);
  assert.deepEqual(store.writes.filter(([, code]) => ['SAPMO15', 'TAMDUNG10', 'THEM21'].includes(code)), []);
  assert.deepEqual(result.blocked, []);
  assert.deepEqual(result.differs, [
    'S TAMDUNG10: is ACTIVE, sample expects PAUSED; left unchanged',
    'S SAPMO15: is DRAFT, sample expects SCHEDULED; left unchanged',
    'S THEM21: is EXPIRED, sample expects DRAFT; left unchanged',
  ]);
});

test('a partial run is reported on re-run, not finished', async () => {
  const store = fakeStore([{ id: 'p1', code: 'SAPMO15', status: 'DRAFT', version: '0' }]);
  const result = await seedVouchers(store.api, 'S', items);
  assert.equal(store.vouchers.find((voucher) => voucher.code === 'SAPMO15').status, 'DRAFT');
  assert.deepEqual(result.differs, ['S SAPMO15: is DRAFT, sample expects SCHEDULED; left unchanged']);
});

test('vouchers with other codes are never touched and paging reads every page', async () => {
  const others = Array.from({ length: 60 }, (_, i) => ({ id: `o${i}`, code: `OTHER${i}`, status: 'ACTIVE', version: '1' }));
  const store = fakeStore([...others, { id: 'late', code: 'GTI01', status: 'ACTIVE', version: '3' }]);
  const result = await seedVouchers(store.api, 'S', items);
  assert.deepEqual(store.vouchers.slice(0, 61), [...others, { id: 'late', code: 'GTI01', status: 'ACTIVE', version: '3' }]);
  assert.equal(store.writes.some(([, code]) => code === 'GTI01' || code.startsWith('OTHER')), false);
  assert.deepEqual(result.differs, []);
});

test('a publish refused for quota is BLOCKED and stops that voucher only', async () => {
  const store = fakeStore([], { refuse: (code, action) => code === 'GTI01' && action === 'publish' });
  const result = await seedVouchers(store.api, 'S', items);
  assert.deepEqual(result.blocked, ['S GTI01 publish: 409 {"code":"VOUCHER_QUOTA_EXCEEDED"}']);
  assert.equal(store.vouchers.find((voucher) => voucher.code === 'GTI01').status, 'DRAFT');
  assert.equal(store.vouchers.find((voucher) => voucher.code === 'THEM21').status, 'DRAFT');
});

function fakeRequests(latest, reply = { ok: true, status: 201, text: '' }) {
  const created = [];
  return { created, api: { latest: async () => latest, create: async (body, key) => { created.push(key); return reply; } } };
}
const body = { requestedLimit: 30 };

test('a Store that never filed a request gets one PENDING request', async () => {
  const requests = fakeRequests(undefined);
  assert.deepEqual(await seedLimitRequest(requests.api, 'S', body, 123), { blocked: [], differs: [] });
  assert.deepEqual(requests.created, ['seed-limit-123']);
});

test('a PENDING request is kept and no second one is filed', async () => {
  const requests = fakeRequests({ status: 'PENDING' });
  assert.deepEqual(await seedLimitRequest(requests.api, 'S', body), { blocked: [], differs: [] });
  assert.deepEqual(requests.created, []);
});

for (const status of ['APPROVED', 'REJECTED', 'CANCELLED']) {
  test(`an Admin-decided ${status} request is not replaced, and the missing PENDING is reported`, async () => {
    const requests = fakeRequests({ status });
    const result = await seedLimitRequest(requests.api, 'S', body);
    assert.deepEqual(requests.created, []);
    assert.deepEqual(result, { blocked: [], differs: [`S limit request: newest is ${status}, no PENDING request; left unchanged`] });
  });
}

test('a refused request is BLOCKED', async () => {
  const requests = fakeRequests(undefined, { ok: false, status: 409, text: 'VOUCHER_LIMIT_REQUEST_PENDING_EXISTS' });
  const result = await seedLimitRequest(requests.api, 'S', body);
  assert.deepEqual(result.blocked, ['S limit request: 409 VOUCHER_LIMIT_REQUEST_PENDING_EXISTS']);
});
