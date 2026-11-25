import assert from 'node:assert/strict';
import test from 'node:test';
import { authLink, safeNext } from './authLinks.ts';

test('the header login link brings the shopper back to the page they were on', () => {
  assert.equal(authLink('/login', '/products/p1', ''), '/login?next=%2Fproducts%2Fp1');
  assert.equal(authLink('/register', '/products', '?q=ao&minPrice=1'), '/register?next=%2Fproducts%3Fq%3Dao%26minPrice%3D1');
});

test('the home page and the login pages themselves need no return path', () => {
  assert.equal(authLink('/login', '/', ''), '/login');
  assert.equal(authLink('/login', '/register', '?next=%2Fcart'), '/login?next=%2Fcart');
  assert.equal(authLink('/register', '/login', ''), '/register');
});

const ORIGIN = 'http://localhost:5173';
const BS = String.fromCharCode(92);

test('a return path on this site is kept after login', () => {
  assert.equal(safeNext('/products/p1?q=ao#top', ORIGIN), '/products/p1?q=ao#top');
  assert.equal(safeNext(null, ORIGIN), '/');
});

test('a return path that leaves the site goes home instead', () => {
  // Browsers read a backslash as a slash and drop tabs and newlines, so all of these open another host.
  for (const next of ['//evil.example', '/' + BS + 'evil.example/x', BS + BS + 'evil.example', '/\t/evil.example',
    '/\n/evil.example', '/.//evil.example', 'https://evil.example', 'evil.example', '']) {
    assert.equal(safeNext(next, ORIGIN), '/', JSON.stringify(next));
  }
});

test('the login link does not carry on a return path that leaves the site', () => {
  assert.equal(authLink('/login', '/register', '?next=' + encodeURIComponent('/' + BS + 'evil.example')), '/login');
});
