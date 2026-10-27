import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, describe, test } from 'node:test';
import { pathToFileURL } from 'node:url';
import { validateDemoImageFiles } from './demoImageValidation.ts';

const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jz1kAAAAASUVORK5CYII=', 'base64');
const jpeg = new Uint8Array([255, 216, 255, 224, 0, 16, 74, 70, 73, 70]);
const webp = new Uint8Array([82, 73, 70, 70, 22, 0, 0, 0, 87, 69, 66, 80, 86, 80, 56, 32]);
const file = (bytes = png, name = 'ảnh.png', type = 'image/png') => new File([bytes], name, { type });
let decoded, closed;
beforeEach(() => {
  decoded = 0; closed = 0;
  globalThis.createImageBitmap = async () => { decoded++; return { width: 1, height: 1, close() { closed++; } }; };
});
after(() => { delete globalThis.createImageBitmap; });

test('accepts PNG/JPEG/WebP only after decoding and closes decoded resources', async () => {
  const files = [file(), file(jpeg, 'photo.JPG', 'image/jpeg'), file(webp, 'photo.webp', 'image/webp')];
  assert.deepEqual(await validateDemoImageFiles(files, 2), { ok: true, files });
  assert.equal(decoded, 3); assert.equal(closed, 3);
});
for (const [name, files, existing] of [
  ['empty selection', [], 0],
  ['six total images', [file(), file()], 4],
  ['zero bytes', [file(new Uint8Array())], 0],
  ['larger than 2 MiB', [file(new Uint8Array(2 * 1024 * 1024 + 1))], 0],
  ['unsupported MIME', [file(png, 'x.svg', 'image/svg+xml')], 0],
  ['extension mismatch', [file(png, 'x.jpg')], 0],
  ['signature mismatch', [file(jpeg)], 0],
  ['invalid bytes', [file(new Uint8Array([1, 2, 3]))], 0],
]) test(`rejects ${name} before allocating a decoded image`, async () => {
  const result = await validateDemoImageFiles(files, existing);
  assert.equal(result.ok, false); assert.ok(result.error); assert.equal(decoded, 0);
});
test('rejects decode failure even with a matching PNG signature', async () => {
  globalThis.createImageBitmap = async () => { throw new Error('private decoder detail'); };
  const result = await validateDemoImageFiles([file()], 0);
  assert.equal(result.ok, false); assert.doesNotMatch(result.error, /private/);
});
for (const [width, height] of [[8193, 1], [4097, 4096], [0, 1]]) test(`rejects decoded dimensions ${width} × ${height}`, async () => {
  globalThis.createImageBitmap = async () => ({ width, height, close() { closed++; } });
  assert.equal((await validateDemoImageFiles([file()], 0)).ok, false);
  assert.equal(closed, 1);
});

describe('file picker DOM', () => {
  let window, server, Picker, createRoot, createElement, reactAct, container, root, revoked, created;
  const act = (callback) => reactAct(async () => { await callback(); });
  before(async () => {
    const { Window } = await import(process.env.TV2_DOM_MODULE ? pathToFileURL(process.env.TV2_DOM_MODULE).href : 'happy-dom');
    window = new Window({ url: 'http://localhost/' });
    for (const name of ['window', 'document', 'navigator', 'HTMLElement', 'HTMLInputElement', 'Event', 'MouseEvent']) Object.defineProperty(globalThis, name, { value: name === 'window' ? window : window[name], configurable: true });
    globalThis.IS_REACT_ACT_ENVIRONMENT = true;
    ({ act: reactAct, createElement } = await import('react'));
    ({ createRoot } = await import('react-dom/client'));
    const { createServer } = await import('vite');
    server = await createServer({ optimizeDeps: { noDiscovery: true, entries: [] }, server: { middlewareMode: true, hmr: { port: 0 }, watch: null } });
    ({ DemoImagePicker: Picker } = await server.ssrLoadModule('/src/demo/DemoImagePicker.tsx'));
  });
  beforeEach(() => {
    revoked = []; created = [];
    URL.createObjectURL = () => { const url = `blob:demo-${created.length}`; created.push(url); return url; };
    URL.revokeObjectURL = (url) => revoked.push(url);
    container = window.document.createElement('div'); window.document.body.append(container); root = createRoot(container);
  });
  afterEach(async () => { await act(() => root.unmount()); container.remove(); });
  after(async () => { await server?.close(); await window?.happyDOM.abort(); });
  const mount = (props = {}, key = 'seller-a') => act(() => root.render(createElement(Picker, { key, images: [], onChange: () => true, ...props })));
  async function select(files) {
    const input = container.querySelector('input[type="file"]');
    Object.defineProperty(input, 'files', { configurable: true, value: files });
    await act(() => input.dispatchEvent(new window.Event('change', { bubbles: true })));
  }
  test('selection passes real File metadata and escaped preview; remove delegates ownership to host', async () => {
    let images = [];
    const onChange = (next) => { images = next; root.render(createElement(Picker, { images, onChange })); return true; };
    await mount({ onChange });
    const input = container.querySelector('input'); assert.equal(input.multiple, true); assert.ok(container.querySelector(`label[for="${input.id}"]`));
    await select([file(png, '<img onerror=x>.png')]);
    assert.equal(images.length, 1); assert.equal(images[0].sizeBytes, png.length); assert.equal(images[0].mediaType, 'image/png');
    assert.equal(container.querySelectorAll('img').length, 1); assert.equal(container.querySelector('img').src, 'blob:demo-0');
    assert.match(container.textContent, /chưa upload lên máy chủ/);
    await act(() => container.querySelector('button').click());
    assert.equal(images.length, 0); assert.deepEqual(revoked, []);
  });
  for (const outcome of ['false', 'throw']) test(`revokes all new URLs when host returns ${outcome}`, async () => {
    await mount({ onChange: () => { if (outcome === 'throw') throw new Error('PRIVATE'); return false; } });
    await select([file()]);
    assert.deepEqual(revoked, created); assert.equal(container.querySelector('img'), null); assert.ok(container.querySelector('[role="alert"]'));
    assert.doesNotMatch(container.textContent, /PRIVATE/);
  });
  test('unmount preserves URLs already accepted by the host', async () => {
    await mount(); await select([file()]); await act(() => root.unmount());
    root = createRoot(container); assert.deepEqual(revoked, []);
  });
  for (const action of ['disabled', 'scope', 'unmount']) test(`${action} discards a late decode without calling old host`, async () => {
    let resolve, calls = 0;
    globalThis.createImageBitmap = () => new Promise((done) => { resolve = done; });
    const onChange = () => { calls++; return true; };
    await mount({ onChange }); await select([file()]);
    if (action === 'disabled') await mount({ onChange, disabled: true });
    if (action === 'scope') await mount({ onChange }, 'seller-b');
    if (action === 'unmount') { await act(() => root.unmount()); root = createRoot(container); }
    await act(() => resolve({ width: 1, height: 1, close() {} }));
    assert.equal(calls, 0); assert.deepEqual(created, []);
  });
  test('invalid selection exposes a linked error without invoking host', async () => {
    let calls = 0; await mount({ onChange: () => { calls++; return true; } }); await select([file(jpeg)]);
    const input = container.querySelector('input');
    assert.equal(input.getAttribute('aria-invalid'), 'true'); assert.ok(window.document.getElementById(input.getAttribute('aria-describedby').split(' ').at(-1)));
    assert.equal(calls, 0); assert.deepEqual(created, []);
  });
  test('drop receives File objects and rejects a sixth preview', async () => {
    let calls = 0;
    const images = Array.from({ length: 5 }, (_, index) => ({ id: String(index), url: `blob:owned-${index}`, name: 'ảnh.png', mediaType: 'image/png', sizeBytes: 10 }));
    await mount({ images, onChange: () => { calls++; return true; } });
    const event = new window.Event('drop', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'dataTransfer', { value: { files: [file()] } });
    await act(() => container.querySelector('.demo-image-drop').dispatchEvent(event));
    assert.equal(event.defaultPrevented, true); assert.equal(calls, 0); assert.ok(container.querySelector('[role="alert"]')); assert.deepEqual(created, []);
  });
  test('new selection discards the older decode and only hands off the latest file', async () => {
    const decodes = []; const accepted = [];
    globalThis.createImageBitmap = () => new Promise((resolve) => decodes.push(resolve));
    await mount({ onChange: (images) => { accepted.push(images); return true; } });
    await select([file(png, 'old.png')]); await select([file(png, 'latest.png')]);
    await act(() => decodes[1]({ width: 1, height: 1, close() {} }));
    await act(() => decodes[0]({ width: 1, height: 1, close() {} }));
    assert.equal(accepted.length, 1); assert.equal(accepted[0][0].name, 'latest.png'); assert.equal(created.length, 1);
  });
  test('partial preview creation failure revokes earlier URLs', async () => {
    let calls = 0;
    URL.createObjectURL = () => { if (created.length) throw new Error('private URL failure'); created.push('blob:first'); return 'blob:first'; };
    await mount({ onChange: () => { calls++; return true; } }); await select([file(), file(png, 'second.png')]);
    assert.equal(calls, 0); assert.deepEqual(revoked, ['blob:first']); assert.ok(container.querySelector('[role="alert"]'));
  });
  test('unmount aborts fallback Image decode and revokes its temporary URL', async () => {
    let calls = 0;
    const originalImage = globalThis.Image;
    delete globalThis.createImageBitmap;
    globalThis.Image = class { set src(value) { this.url = value; } };
    try {
      await mount({ onChange: () => { calls++; return true; } }); await select([file()]);
      assert.equal(created.length, 1);
      await act(() => root.unmount()); root = createRoot(container);
      assert.equal(calls, 0); assert.deepEqual(revoked, created);
    } finally { globalThis.Image = originalImage; }
  });
});
