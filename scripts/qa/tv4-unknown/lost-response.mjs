import http from 'node:http';
import { createHash } from 'node:crypto';

export const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

// Preparation only: no CLI/listener, Compose override, credential, or flag activation.
// A runner must first obtain approval for the exact isolated target/source/tuple.
export function lostResponseHandler({ target, operationId, bodySha256, onReceipt }) {
  const url = new URL(target);
  if (url.protocol !== 'http:' || url.hostname !== '127.0.0.1' || url.username || url.password
      || url.search || url.hash || !operationId || !/^[a-f0-9]{64}$/.test(bodySha256)
      || typeof onReceipt !== 'function') throw new Error('An exact loopback target and tuple receipt are required');
  return async (req, res) => {
    if (req.method !== 'POST' || req.url !== url.pathname) {
      res.writeHead(400).end();
      return;
    }
    try {
      const chunks = [];
      let size = 0;
      for await (const chunk of req) {
        size += chunk.length;
        if (size > 1024 * 1024) { res.writeHead(413).end(); return; }
        chunks.push(chunk);
      }
      const body = Buffer.concat(chunks);
      if (sha256(body) !== bodySha256 || JSON.parse(body).operationId !== operationId) {
        res.writeHead(400).end();
        return;
      }
      const headers = { ...req.headers, host: url.host, connection: 'close', 'content-length': body.length };
      delete headers['transfer-encoding'];
      const upstream = http.request(url, { method: 'POST', headers }, response => {
        const digest = createHash('sha256');
        let bytes = 0;
        response.on('data', chunk => { bytes += chunk.length; digest.update(chunk); });
        response.on('error', () => res.destroy());
        response.on('end', () => {
          try {
            // This is transport evidence ONLY. It is not proof of a database COMMIT.
            onReceipt({ operationId, bodySha256, upstreamStatus: response.statusCode,
              responseSha256: digest.digest('hex'), responseBytes: bytes, responseSuppressed: true });
          } finally {
            // Do not synthesize HTTP 409/500 or issue any retry/compensation request.
            res.destroy();
          }
        });
      });
      upstream.setTimeout(30000, () => upstream.destroy());
      upstream.on('error', () => res.destroy());
      upstream.end(body);
    } catch { res.destroy(); }
  };
}
