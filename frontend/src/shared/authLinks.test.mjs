import assert from 'node:assert/strict';
import test from 'node:test';
import { authLink } from './authLinks.ts';

test('the header login link brings the shopper back to the page they were on', () => {
  assert.equal(authLink('/login', '/products/p1', ''), '/login?next=%2Fproducts%2Fp1');
  assert.equal(authLink('/register', '/products', '?q=ao&minPrice=1'), '/register?next=%2Fproducts%3Fq%3Dao%26minPrice%3D1');
});

test('the home page and the login pages themselves need no return path', () => {
  assert.equal(authLink('/login', '/', ''), '/login');
  assert.equal(authLink('/login', '/register', '?next=%2Fcart'), '/login?next=%2Fcart');
  assert.equal(authLink('/register', '/login', ''), '/register');
});
