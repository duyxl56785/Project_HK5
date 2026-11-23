import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createServer } from 'vite';
import { z } from 'zod';

let vite, toE164, splitE164, checkPhone, DEFAULT_PHONE_CODE;
before(async () => {
  vite = await createServer({ server: { middlewareMode: true, hmr: false, watch: null }, optimizeDeps: { noDiscovery: true }, logLevel: 'silent' });
  ({ toE164, splitE164, checkPhone, DEFAULT_PHONE_CODE } = await vite.ssrLoadModule('/src/shared/phone.ts'));
});
after(() => vite?.close());

test('a Vietnamese number is typed as at home and stored as E.164', () => {
  assert.equal(DEFAULT_PHONE_CODE, '+84');
  assert.equal(toE164('+84', '0901234567'), '+84901234567');
  assert.equal(toE164('+84', '901234567'), '+84901234567');
  assert.equal(toE164('+84', ' 090 123.45-67 '), '+84901234567');
  assert.equal(toE164('+65', '81234567'), '+6581234567');
});

test('a number typed with its own + keeps it, and an empty field stays empty', () => {
  assert.equal(toE164('+84', '+6581234567'), '+6581234567');
  assert.equal(toE164('+84', '   '), '');
});

test('a stored number is split back into the code and the national part', () => {
  assert.deepEqual(splitE164('+84901234567'), { code: '+84', local: '901234567' });
  assert.deepEqual(splitE164('+12025550123'), { code: '+1', local: '2025550123' });
  assert.deepEqual(splitE164(null), { code: '+84', local: '' });
  assert.deepEqual(splitE164('+4420712345'), { code: '+84', local: '+4420712345' });
});

test('a stored number with a code not in the list is saved back unchanged', () => {
  const stored = splitE164('+447911123456');
  assert.deepEqual(stored, { code: '+84', local: '+447911123456' });
  assert.equal(toE164(stored.code, stored.local), '+447911123456');
  assert.deepEqual(errors(true, stored.local, stored.code), []);
});

const schema = (required) => z.object({ phoneCode: z.string(), phone: z.string() }).superRefine(checkPhone(required));
const errors = (required, phone, phoneCode = '+84') => {
  const result = schema(required).safeParse({ phoneCode, phone });
  return result.success ? [] : result.error.issues.map((issue) => issue.path.join('.'));
};

test('the number is checked after the code is joined', () => {
  assert.deepEqual(errors(true, '0901234567'), []);
  assert.deepEqual(errors(false, ''), []);
  assert.deepEqual(errors(true, ''), ['phone']);
  for (const bad of ['abc', '09012x4567', '0123', '0901234567890123']) {
    assert.deepEqual(errors(true, bad), ['phone'], bad);
  }
});
