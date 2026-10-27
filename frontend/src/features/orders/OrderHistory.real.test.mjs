/** Requires the real #107 HTTP/PostgreSQL harness; missing configuration fails rather than skips. */
import assert from 'node:assert/strict';
import { before, after, afterEach, test } from 'node:test';
import { pathToFileURL } from 'node:url';
import { act, createElement } from 'react';
import { createServer } from 'vite';
import { createOrderHistoryClient } from './orderHistoryClient.ts';

const base=process.env.TV3_HISTORY_URL, tokenA=process.env.TV3_HISTORY_TOKEN_A, tokenB=process.env.TV3_HISTORY_TOKEN_B;
const ownId=process.env.TV3_HISTORY_ORDER_A, foreignId=process.env.TV3_HISTORY_ORDER_B, bigId=process.env.TV3_HISTORY_BIG_ID;
if(!base||!tokenA||!tokenB||!ownId||!foreignId||!bigId) throw Error('Real HTTP/PostgreSQL harness configuration required');
const nativeFetch=globalThis.fetch;
const transport=token=>(path,init)=>nativeFetch(new URL(path,base),{...init,headers:{...init.headers,Authorization:`Bearer ${token}`}});
const a=transport(tokenA),b=transport(tokenB),clientA=createOrderHistoryClient(a),clientB=createOrderHistoryClient(b);
let window,server,OrderHistory,createRoot,root,container;
before(async()=>{
  const {Window}=await import(pathToFileURL(process.env.TV3_DOM_MODULE).href);window=new Window();
  Object.assign(globalThis,{window,document:window.document,HTMLElement:window.HTMLElement,Event:window.Event,MouseEvent:window.MouseEvent,IS_REACT_ACT_ENVIRONMENT:true});
  Object.defineProperty(globalThis,'navigator',{configurable:true,value:window.navigator});
  ({createRoot}=await import('react-dom/client'));
  server=await createServer({server:{middlewareMode:true,hmr:false,watch:null}});
  ({OrderHistory}=await server.ssrLoadModule('/src/features/orders/OrderHistory.tsx'));
});
afterEach(async()=>{if(root){await act(()=>root.unmount());root=null;container.remove();}});
after(async()=>{await server?.close();window?.happyDOM.abort();});
async function mount(scope,fetcher=a){
  if(!root){container=document.createElement('div');document.body.append(container);root=createRoot(container);}
  await act(()=>root.render(createElement(OrderHistory,{sessionScope:scope,transport:fetcher})));
}
async function until(predicate){
  for(let i=0;i<100;i++){if(predicate())return;await act(async()=>{await new Promise(r=>setTimeout(r,30));});}
  assert.ok(predicate(),'Expected UI state after real HTTP request');
}
function button(label){return [...container.querySelectorAll('button')].find(b=>b.textContent===label);}
async function click(element){assert.ok(element);await act(()=>element.click());}

test('real API list is scoped and pages remain distinct',async()=>{
  const first=await clientA.list(0,20),next=await clientA.list(1,20),other=await clientB.list();
  assert.equal(first.totalElements,22);assert.equal(first.content.length,20);assert.equal(next.content.length,2);
  assert.equal(new Set([...first.content,...next.content].map(o=>o.orderId)).size,22);
  assert.equal(other.content.length,1);assert.equal(other.content[0].orderId,foreignId);
  assert.ok(!first.content.some(o=>o.orderId===foreignId));
});
test('real detail contains two Stores and exact persisted item snapshots',async()=>{
  const detail=await clientA.detail(ownId);assert.equal(detail.sellerOrders.length,2);assert.equal(detail.total,'60.00');
  assert.deepEqual(new Set(detail.sellerOrders.map(s=>s.storeId)),new Set(['store-A','store-B']));
  assert.equal(detail.sellerOrders.flatMap(s=>s.items).length,4);
  assert.equal(JSON.stringify(detail).includes('idempotency'),false);
});
test('real foreign/missing detail and invalid JWT are refused',async()=>{
  await assert.rejects(clientB.detail(ownId),e=>e.kind==='not-found');
  await assert.rejects(clientA.detail('00000000-0000-4000-8000-000000000000'),e=>e.kind==='not-found');
  await assert.rejects(createOrderHistoryClient(transport('invalid.jwt.value')).list(),e=>e.kind==='unauthorized');
});
test('real maximum NUMERIC snapshot is never converted to Number',async()=>{
  const detail=await clientA.detail(bigId);assert.equal(detail.total,'99999999999999999.99');
  assert.equal(detail.sellerOrders[0].items[0].unitPriceSnapshot,detail.total);
});
test('UI loads actual HTTP list, navigates two-Store detail and next page',async()=>{
  await mount('session-A');await until(()=>container.querySelectorAll('tbody tr').length===20);
  const view=container.querySelector(`button[aria-label="Xem đơn ${ownId}"]`);await click(view);
  await until(()=>container.querySelectorAll('article section').length===2);
  assert.match(container.textContent,/product-A-0/);assert.match(container.textContent,/60.00/);
  assert.doesNotMatch(container.textContent,/COD_PAID|Đã thanh toán|VND/);
  await click(button('Về danh sách'));await until(()=>container.querySelectorAll('tbody tr').length===20);
  await click(button('Trang sau'));await until(()=>container.querySelectorAll('tbody tr').length===2);
  assert.ok(button('Trang sau').disabled);
});
test('session loss removes private rows synchronously and performs no new request',async()=>{
  await mount('session-A');await until(()=>container.textContent.includes(ownId));
  let calls=0;await mount(null,()=>{calls++;throw Error('must not request');});
  assert.ok(!container.textContent.includes(ownId));assert.match(container.textContent,/cần đăng nhập/);assert.equal(calls,0);
});
test('real unauthorized response clears history and shows no server diagnostic',async()=>{
  await mount('invalid-session',transport('invalid.jwt.value'));
  await until(()=>container.textContent.includes('cần đăng nhập'));
  assert.ok(!container.querySelector('table'));assert.ok(!container.textContent.includes(ownId));
});
test('late real HTTP response from old session cannot replace current Customer history',async()=>{
  let release,started;const gate=new Promise(r=>release=r),begun=new Promise(r=>started=r);
  const held=async(path,init)=>{
    const {signal,...withoutAbort}=init;void signal;
    const response=await a(path,withoutAbort);started();await gate;return response;
  };
  await mount('old-A',held);await begun;
  await mount('new-B',b);await until(()=>container.textContent.includes(foreignId));
  await act(async()=>{release();await new Promise(r=>setTimeout(r,60));});
  assert.ok(container.textContent.includes(foreignId));assert.ok(!container.textContent.includes(ownId));
});
