import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createServer } from 'vite';

let vite, sellerApplicationSchema, TERMS_VERSION;
before(async () => {
  vite = await createServer({ server: { middlewareMode: true, hmr: false, watch: null }, optimizeDeps: { noDiscovery: true }, logLevel: 'silent' });
  ({ sellerApplicationSchema } = await vite.ssrLoadModule('/src/shared/forms.ts'));
  ({ TERMS_VERSION } = await vite.ssrLoadModule('/src/features/policy/terms.ts'));
});
after(() => vite?.close());

const valid = {
  businessType: 'INDIVIDUAL', representativeName: 'Nguyễn Văn An', idNumber: '001234567890', taxCode: '',
  phone: '0901234567', email: 'an@example.com', pickupAddress: '1 Lê Lợi', bankName: 'Ngân hàng A',
  bankAccountNumber: '0123456789', bankAccountHolder: 'NGUYEN VAN AN', acceptTerms: true,
};
const errorPaths = (value) => {
  const result = sellerApplicationSchema.safeParse(value);
  return result.success ? [] : result.error.issues.map((issue) => issue.path.join('.'));
};

test('a fake but well-formed seller application passes', () => {
  assert.deepEqual(errorPaths(valid), []);
  assert.deepEqual(errorPaths({ ...valid, businessType: 'COMPANY', taxCode: '0123456789-001' }), []);
});

test('each field is checked for format only', () => {
  for (const [field, bad] of [['idNumber', '12345678901'], ['idNumber', '00123456789a'], ['taxCode', '12345'],
    ['email', 'an@'], ['bankAccountNumber', '12345'], ['bankAccountNumber', '12345678901234567890'],
    ['bankName', ' '], ['bankAccountHolder', ''], ['phone', 'abc'], ['businessType', 'SHOP']]) {
    assert.deepEqual(errorPaths({ ...valid, [field]: bad }), [field], `${field}=${bad}`);
  }
});

test('the rules must be accepted and a business needs a tax code', () => {
  assert.deepEqual(errorPaths({ ...valid, acceptTerms: false }), ['acceptTerms']);
  assert.deepEqual(errorPaths({ ...valid, businessType: 'HOUSEHOLD' }), ['taxCode']);
});

test('the rules version matches the server', () => {
  assert.equal(TERMS_VERSION, '2026-11-11');
});
