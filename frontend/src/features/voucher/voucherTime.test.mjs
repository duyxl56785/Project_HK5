import assert from 'node:assert/strict';
import test from 'node:test';
import { toIsoVN, toVNInput } from './voucherTime.ts';

test('Voucher civil time always uses Vietnam UTC+07 irrespective of browser timezone',()=>{
  assert.equal(toIsoVN('2026-11-14T22:00'),'2026-11-14T15:00:00.000Z');
  assert.equal(toIsoVN('2026-11-15T00:00'),'2026-11-14T17:00:00.000Z');
  assert.equal(toVNInput('2026-11-14T15:00:00Z'),'2026-11-14T22:00');
});
test('reject malformed and normalized-invalid dates instead of silently changing promotion schedule',()=>{
  for(const value of ['', '2026-11-14', '2026-02-30T10:00','2026-11-14T25:00','2026-00-02T09:00',
    '2026-11-14T22:00Z', '2026-11-14T22:00+07:00', '2026-11-14T22:00:00']){
    assert.throws(()=>toIsoVN(value), undefined, value);
  }
});
test('invalid server time cannot become an editable date',()=>{
  assert.equal(toVNInput('invalid'),'');
});
