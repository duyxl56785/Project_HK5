# Image fixtures

All five images are original 16 x 12 synthetic blue/yellow rectangles, not downloaded photographs or header-only samples. JPEG, PNG and extended WebP were encoded using Microsoft Edge's Canvas encoder on 2026-10-20; tests decode their pixels with independent Java ImageIO readers.

Reproduction in a browser console (no application endpoint needed):

```javascript
const c = document.createElement('canvas');
c.width = 16; c.height = 12;
const x = c.getContext('2d');
x.fillStyle = '#265caa'; x.fillRect(0, 0, 16, 12);
x.fillStyle = '#f3b521'; x.fillRect(3, 2, 8, 7);
const jpeg = c.toDataURL('image/jpeg', 0.85);
const png = c.toDataURL('image/png');
const webp = c.toDataURL('image/webp', 0.8);
x.clearRect(0, 0, 2, 2);
const alphaWebp = c.toDataURL('image/webp', 1);
```

- `sample.jpg`: JPEG; `sample.png`: PNG.
- `sample.webp`: VP8X / ICCP / lossy VP8.
- `alpha.webp`: VP8X / ICCP / lossless VP8L with transparency.
- `lossless.webp`: the complete VP8L chunk (including its encoded pixels) from `alpha.webp`, wrapped as simple `RIFF(size) WEBP VP8L(size,payload,padding)`, with no ICC profile/VP8X. No pixel data was invented or truncated.

Save the base64 portion of each data URL as binary; encoding versions can produce different bytes. Boundary and corrupt variants are made in memory by the unit tests. The exact 2 MiB positive PNG uses a valid unknown ancillary chunk plus CRC, not arbitrary trailing padding.
