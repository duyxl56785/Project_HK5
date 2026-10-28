import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createServer } from 'vite';

let server, model;
before(async () => {
  server = await createServer({ optimizeDeps: { noDiscovery: true, include: [] }, server: { middlewareMode: true, hmr: false, watch: null } });
  model = await server.ssrLoadModule('/src/features/finance/workspaceModel.ts');
});
after(async () => { await server?.close(); });

test('formats exact integers above the JavaScript safe integer boundary', () => {
  assert.equal(model.formatVnd('9007199254740993', 'VND'), '9.007.199.254.740.993 ₫');
});
test('preserves fractional snapshot digits without rounding or inventing policy', () => {
  assert.equal(model.formatVnd('1234567.0500', 'VND'), '1.234.567,0500 ₫');
});
for (const value of [1.5, '', '1e3', '01', ' 123', 'NaN', 'Infinity', '1.1234567']) {
  test(`rejects noncanonical money ${JSON.stringify(value)}`, () => assert.equal(model.formatVnd(value, 'VND'), 'Chưa có snapshot'));
}
test('currency is explicit and never silently defaults or converts', () => {
  assert.equal(model.formatVnd('100', undefined), 'Chưa có snapshot');
  assert.equal(model.formatVnd('100', 'USD'), 'Chưa có snapshot');
});
test('sum is exact across scales and never uses Number for money', () => {
  assert.equal(model.sumAmounts(['9007199254740993', '0.01', '1.020']), '9007199254740994.030');
  assert.equal(model.sumAmounts(['10', null]), null);
});
const tx = (id, storeId = 'A') => ({ id, orderId: `order-${id}`, storeId, store: `Store ${storeId}`, seller: `Seller ${storeId}`,
  amount: '100', currency: 'VND', delivery: 'DELIVERED', cod: 'PAID', verification: 'UNVERIFIED', paymentEvidence: null,
  settlement: null, commission: null, sellerNet: null, createdAt: '2026-10-27T10:00:00Z', updatedAt: '2026-10-27T11:00:00Z' });
test('Store scope strips foreign rows before summaries, filters or details', () => {
  const raw = Object.freeze([tx('one'), tx('secret', 'B')]);
  const scoped = model.scopeTransactions(raw, { kind: 'store', storeId: 'A' });
  assert.deepEqual(scoped.map(x => x.id), ['one']);
  assert.equal(model.financeSummary(scoped).total, '100');
  assert.equal(raw.length, 2);
});
test('missing/denied Store scope never falls back to admin data', () => {
  assert.deepEqual(model.scopeTransactions([tx('secret')], { kind: 'denied' }), []);
  assert.deepEqual(model.scopeTransactions([tx('secret')], { kind: 'store', storeId: '' }), []);
});
test('summary does not count PAID as verified without an evidence reference', () => {
  const rows = [tx('one'), { ...tx('two'), verification: 'VERIFIED', paymentEvidence: null },
    { ...tx('three'), verification: 'VERIFIED', paymentEvidence: 'fixture-evidence-3' }];
  assert.equal(model.financeSummary(rows).verifiedCod, '100');
  assert.equal(model.financeSummary(rows).unverifiedCod, '200');
  assert.equal(model.financeSummary(rows).settlements, 0);
});
test('filter combines literal search, Store, status and inclusive UTC dates', () => {
  const rows = [tx('one'), tx('two', 'B'), { ...tx('old'), createdAt: '2026-10-26T23:59:59Z' }];
  assert.deepEqual(model.filterTransactions(rows, { search: 'ORDER-ONE', storeId: 'A', status: 'PAID', from: '2026-10-27', to: '2026-10-27' }).map(x => x.id), ['one']);
  assert.deepEqual(model.filterTransactions(rows, { from: 'invalid' }), []);
  assert.deepEqual(model.filterTransactions(rows, { from: '2026-10-28', to: '2026-10-27' }), []);
});
test('pagination clamps stale page after a filter changes the dataset', () => {
  assert.deepEqual(model.paginate([1, 2, 3], 99, 2), { rows: [3], page: 2, pages: 2, total: 3 });
  assert.deepEqual(model.paginate([], 99, 2), { rows: [], page: 1, pages: 1, total: 0 });
});
test('QA totals belong to one selected run and never aggregate different heads', () => {
  const runs = [{ id: 'a', head: 'aaaaaaa', cases: [{ result: 'PASS', evidence: 'https://github.com/duyxl56785/Project_HK5/blob/a/report.json' }, { result: 'BLOCKED' }] },
    { id: 'b', head: 'bbbbbbb', cases: [{ result: 'FAIL' }] }];
  assert.deepEqual(model.qaCounts(runs[0].cases), { PASS: 1, FAIL: 0, SKIP: 0, NOT_RUN: 0, BLOCKED: 1, executed: 1 });
});
test('PASS without a safe receipt becomes NOT_RUN, never a green result', () => {
  assert.equal(model.qaResult({ result: 'PASS', evidence: null }), 'NOT_RUN');
  assert.equal(model.qaResult({ result: 'PASS', evidence: 'javascript:alert(1)' }), 'NOT_RUN');
});
for (const link of ['javascript:alert(1)', 'https://evil.test/token', 'https://github.com.evil.test/duyxl56785/Project_HK5', '//evil.test', 'https://user:password@github.com/duyxl56785/Project_HK5/pull/1']) {
  test(`refuses unsafe evidence link ${link.split(':')[0]}`, () => assert.equal(model.safeEvidenceUrl(link), null));
}
test('incomplete PR audit fails closed without usable READY subset', () => {
  assert.equal(model.readPrAudit({ complete: false, PRs: [{ number: 1 }] }).status, 'blocked');
});
test('imports the existing sanitized CLI schema without exposing extra raw fields', () => {
  const raw = { complete: true, errors: [], generatedAtUtc: '2026-10-27T00:00:00Z', PRs: [
    { number: 110, title: '<script>x</script>', author: 'owner', head: 'abcdef0', state: 'open', draft: false,
      url: 'https://github.com/duyxl56785/Project_HK5/pull/110', requestedReviewers: ['nguyenminhduy090-alt'],
      effectiveReviews: [{ author: 'reviewer', state: 'CHANGES_REQUESTED', head: 'aaaaaaa' }], ci: 'NO_CHECKS', mergeable: null,
      unresolvedThreads: [{ id: 'x' }], observedGateSignals: [], secret: 'PRIVATE_TOKEN' }] };
  const result = model.readPrAudit(raw);
  assert.equal(result.status, 'ready');
  assert.equal(result.rows[0].ci, 'NO_CHECKS');
  assert.equal(result.rows[0].mergeable, null);
  assert.equal(result.rows[0].unresolved, 1);
  assert.doesNotMatch(JSON.stringify(result), /PRIVATE_TOKEN/);
});
