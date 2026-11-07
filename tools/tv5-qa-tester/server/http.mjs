import http from 'node:http';
import { readFile, readdir, stat } from 'node:fs/promises';
import path from 'node:path';
import archiver from 'archiver';
import { spawn } from 'node:child_process';
import { suites, publicSuite } from './catalog.mjs';
import { Runner, evidenceRoot, toolDir } from './runner.mjs';

const types = { '.html':'text/html; charset=utf-8', '.js':'text/javascript; charset=utf-8', '.css':'text/css; charset=utf-8', '.json':'application/json; charset=utf-8', '.csv':'text/csv; charset=utf-8', '.txt':'text/plain; charset=utf-8', '.png':'image/png', '.xml':'application/xml; charset=utf-8' };
const send = (res, code, value, type = 'application/json; charset=utf-8') => { res.writeHead(code, { 'Content-Type':type, 'Cache-Control':'no-store', 'X-Content-Type-Options':'nosniff', 'Content-Security-Policy':"default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'" }); res.end(type.startsWith('application/json') ? JSON.stringify(value) : value); };
async function jsonBody(req) { let body = ''; for await (const chunk of req) { body += chunk; if (body.length > 4096) throw new Error('Request quá lớn'); } return JSON.parse(body); }
function safeFile(base, relative) {
  const target = path.resolve(base, relative);
  if (!target.startsWith(path.resolve(base) + path.sep)) throw new Error('Đường dẫn không hợp lệ');
  return target;
}
export function createServer({ runner = new Runner(), uiDir = path.join(toolDir, 'dist'), port = 4318 } = {}) {
  return http.createServer(async (req, res) => {
    const host = req.headers.host;
    const origin = req.headers.origin;
    if (![`127.0.0.1:${port}`,`localhost:${port}`].includes(host) || (origin && ![`http://127.0.0.1:${port}`,`http://localhost:${port}`].includes(origin))) return send(res, 403, { error:'Chỉ localhost' });
    const url = new URL(req.url, `http://${host}`);
    try {
      if (req.method === 'GET' && url.pathname === '/api/suites') return send(res, 200, suites.map(publicSuite));
      if (req.method === 'GET' && url.pathname === '/api/history') return send(res, 200, [...runner.runs.values()].sort((a,b)=>String(b.timestamp??b.id).localeCompare(String(a.timestamp??a.id))));
      if (req.method === 'POST' && url.pathname === '/api/runs') { const { suiteIds, safeAll } = await jsonBody(req); return send(res, 202, safeAll ? await runner.runSafe() : await runner.start(suiteIds)); }
      const run = url.pathname.match(/^\/api\/runs\/([a-zA-Z0-9-]+)(?:\/(cancel))?$/);
      if (run) {
        if (req.method === 'GET' && !run[2]) return send(res, runner.runs.has(run[1]) ? 200 : 404, runner.runs.get(run[1]) ?? { error:'Không tìm thấy run' });
        if (req.method === 'POST' && run[2]) return send(res, runner.cancel(run[1]) ? 200 : 409, { cancelled: runner.active?.cancelled ?? false });
      }
      const open = url.pathname.match(/^\/api\/evidence\/([a-zA-Z0-9-]+)\/open$/);
      if (req.method === 'POST' && open) {
        if (!runner.runs.has(open[1])) return send(res, 404, {error:'Không tìm thấy run'});
        const folder = path.join(runner.root, open[1]);
        await stat(folder);
        const executable = process.platform === 'win32' ? 'explorer.exe' : process.platform === 'darwin' ? 'open' : 'xdg-open';
        const child = spawn(executable, [folder], {shell:false, detached:true, stdio:'ignore', windowsHide:true});
        child.on('error',()=>{}); child.unref();
        return send(res, 200, {opened:true});
      }
      const evidence = url.pathname.match(/^\/api\/evidence\/([a-zA-Z0-9-]+)\/([a-zA-Z0-9_.-]+(?:\/[a-zA-Z0-9_.-]+)?)$/);
      const archive = url.pathname.match(/^\/api\/evidence\/([a-zA-Z0-9-]+)\/export$/);
      if (req.method === 'GET' && archive) {
        if (!runner.runs.has(archive[1])) return send(res, 404, {error:'Không tìm thấy run'});
        res.writeHead(200, { 'Content-Type':'application/zip', 'Content-Disposition':`attachment; filename="${archive[1]}.zip"`, 'Cache-Control':'no-store' });
        const zip = archiver('zip', {zlib:{level:9}});
        zip.on('error', () => res.destroy()); zip.pipe(res);
        zip.directory(path.join(runner.root, archive[1]), archive[1]);
        const screenshotDir = path.join(runner.root, 'screenshots', archive[1]);
        try { await stat(screenshotDir); zip.directory(screenshotDir, `${archive[1]}/screenshots/${archive[1]}`); } catch {}
        await zip.finalize(); return;
      }
      const screenshot = url.pathname.match(/^\/api\/screenshots\/([a-zA-Z0-9-]+)\/([A-Z0-9-]+)\/([a-zA-Z0-9_.-]+\.png)$/);
      if (req.method === 'GET' && screenshot) {
        if (!runner.runs.has(screenshot[1])) return send(res, 404, {error:'Không tìm thấy run'});
        const data = await readFile(safeFile(path.join(runner.root, 'screenshots', screenshot[1], screenshot[2]), screenshot[3]));
        res.writeHead(200, { 'Content-Type':'image/png', 'X-Content-Type-Options':'nosniff', 'Cache-Control':'no-store' }); return res.end(data);
      }
      if (req.method === 'GET' && evidence) {
        if (!runner.runs.has(evidence[1])) return send(res, 404, {error:'Không tìm thấy run'});
        const base = path.join(runner.root, evidence[1]);
        const file = safeFile(base, evidence[2]);
        const data = await readFile(file);
        res.writeHead(200, { 'Content-Type':types[path.extname(file)] ?? 'application/octet-stream', 'Content-Disposition':`attachment; filename="${path.basename(file)}"`, 'X-Content-Type-Options':'nosniff', 'Cache-Control':'no-store' }); return res.end(data);
      }
      if (req.method === 'GET' && url.pathname === '/api/evidence-root') return send(res, 200, { path: runner.root });
      if (req.method !== 'GET') return send(res, 405, {error:'Method không hợp lệ'});
      const file = safeFile(uiDir, url.pathname === '/' ? 'index.html' : decodeURIComponent(url.pathname.slice(1)));
      const data = await readFile(file);
      res.writeHead(200, { 'Content-Type':types[path.extname(file)] ?? 'application/octet-stream', 'X-Content-Type-Options':'nosniff' }); res.end(data);
    } catch (error) { send(res, error.code === 'ENOENT' ? 404 : 400, { error:error.message }); }
  });
}
