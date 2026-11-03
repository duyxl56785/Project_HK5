import http from 'node:http';
import { once } from 'node:events';
import test from 'node:test';
import assert from 'node:assert/strict';
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

test('reject non-loopback targets and missing tuple evidence before creating handler', () => {
  for (const target of ['http://example.com/x', 'http://localhost/x', 'http://127.0.0.1@evil.invalid/x']) {
    assert.throws(() => lostResponseHandler({ target, operationId: 'fake-reserve-1', bodySha256: sha256(body), onReceipt() {} }));
  }
  assert.throws(() => lostResponseHandler({ target: 'http://127.0.0.1/x', operationId: 'fake-reserve-1', bodySha256: '', onReceipt() {} }));
});
