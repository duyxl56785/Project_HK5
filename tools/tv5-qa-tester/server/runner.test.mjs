import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { EventEmitter } from 'node:events';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { Runner, countEvidence, terminateProcessTree } from './runner.mjs';
import { renderHtml, renderCsv } from './report.mjs';

test('allowlist từ chối executable bất kỳ và suite đang bị khóa', async () => {
  const runner = new Runner({root:await mkdtemp(path.join(tmpdir(),'soopi-qa-'))});
  await assert.rejects(runner.start(['cmd /c del']), /allowlist/);
  const run = await runner.start(['FINANCE-PRODUCTION']);
  await new Promise(resolve=>setTimeout(resolve,100));
  assert.equal(runner.runs.get(run.id).suites[0].status,'BLOCKED');
});
test('timeout, FAIL và thiếu test evidence không thể PASS', async () => {
  for (const [mock,expected] of [[{code:0,output:'',timedOut:true},'TIMEOUT'],[{code:1,output:'error',timedOut:false},'FAIL'],[{code:0,output:'',timedOut:false},'NOT_RUN']]) {
    const root=await mkdtemp(path.join(tmpdir(),'soopi-qa-'));
    const runner=new Runner({root,exec:async()=>mock});
    const run=await runner.start(['F15-RULES']);
    while(runner.runs.get(run.id).status==='RUNNING') await new Promise(resolve=>setTimeout(resolve,20));
    assert.equal(runner.runs.get(run.id).suites[0].status,expected);
    assert.match(await readFile(path.join(root,run.id,'SHA256SUMS.txt'),'utf8'),/report\.json/);
  }
});
test('Docker không hoạt động thì PostgreSQL BLOCKED_ENV và command không chạy', async () => {
  let invoked=false;
  const runner=new Runner({root:await mkdtemp(path.join(tmpdir(),'soopi-qa-')),docker:async()=>false,exec:async()=>{invoked=true;return {code:0,output:''};}});
  const run=await runner.start(['F15-POSTGRES']);
  while(runner.runs.get(run.id).status==='RUNNING') await new Promise(resolve=>setTimeout(resolve,20));
  assert.equal(runner.runs.get(run.id).suites[0].status,'BLOCKED_ENV');
  assert.equal(invoked,false);
});
test('Playwright không có screenshot thì không PASS dù process báo passed', async () => {
  const runner=new Runner({root:await mkdtemp(path.join(tmpdir(),'soopi-qa-')),exec:async()=>({code:0,output:'2 passed (1s)',timedOut:false})});
  const run=await runner.start(['QA-UI']);
  while(runner.runs.get(run.id).status==='RUNNING') await new Promise(resolve=>setTimeout(resolve,20));
  assert.equal(runner.runs.get(run.id).suites[0].status,'FAIL');
});
test('hai Playwright suite trong một run không đếm ảnh của nhau', async () => {
  const root=await mkdtemp(path.join(tmpdir(),'soopi-qa-'));
  const runner=new Runner({root,exec:async(_exe,_args,options)=>{
    const dir=options.envExtra.SOOPI_QA_SCREENSHOT_DIR;
    await mkdir(dir,{recursive:true});
    if(dir.endsWith('QA-UI-DIAGNOSTIC')) { await writeFile(path.join(dir,'fail-failed.png'),'PNG'); return {code:1,output:'1 failed (1s)',timedOut:false}; }
    await writeFile(path.join(dir,'a-passed.png'),'PNG'); await writeFile(path.join(dir,'b-passed.png'),'PNG');
    return {code:0,output:'2 passed (1s)',timedOut:false};
  }});
  const run=await runner.start(['QA-UI','QA-UI-DIAGNOSTIC']);
  while(runner.runs.get(run.id).status==='RUNNING') await new Promise(resolve=>setTimeout(resolve,20));
  const [pass,fail]=runner.runs.get(run.id).suites;
  assert.equal(pass.status,'PASS'); assert.equal(pass.screenshots.length,2);
  assert.equal(fail.status,'FAIL'); assert.equal(fail.screenshots.length,1);
});
test('TAP/JUnit có số kiểm thử và export escape dữ liệu', () => {
  assert.deepEqual(countEvidence('tap','# tests 2\n# pass 1\n# fail 1\n# skipped 0'),{tests:2,pass:1,fail:1,skip:0});
  assert.equal(countEvidence('tap','BUILD SUCCESSFUL'),null);
  const report={id:'a',sha:'abc',timestamp:'now',status:'FAIL',environment:{localhost:true},suites:[{id:'X',status:'FAIL',expected:'<script>',actual:'=danger'}]};
  assert.match(renderHtml(report),/&lt;script&gt;/);
  assert.match(renderCsv(report),/'=danger/);
});
test('Windows cancel targets only spawned child tree, with fallback on taskkill failure', () => {
  let calls = 0, killed = 0;
  const child = { pid: 43210, exitCode: null, signalCode: null, kill: () => { killed++; } };
  const killer = new EventEmitter();
  const spawnProcess = (exe, args, opts) => {
    calls++;
    assert.equal(exe, 'taskkill');
    assert.deepEqual(args, ['/PID', '43210', '/T', '/F']);
    assert.equal(opts.shell, false);
    return killer;
  };
  assert.equal(terminateProcessTree(child, { platform: 'win32', spawnProcess }), true);
  assert.equal(calls, 1);
  assert.equal(killed, 0);
  killer.emit('close', 1);
  assert.equal(killed, 1);
  child.exitCode = 1;
  assert.equal(terminateProcessTree(child, { platform: 'win32', spawnProcess }), false);
  assert.equal(calls, 1);
});
test('Windows cancel actually stops a private child and grandchild', { skip: process.platform !== 'win32', timeout: 15000 }, async () => {
  const script = "const {spawn}=require('node:child_process');const c=spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{stdio:'ignore'});console.log(c.pid);setInterval(()=>{},1000)";
  const parent = spawn(process.execPath, ['-e', script], { stdio: ['ignore', 'pipe', 'ignore'], windowsHide: true });
  let grandchildPid;
  try {
    const [chunk] = await Promise.race([once(parent.stdout, 'data'), new Promise((_, reject) => setTimeout(() => reject(new Error('child did not start')), 5000))]);
    grandchildPid = Number(chunk.toString().trim());
    assert.ok(Number.isSafeInteger(grandchildPid) && grandchildPid > 0);
    process.kill(grandchildPid, 0);
    terminateProcessTree(parent);
    await Promise.race([once(parent, 'close'), new Promise((_, reject) => setTimeout(() => reject(new Error('parent survived cancel')), 5000))]);
    let alive = true;
    for (let i = 0; i < 20; i++) {
      try { process.kill(grandchildPid, 0); await new Promise(resolve => setTimeout(resolve, 100)); }
      catch { alive = false; break; }
    }
    assert.equal(alive, false, 'grandchild survived cancel');
  } finally {
    if (parent.exitCode === null && parent.signalCode === null) terminateProcessTree(parent);
    if (grandchildPid) { try { process.kill(grandchildPid); } catch {} }
  }
});
