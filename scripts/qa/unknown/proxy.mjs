import http from 'node:http';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

const bytes = readFileSync(process.env.HK5_UNKNOWN_PLAN_FILE ?? '/fixture/plan.json');
const plan = JSON.parse(bytes);
// A prepared plan cannot start a listener. Reviewers must authorize a separate execution artifact.
if (plan.executionApproved !== true || process.env.HK5_UNKNOWN_APPROVED_PLAN_SHA256 !== sha256(bytes)) {
  throw new Error('UNKNOWN fixture execution has not been approved for this exact plan');
}
if (plan.sourceDirty !== false || plan.sourceRef !== 'main' || !/^[a-f0-9]{40}$/.test(plan.sourceSha ?? '')
    || !/^hk5-unknown-[0-9]{14}$/.test(plan.project ?? '') || plan.case !== 'lost-response') throw new Error('Clean main and exact isolated plan required');
if (sha256(readFileSync('/fixture/lost-response.mjs')) !== plan.helperSha256 || sha256(readFileSync('/fixture/proxy.mjs')) !== plan.proxySha256) throw new Error('Fixture source fingerprint changed');
const { lostResponseHandler } = await import('./lost-response.mjs');
http.createServer(lostResponseHandler({ target: 'http://127.0.0.1:8105/api/inventory/operations',
  operationId: plan.operationId, bodySha256: plan.bodySha256,
  onReceipt: receipt => console.log(JSON.stringify({ ...receipt, providerCommitVerified: false }))
})).listen(18105, '0.0.0.0');
