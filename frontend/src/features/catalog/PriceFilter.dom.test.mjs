// Real React DOM regression on happy-dom for the Catalog price range.
// Run from frontend with: npm test
import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { Window } from 'happy-dom';
import { createServer } from 'vite';

let environment, vite, createRoot, act, PriceFilter;
before(async () => {
  environment = new Window({ url: 'http://localhost/' });
  globalThis.window = environment;
  globalThis.document = environment.document;
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: environment.navigator });
  globalThis.HTMLElement = environment.HTMLElement;
  globalThis.Node = environment.Node;
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  ({ act } = await import('react'));
  vite = await createServer({ server: { middlewareMode: true, hmr: false, watch: null }, optimizeDeps: { noDiscovery: true }, logLevel: 'silent' });
  ({ PriceFilter } = await vite.ssrLoadModule('/src/features/catalog/PriceFilter.tsx'));
});
after(async () => {
  await vite?.close();
  await environment?.happyDOM?.abort?.();
});

async function mount(props) {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const root = createRoot(host);
  await act(async () => root.render(createElement(PriceFilter, props)));
  return { host, root };
}

const type = (input, value) => {
  input.value = value;
  input.dispatchEvent(new window.Event('input', { bubbles: true }));
};

test('submitting the range applies both prices at once, not only the field that lost focus', async () => {
  const applied = [];
  const { host, root } = await mount({ min: '', max: '', onApply: (range) => applied.push(range) });
  const [min, max] = host.querySelectorAll('input');
  type(min, '200000');
  type(max, '400000');
  await act(async () => host.querySelector('button[type="submit"]').click());
  assert.deepEqual(applied.at(-1), { minPrice: '200000', maxPrice: '400000' });
  await act(async () => root.unmount());
});

test('leaving a field unchanged applies nothing, so the current page is kept', async () => {
  const applied = [];
  const { host, root } = await mount({ min: '100000', max: '', onApply: (range) => applied.push(range) });
  const [min] = host.querySelectorAll('input');
  await act(async () => { min.focus(); min.blur(); });
  assert.deepEqual(applied, []);
  await act(async () => root.unmount());
});

test('the fields follow bounds changed from outside, such as clearing all filters', async () => {
  const props = { min: '100000', max: '500000', onApply: () => {} };
  const { host, root } = await mount(props);
  await act(async () => root.render(createElement(PriceFilter, { ...props, min: '', max: '' })));
  assert.deepEqual([...host.querySelectorAll('input')].map((input) => input.value), ['', '']);
  await act(async () => root.unmount());
});

test('applying "Giá từ" on leaving it keeps the "đến" being typed', async () => {
  // Same order as a shopper: type min, move to max (min is applied), type max before the page re-renders.
  const props = { min: '', max: '', onApply: () => {} };
  const { host, root } = await mount(props);
  const [min, max] = host.querySelectorAll('input');
  type(min, '200000');
  type(max, '400000');
  await act(async () => root.render(createElement(PriceFilter, { ...props, min: '200000' })));
  assert.deepEqual([min.value, max.value], ['200000', '400000']);
  await act(async () => root.unmount());
});

test('an emptied field clears that bound', async () => {
  const applied = [];
  const { host, root } = await mount({ min: '100000', max: '500000', onApply: (range) => applied.push(range) });
  const [, max] = host.querySelectorAll('input');
  assert.equal(max.value, '500000');
  type(max, ' ');
  await act(async () => host.querySelector('form').requestSubmit());
  assert.deepEqual(applied.at(-1), { minPrice: '100000', maxPrice: '' });
  await act(async () => root.unmount());
});
