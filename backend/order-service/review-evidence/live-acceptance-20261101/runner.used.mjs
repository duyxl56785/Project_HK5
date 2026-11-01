import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {resolve} from 'node:path';
const root='/workspace/tv3-pr145-live-acceptance';
const evidence='/tmp/tv3-live-acceptance-145-20261101';
const mode=process.argv[2];
if(!['prepare','two-store','release','consume'].includes(mode))throw new Error('Unknown case');
const local=Object.fromEntries(readFileSync(root+'/.env.demo','utf8').split('\n').filter(s=>s.includes('=')&&!s.startsWith('#')).map(s=>[s.slice(0,s.indexOf('=')),s.slice(s.indexOf('=')+1).trim()]));
const base='http://localhost:5173';
const headers={Origin:base,'X-CSRF-Protection':'1','Content-Type':'application/json'};
async function api(path,options={}){
  const r=await fetch(base+path,{...options,headers:{...headers,...options.headers},signal:AbortSignal.timeout(15000)});
  if(!r.ok)throw new Error(`Fixture verification ${path} returned ${r.status}`);
  return r.json();
}
if(mode==='prepare'){
  const token=(await api('/api/auth/login',{method:'POST',body:JSON.stringify({email:'customer@hk5.local',password:local.SEED_PASSWORD})})).accessToken;
  const customer=await api('/api/auth/me',{headers:{Authorization:`Bearer ${token}`}});
  if(!customer.roles.includes('CUSTOMER'))throw new Error('Customer role absent');
  const sellerToken=(await api('/api/auth/login',{method:'POST',body:JSON.stringify({email:'seller2@hk5.local',password:local.SEED_PASSWORD})})).accessToken;
  const seller=await api('/api/auth/me',{headers:{Authorization:`Bearer ${sellerToken}`}});
  if(!seller.roles.includes('SELLER'))throw new Error('Seller role absent');
  const store=await api('/api/stores/me',{headers:{Authorization:`Bearer ${sellerToken}`}});
  const catalog=await api('/api/catalog/products?size=50');
  const first=catalog.items.find(p=>p.store.name==='Tiệm Áo Sài Gòn');
  const second=catalog.items.find(p=>p.store.id===store.id);
  if(!first||!second||first.store.id===second.store.id)throw new Error('Two distinct approved seed Stores required');
  const lines=[];
  for(const item of [first,second]){
    const p=await api('/api/catalog/products/'+item.id);
    if(!p.sku||p.sku===p.id||p.available<6)throw new Error('Authoritative Product/SKU/stock fixture unavailable');
    lines.push({productId:p.id,sku:p.sku,storeId:p.store.id,quantity:2,available:p.available});
  }
  const cart=await api('/api/cart',{headers:{Authorization:`Bearer ${token}`}});
  if(cart.claimed||cart.items.length)throw new Error('Dedicated seed Customer Cart must start empty');
  writeFileSync(evidence+'/fixtures.public.json',JSON.stringify({sourceHead:'bf8eb3a027c72ad9fde81d0aa5dabc4e73bebacf',customerId:customer.userId,sellerBStoreId:store.id,customerRoleVerified:true,sellerRoleVerified:true,lines},null,2)+'\n');
  writeFileSync(evidence+'/fault-prerequisites.json',JSON.stringify({claimConflict:{state:'NOT_RUN',executed:0,reason:'No TV4/TV5-approved real fault fixture, original operation identity, active fault receipt and evidence SHA256 supplied'},timeout:{state:'NOT_RUN',executed:0,reason:'No TV4/TV5-approved timeout/lost-response fixture, operation identity, active fault receipt and evidence SHA256 supplied'}},null,2)+'\n');
  console.log('Real Auth roles, two seed Stores/Product-SKU identities and empty Customer Cart verified; credentials not exported.');
  process.exit(0);
}
const fixture=JSON.parse(readFileSync(evidence+'/fixtures.public.json','utf8'));
const run=process.env.TV3_RUN_LABEL??'original';
if(!/^[a-z0-9-]+$/.test(run))throw new Error('Unsafe run label');
const dir=resolve(evidence,'cases',run,mode);mkdirSync(dir,{recursive:true,mode:0o700});
const env={...process.env,TV3_E2E_BASE_URL:base,TV3_E2E_CUSTOMER_EMAIL:'customer@hk5.local',TV3_E2E_CUSTOMER_PASSWORD:local.SEED_PASSWORD,TV3_E2E_SELLER_B_EMAIL:'seller2@hk5.local',TV3_E2E_SELLER_B_PASSWORD:local.SEED_PASSWORD,TV3_E2E_PRODUCTS:JSON.stringify(fixture.lines.map(({productId,quantity})=>({productId,quantity}))),TV3_E2E_PG_CONTAINER:'tv3-live-145-20261101-postgres-1',TV3_E2E_PG_USER:'hk5',TV3_E2E_ORDER_DB:'order_db',TV3_E2E_INVENTORY_DB:'inventory_db',TV3_TV4_CONTRACT_REFERENCE:'docs/TV4_FULFILLMENT_IMPLEMENTATION_CONTRACT.md dispatch boundary; merged PR135 FulfillmentService DISPATCHED; user live-demo acceptance instruction',TV3_CASE_DIR:dir};
for(const k of ['DOCKER_HOST','DOCKER_CONTEXT','DOCKER_TLS','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH'])delete env[k];
function snapshot(phase){const r=spawnSync('python',[evidence+'/snapshot.py',dir+'/'+phase],{env,encoding:'utf8'});if(r.status!==0)throw new Error('Read-only snapshot failed before/after case');}
function sanitize(value){
 let s=String(value);
 for(const v of Object.values(local))if(v&&v.length>=8&&![base,'http://auth-service.hk5.local','smart-ecommerce-api'].includes(v))s=s.replaceAll(v,'[REDACTED_LOCAL_VALUE]');
 s=s.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[REDACTED_JWT]');
 return s;
}
snapshot('before');
const args=[evidence+'/node-runtime/node_modules/@playwright/test/cli.js','test','--config',evidence+'/runtime.config.ts','--project',mode==='two-store'?'two-store':'tv4-lifecycle'];
if(mode==='release')args.push('--grep','customer cancellation');
if(mode==='consume')args.push('--grep','CONSUME follows');
const result=spawnSync(process.execPath,args,{cwd:root+'/frontend',env,encoding:'utf8',maxBuffer:16*1024*1024});
writeFileSync(dir+'/console.private.log',result.stdout+result.stderr,{mode:0o600});
writeFileSync(dir+'/console.log',sanitize(result.stdout+result.stderr));
for(const name of ['results.private.json','junit.private.xml']){
 const content=readFileSync(dir+'/'+name,'utf8');writeFileSync(dir+'/'+name.replace('.private',''),sanitize(content));
}
snapshot('after');
writeFileSync(dir+'/command.json',JSON.stringify({cwd:root+'/frontend',command:['node',...args],exitCode:result.status,signal:result.signal,applicationHead:fixture.sourceHead,runLabel:run,case:mode,browser:'/usr/bin/chromium',trace:'off',mockedNetwork:false},null,2)+'\n');
console.log(sanitize(result.stdout+result.stderr));
process.exit(result.status??1);
