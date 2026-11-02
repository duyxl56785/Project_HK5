import assert from 'node:assert/strict';

/** Test assertion only. Select the expected deployed contract; never guess a contract from a successful response. */
export function assertCheckoutRefusal(body, { contract, legacyMessage, item }) {
  assert.ok(['legacy145', 'itemized147'].includes(contract), 'Unknown acceptance error contract');
  if (contract === 'itemized147' && item) {
    assert.equal(body.code, 'CHECKOUT_ITEMS_REJECTED');
    assert.deepEqual(body.items, [item]);
    assert.equal(typeof body.message, 'string');
    assert.ok(body.message.length > 0);
    return;
  }
  assert.notEqual(body.code, 'CHECKOUT_ITEMS_REJECTED', 'Unexpected itemized refusal for this contract/case');
  assert.equal(typeof body.message, 'string');
  assert.ok(body.message.includes(legacyMessage), 'Expected the declared legacy/non-itemized error');
}
