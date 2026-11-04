import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { Runner, countEvidence } from './runner.mjs';
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
test('TAP/JUnit có số kiểm thử và export escape dữ liệu', () => {
  assert.deepEqual(countEvidence('tap','# tests 2\n# pass 1\n# fail 1\n# skipped 0'),{tests:2,pass:1,fail:1,skip:0});
  assert.equal(countEvidence('tap','BUILD SUCCESSFUL'),null);
  const report={id:'a',sha:'abc',timestamp:'now',status:'FAIL',environment:{localhost:true},suites:[{id:'X',status:'FAIL',expected:'<script>',actual:'=danger'}]};
  assert.match(renderHtml(report),/&lt;script&gt;/);
  assert.match(renderCsv(report),/'=danger/);
});
