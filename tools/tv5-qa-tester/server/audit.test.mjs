import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,mkdir,readFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {Runner} from './runner.mjs';
import {createServer} from './http.mjs';
import {diagnose,redact} from './diagnostics.mjs';
const root=()=>mkdtemp(path.join(tmpdir(),'soopi-audit-'));
const finish=async(r,id)=>{for(let n=0;n<100&&(r.runs.get(id).status==='RUNNING'||r.active?.id===id);n++)await new Promise(x=>setTimeout(x,10));return r.runs.get(id);};
test('hai POST đồng thời chỉ nhận một run',async()=>{
 const r=new Runner({root:await root(),exec:async()=>({code:0,output:'',timedOut:false})});
 const results=await Promise.allSettled([r.start(['FINANCE-PRODUCTION']),r.start(['UNKNOWN'])]);
 assert.equal(results.filter(x=>x.status==='fulfilled').length,1);
 for(const x of results)if(x.status==='fulfilled')await finish(r,x.value.id);
});
test('reload server không giữ RUNNING mồ côi và không sửa raw history',async()=>{
 const dir=await root();const id='orphan';await mkdir(path.join(dir,id));
 const text=JSON.stringify({id,status:'RUNNING',suites:[{id:'F15-RULES',status:'RUNNING'}]});
 await writeFile(path.join(dir,id,'report.json'),text);const r=new Runner({root:dir});await r.hydrate();
 assert.equal(r.runs.get(id).status,'INTERRUPTED');assert.equal(r.runs.get(id).suites[0].status,'INTERRUPTED');
 assert.equal(await readFile(path.join(dir,id,'report.json'),'utf8'),text);
});
test('toàn bộ skipped không được gắn PASS',async()=>{
 const r=new Runner({root:await root(),exec:async()=>({code:0,output:'2 skipped (1s)',timedOut:false})});
 const p=await r.start(['QA-UI']);const out=await finish(r,p.id);assert.equal(out.suites[0].status,'SKIP');
});
test('exec exception được lưu lỗi và giải phóng run thay vì treo',async()=>{
 const r=new Runner({root:await root(),exec:async()=>{throw new Error('EACCES process');}});
 const p=await r.start(['F15-RULES']);const out=await finish(r,p.id);
 assert.equal(out.status,'FAIL');assert.ok(out.suites[0].bugs?.length);assert.equal(r.active,null);
});
test('download raw log và report.html trả đúng file',async()=>{
 const dir=await root();await mkdir(path.join(dir,'run'));await writeFile(path.join(dir,'run','F15-RULES.log'),'raw');await writeFile(path.join(dir,'run','report.html'),'<p>report</p>');
 const r=new Runner({root:dir});r.runs.set('run',{id:'run'});const server=createServer({runner:r,port:4319});
 await new Promise(resolve=>server.listen(4319,'127.0.0.1',resolve));
 try{for(const file of ['F15-RULES.log','report.html']){const response=await fetch('http://127.0.0.1:4319/api/evidence/run/'+file);assert.equal(response.status,200);}}finally{await new Promise(resolve=>server.close(resolve));}
});
test('chẩn đoán ECONNREFUSED không bịa root cause và không lộ credential',()=>{
 const row={id:'WEBSITE-SMOKE',status:'FAIL',expected:'HTTP200',actual:'No response',endpoint:'/api/stores'};
 const [bug]=diagnose(row,{module:'Website',source:'test'}, {id:'run',sha:'abc',environment:{localhost:true}},'ECONNREFUSED\nError token=local-sensitive-value\nBearer abcdef0123456789');
 assert.equal(bug.type,'ENVIRONMENT_FAILURE');assert.equal(bug.endpoint,'/api/stores');assert.match(bug.verifiedCause,/chưa xác minh/);assert.ok(!bug.stackTrace.includes('local-sensitive-value'));
 assert.ok(!redact('a@personal.example Bearer abcdef eyJhbGciOi.xxxx.yyyy').includes('personal.example'));
});
