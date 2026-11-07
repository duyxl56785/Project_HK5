import http from 'node:http';
import { once } from 'node:events';
import test from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { lostResponseHandler, sha256 } from './lost-response.mjs';

const path = '/internal/inventory/operations';
const body = JSON.stringify({ operationId: 'fake-reserve-1', type: 'RESERVE', items: [{ sku: 'fake-sku', quantity: 1 }] });
async function server(t, handler) {
  const s = http.createServer(handler);
  s.listen(0, '127.0.0.1');
  await once(s, 'listening');
  t.after(() => new Promise(resolve => { s.closeAllConnections(); s.close(resolve); }));
  return `http://127.0.0.1:${s.address().port}${path}`;
}
function send(url, payload) {
  return new Promise((resolve, reject) => {
    const req = http.request(url, { method: 'POST' }, res => { res.resume(); res.on('end', () => resolve(res.statusCode)); });
    req.on('error', reject);
    req.end(payload);
  });
}

test('harness receives exact request once; proxy loses actual response without a synthetic error', async t => {
  let received = [], receipts = [];
  const target = await server(t, async (req, res) => {
    const chunks = []; for await (const c of req) chunks.push(c);
    received.push(Buffer.concat(chunks).toString());
    res.writeHead(200).end('{"harnessOnly":true}');
  });
  const proxy = await server(t, lostResponseHandler({ target, operationId: 'fake-reserve-1', bodySha256: sha256(body),
    onReceipt: receipt => receipts.push(receipt) }));
  await assert.rejects(send(proxy, body), { code: 'ECONNRESET' });
  assert.deepEqual(received, [body]);
  assert.equal(receipts.length, 1);
  assert.equal(receipts[0].upstreamStatus, 200);
  assert.equal(receipts[0].responseSha256, sha256('{"harnessOnly":true}'));
  assert.equal(receipts[0].responseSuppressed, true);
});

test('changed operation/payload never reaches harness', async t => {
  let calls = 0;
  const target = await server(t, (_req, res) => { calls++; res.end(); });
  const proxy = await server(t, lostResponseHandler({ target, operationId: 'fake-reserve-1', bodySha256: sha256(body), onReceipt() {} }));
  assert.equal(await send(proxy, body.replace('fake-reserve-1', 'new-key')), 400);
  assert.equal(await send(proxy, body.replace('quantity":1', 'quantity":2')), 400);
  assert.equal(calls, 0);
});

test('malformed or non-object JSON is rejected clearly without reaching harness', async t => {
  let calls = 0;
  const target = await server(t, (_req, res) => { calls++; res.end(); });
  const proxy = await server(t, lostResponseHandler({ target, operationId: 'fake-reserve-1', bodySha256: sha256(body), onReceipt() {} }));
  assert.equal(await send(proxy, '{'), 400);
  assert.equal(await send(proxy, 'null'), 400);
  assert.equal(calls, 0);
});

test('reject non-loopback targets and missing tuple evidence before creating handler', () => {
  for (const target of ['http://example.com/x', 'http://localhost/x', 'http://127.0.0.1@evil.invalid/x']) {
    assert.throws(() => lostResponseHandler({ target, operationId: 'fake-reserve-1', bodySha256: sha256(body), onReceipt() {} }));
  }
  assert.throws(() => lostResponseHandler({ target: 'http://127.0.0.1/x', operationId: 'fake-reserve-1', bodySha256: '', onReceipt() {} }));
});

test('sequential replay never forwards the approved tuple a second time', async t => {
  const received = [], receipts = [];
  const target = await server(t, async (req, res) => {
    const chunks = []; for await (const chunk of req) chunks.push(chunk);
    received.push(Buffer.concat(chunks).toString());
    res.end('actual transport response');
  });
  const proxy = await server(t, lostResponseHandler({ target, operationId: 'fake-reserve-1', bodySha256: sha256(body),
    onReceipt: receipt => receipts.push(receipt) }));
  for (let i = 0; i < 3; i++) await assert.rejects(send(proxy, body), { code: 'ECONNRESET' });
  assert.deepEqual(received, [body]);
  assert.equal(receipts.length, 1);
});

test('concurrent duplicate bodies share one atomic admission while upstream is pending', async t => {
  const received = [], receipts = [];
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  t.after(() => release());
  const target = await server(t, async (req, res) => {
    const chunks = []; for await (const chunk of req) chunks.push(chunk);
    received.push(Buffer.concat(chunks).toString());
    await pending;
    res.end('actual transport response');
  });
  const handler = lostResponseHandler({ target, operationId: 'fake-reserve-1', bodySha256: sha256(body),
    onReceipt: receipt => receipts.push(receipt) });
  let readBodies = 0;
  const proxy = await server(t, (req, res) => {
    req.once('end', () => { if (++readBodies === 8) release(); });
    return handler(req, res);
  });
  const results = await Promise.allSettled(Array.from({ length: 8 }, () => send(proxy, body)));
  for (const result of results) {
    assert.equal(result.status, 'rejected');
    assert.equal(result.reason.code, 'ECONNRESET');
  }
  assert.deepEqual(received, [body]);
  assert.equal(receipts.length, 1);
});

for (const mode of ['sync', 'async']) {
  test(`${mode} receipt failure is reported without crashing the process or reopening admission`, async () => {
    const script = `
      import http from 'node:http';
      import { once } from 'node:events';
      import assert from 'node:assert/strict';
      import { lostResponseHandler, sha256 } from ${JSON.stringify(new URL('./lost-response.mjs', import.meta.url).href)};
      const body = ${JSON.stringify(body)};
      let calls = 0;
      const upstream = http.createServer((req, res) => { calls++; req.resume(); res.end('real response'); });
      upstream.listen(0, '127.0.0.1'); await once(upstream, 'listening');
      const proxy = http.createServer(lostResponseHandler({
        target: 'http://127.0.0.1:' + upstream.address().port + ${JSON.stringify(path)},
        operationId: 'fake-reserve-1', bodySha256: sha256(body),
        onReceipt: ${mode === 'async' ? 'async' : ''} () => { throw new Error('sensitive-callback-details'); }
      }));
      proxy.listen(0, '127.0.0.1'); await once(proxy, 'listening');
      const send = () => new Promise((resolve, reject) => {
        const req = http.request({ hostname: '127.0.0.1', port: proxy.address().port, path: ${JSON.stringify(path)},
          method: 'POST' }, res => { res.resume(); res.on('end', () => resolve(res.statusCode)); });
        req.on('error', reject); req.end(body);
      });
      await assert.rejects(send(), { code: 'ECONNRESET' });
      await assert.rejects(send(), { code: 'ECONNRESET' });
      assert.equal(calls, 1);
      proxy.closeAllConnections(); upstream.closeAllConnections();
      await Promise.all([new Promise(resolve => proxy.close(resolve)), new Promise(resolve => upstream.close(resolve))]);
      console.log('CALLBACK_FAILURE_SURVIVED');
    `;
    const { stdout, stderr } = await promisify(execFile)(process.execPath, ['--input-type=module', '-e', script], { timeout: 5000 });
    assert.match(stdout, /CALLBACK_FAILURE_SURVIVED/);
    assert.match(stderr, /UNKNOWN_PROXY_EVIDENCE_MISSING/);
    assert.doesNotMatch(stderr, /sensitive-callback-details/);
  });
}

test('timeout records no receipt and never permits a second upstream attempt', async t => {
  const received = [], receipts = [];
  const target = await server(t, async req => {
    const chunks = []; for await (const chunk of req) chunks.push(chunk);
    received.push(Buffer.concat(chunks).toString());
  });
  const original = http.ClientRequest.prototype.setTimeout;
  t.mock.method(http.ClientRequest.prototype, 'setTimeout', function (ms, callback) {
    assert.equal(ms, 30000);
    return original.call(this, 100, callback);
  });
  const proxy = await server(t, lostResponseHandler({ target, operationId: 'fake-reserve-1', bodySha256: sha256(body),
    onReceipt: receipt => receipts.push(receipt) }));
  await assert.rejects(send(proxy, body), { code: 'ECONNRESET' });
  await assert.rejects(send(proxy, body), { code: 'ECONNRESET' });
  assert.deepEqual(received, [body]);
  assert.deepEqual(receipts, []);
});

test('malformed JSON and wrong fingerprints do not consume the one valid admission', async t => {
  let calls = 0;
  const target = await server(t, (req, res) => { calls++; req.resume(); res.end(); });
  const proxy = await server(t, lostResponseHandler({ target, operationId: 'fake-reserve-1', bodySha256: sha256(body), onReceipt() {} }));
  for (const invalid of ['{', 'null', '[]', JSON.stringify(JSON.parse(body), null, 2),
    body.replace('quantity":1', 'quantity":2'), body.replace('fake-reserve-1', 'wrong-id')]) {
    assert.equal(await send(proxy, invalid), 400);
  }
  assert.equal(calls, 0);
  await assert.rejects(send(proxy, body), { code: 'ECONNRESET' });
  await assert.rejects(send(proxy, body), { code: 'ECONNRESET' });
  assert.equal(calls, 1);
});

test('operation ID must match even when the request fingerprint matches', async t => {
  let calls = 0;
  const target = await server(t, (req, res) => { calls++; req.resume(); res.end(); });
  const wrongBody = body.replace('fake-reserve-1', 'wrong-id');
  const proxy = await server(t, lostResponseHandler({ target, operationId: 'fake-reserve-1',
    bodySha256: sha256(wrongBody), onReceipt() {} }));
  assert.equal(await send(proxy, wrongBody), 400);
  assert.equal(calls, 0);
});

test('incomplete upstream response is not a receipt and does not reopen admission', async t => {
  let calls = 0;
  const receipts = [];
  const target = await server(t, (req, res) => {
    calls++; req.resume(); res.writeHead(200); res.write('partial');
    setImmediate(() => res.destroy());
  });
  const proxy = await server(t, lostResponseHandler({ target, operationId: 'fake-reserve-1', bodySha256: sha256(body),
    onReceipt: receipt => receipts.push(receipt) }));
  await assert.rejects(send(proxy, body), { code: 'ECONNRESET' });
  await assert.rejects(send(proxy, body), { code: 'ECONNRESET' });
  assert.equal(calls, 1);
  assert.deepEqual(receipts, []);
});
