import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createOrderHistoryClient } from './orderHistoryClient.ts';
const id = '10000000-0000-4000-8000-000000000001';
const summary = { orderId:id, status:'PLACED', total:'99999999999999999.99', createdAt:'2026-10-01T00:00:00Z', sellerOrderCount:2 };
const page = { content:[summary], page:0, size:20, totalElements:1, totalPages:1 };
const from = body => createOrderHistoryClient(async () => new Response(JSON.stringify(body), { status:200 }));
test('retains exact decimals and strips unknown private fields', async () => {
  const result = await from({...page, content:[{...summary, idempotencyKey:'PRIVATE'}]}).list();
  assert.equal(result.content[0].total, summary.total);
  assert.equal(JSON.stringify(result).includes('PRIVATE'), false);
});
test('rejects malformed totals, pages, status, duplicates and numeric money', async () => {
  for (const invalid of [{...page,totalPages:7}, {...page,size:1}, {...page,content:[summary,summary]},
    {...page,content:[{...summary,total:60}]}, {...page,content:[{...summary,status:'COD_PAID'}]}]) {
    await assert.rejects(from(invalid).list(), error => error.kind === 'unavailable');
  }
});
test('invalid IDs and pagination cannot generate requests', async () => {
  let calls=0;const client=createOrderHistoryClient(async()=>{calls++;throw Error('must not call');});
  await assert.rejects(client.detail('../internal/secret'));
  await assert.rejects(client.list(-1));await assert.rejects(client.list(0,101));assert.equal(calls,0);
});
test('maps access failures without exposing backend diagnostics', async () => {
  for(const status of [401,403,404,500]) {
    const client=createOrderHistoryClient(async()=>new Response('PRIVATE_DIAGNOSTIC',{status}));
    await assert.rejects(client.detail(id),error=>error.kind===(status===401||status===403?'unauthorized':status===404?'not-found':'unavailable')&&!error.message.includes('PRIVATE'));
  }
  await assert.rejects(createOrderHistoryClient(async()=>new Response('missing route',{status:404})).list(),error=>error.kind==='unavailable');
});
