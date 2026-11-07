import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { access, mkdir, readdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { byId, safeIds, suites } from './catalog.mjs';
import { renderHtml, renderCsv } from './report.mjs';
import { diagnose, redact } from './diagnostics.mjs';

export const toolDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const repoDir = path.resolve(toolDir, '../..');
export const evidenceRoot = path.join(repoDir, 'reports/minh-chung-kiem-thu/tv5-qa-tool');
const forbidden = /(?:-----BEGIN [A-Z ]*PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9_]{20,}|(?:password|token|authorization|jwt|secret)\s*[=:]\s*[^\s,;]{5,})/i;
const sha256 = data => createHash('sha256').update(data).digest('hex');
const cleanEnv = extra => ({ ...Object.fromEntries(Object.entries(process.env).filter(([key]) => /^(?:PATH|Path|PATHEXT|SystemRoot|SYSTEMROOT|TEMP|TMP|JAVA_HOME|GRADLE_USER_HOME|APPDATA|LOCALAPPDATA|USERPROFILE|HOME|CI|LANG|LC_ALL)$/.test(key))), ...extra });

export function terminateProcessTree(child, { platform = process.platform, spawnProcess = spawn } = {}) {
  if (!child || child.exitCode !== null || child.signalCode !== null) return false;
  if (platform !== 'win32' || !Number.isSafeInteger(child.pid) || child.pid <= 0 || child.pid === process.pid) {
    child.kill();
    return true;
  }
  // The Gradle wrapper starts JVM/Testcontainers children. Stop only the tree of this spawned PID.
  const killer = spawnProcess('taskkill', ['/PID', String(child.pid), '/T', '/F'], { shell: false, windowsHide: true, stdio: 'ignore' });
  const fallback = () => { if (child.exitCode === null && child.signalCode === null) child.kill(); };
  killer.once('error', fallback);
  killer.once('close', code => { if (code !== 0) fallback(); });
  return true;
}

export function execute(executable, args, options = {}) {
  return new Promise(resolve => {
    let output = '', timedOut = false, cancelled = false;
    const child = spawn(executable, args, { cwd: options.cwd, env: cleanEnv(options.envExtra), shell: false, windowsHide: true });
    const timer = setTimeout(() => { timedOut = true; terminateProcessTree(child); }, options.timeoutMs ?? 180000);
    options.onChild?.(child);
    for (const stream of [child.stdout, child.stderr]) stream?.on('data', chunk => { output += chunk.toString(); options.onOutput?.(chunk.toString()); });
    child.on('error', error => { output += `\nProcess error: ${error.message}`; });
    child.on('close', code => { clearTimeout(timer); cancelled = options.wasCancelled?.() ?? false; resolve({ code, output, timedOut, cancelled }); });
  });
}

async function gitSha() {
  const result = await execute('git', ['rev-parse', 'HEAD'], { cwd: repoDir, timeoutMs: 10000 });
  return /^[a-f0-9]{40}$/.test(result.output.trim()) ? result.output.trim() : 'UNKNOWN';
}

async function dockerLocal() {
  const context = await execute('docker', ['context', 'inspect', '--format', '{{.Endpoints.docker.Host}}'], { cwd: repoDir, timeoutMs: 10000 });
  const endpoint = context.output.trim();
  if (context.code !== 0 || !/^(?:npipe:\/\/|unix:\/\/)/.test(endpoint)) return false;
  const info = await execute('docker', ['info', '--format', '{{.ServerVersion}}'], { cwd: repoDir, timeoutMs: 10000 });
  return info.code === 0 && Boolean(info.output.trim());
}

async function xmlFiles(dir, since) {
  try {
    const entries = await readdir(dir, { withFileTypes: true });
    const { stat } = await import('node:fs/promises');
    const files = [];
    for (const entry of entries) if (entry.isFile() && entry.name.endsWith('.xml')) {
      const full = path.join(dir, entry.name);
      if ((await stat(full)).mtimeMs >= since - 2000) files.push(full);
    }
    return files;
  } catch { return []; }
}

export function countEvidence(kind, output, xmlTexts = []) {
  if (kind === 'playwright') {
    const number = word => Number(output.match(new RegExp(`(\\d+) ${word}`))?.[1] ?? 0);
    const pass = number('passed'), fail = number('failed'), skip = number('skipped');
    return pass + fail + skip ? { tests: pass + fail + skip, pass, fail, skip } : null;
  }
  if (kind === 'tap') {
    const get = name => Number(output.match(new RegExp(`^# ${name} (\\d+)$`, 'm'))?.[1] ?? NaN);
    const tests = get('tests'), pass = get('pass'), fail = get('fail'), skip = get('skipped');
    return Number.isFinite(tests + pass + fail + skip) && tests > 0 ? { tests, pass, fail, skip } : null;
  }
  if (!xmlTexts.length) return null;
  const counts = { tests: 0, pass: 0, fail: 0, skip: 0 };
  for (const xml of xmlTexts) {
    const tag = xml.match(/<testsuite\b[^>]*>/)?.[0];
    if (!tag) return null;
    const attr = key => Number(tag.match(new RegExp(`\\b${key}="(\\d+)"`))?.[1] ?? NaN);
    const tests = attr('tests'), failures = attr('failures'), errors = attr('errors'), skipped = attr('skipped');
    if (!Number.isFinite(tests + failures + errors + skipped)) return null;
    counts.tests += tests; counts.fail += failures + errors; counts.skip += skipped;
  }
  counts.pass = counts.tests - counts.fail - counts.skip;
  return counts.tests > 0 ? counts : null;
}

export class Runner {
  constructor({ exec = execute, docker = dockerLocal, root = evidenceRoot } = {}) {
    this.exec = exec; this.docker = docker; this.root = root; this.runs = new Map(); this.active = null;
  }
  async hydrate() {
    try {
      for (const entry of await readdir(this.root, {withFileTypes:true})) {
        if (!entry.isDirectory() || entry.name === 'screenshots') continue;
        try { const report=JSON.parse(await readFile(path.join(this.root,entry.name,'report.json'),'utf8')); if (report.id===entry.name) {
          if(report.status==='RUNNING') {report.status='INTERRUPTED';report.recoveryNote='Máy chủ đã khởi động lại; không có tiến trình nào được tiếp quản. Raw history giữ nguyên.';for(const row of report.suites)if(row.status==='RUNNING')row.status='INTERRUPTED';}
          this.runs.set(report.id,report);
        } } catch {}
      }
    } catch {}
  }
  async start(ids) {
    if (this.active) throw new Error('Một lần chạy đang hoạt động; không chạy đồng thời database suites.');
    if (!Array.isArray(ids) || !ids.length || ids.length > suites.length || ids.some(id => typeof id !== 'string' || !byId.has(id)) || new Set(ids).size !== ids.length) throw new Error('Chỉ được chọn ID trong allowlist.');
    this.active = { id:null, child:null, cancelled:false };
    let sha;
    try { sha = await gitSha(); } catch(error) {this.active=null;throw error;}
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const id = `${timestamp}-${sha}-${crypto.randomUUID().slice(0, 8)}`;
    const report = { id, sha, timestamp: new Date().toISOString(), environment: { platform: process.platform, node: process.version, localhost: true }, status: 'RUNNING', suites: ids.map(key => ({ id: key, status: 'NOT_RUN', expected: byId.get(key).expected, actual: 'Chưa chạy' })) };
    this.runs.set(id, report); this.active = { id, child: null, cancelled: false };
    this.process(report).catch(async error=>{
      const row=report.suites.find(r=>r.status==='RUNNING')??report.suites[0];row.status='FAIL';row.actual='Runner exception: không thể hoàn thành kiểm thử';
      row.bugs=diagnose(row,byId.get(row.id),report,String(error));report.status='FAIL';report.finishedAt=new Date().toISOString();
      try {await mkdir(path.join(this.root,id),{recursive:true});row.log='runner-error.log';await writeFile(path.join(this.root,id,row.log),redact(error.stack??error));}catch{}
      try {await this.persist(report);}catch {report.persistenceError='Không ghi được evidence; không được coi kết quả là VERIFIED';}
    }).finally(() => { if(this.active?.id===id)this.active = null; });
    return report;
  }
  async runSafe() { return this.start(safeIds); }
  cancel(id) {
    if (this.active?.id !== id) return false;
    this.active.cancelled = true; terminateProcessTree(this.active.child); return true;
  }
  async process(report) {
    const dir = path.join(this.root, report.id);
    await mkdir(path.join(dir, 'junit'), { recursive: true });
    await this.persist(report);
    for (const row of report.suites) {
      if (this.active?.cancelled) break;
      const suite = byId.get(row.id);
      if (suite.disabledReason) { row.status = 'BLOCKED'; row.actual = suite.disabledReason; row.bugs=diagnose(row,suite,report);continue; }
      if (suite.requiresDocker && !(await this.docker())) { row.status = 'BLOCKED_ENV'; row.actual = 'Docker local không hoạt động hoặc context không cục bộ'; row.bugs=diagnose(row,suite,report);continue; }
      if (suite.requiresFrontendDeps) {
        try { await access(path.join(repoDir, 'frontend/node_modules/vite/package.json')); }
        catch { row.status = 'BLOCKED_ENV'; row.actual = 'Thiếu frontend dependencies; chạy npm ci trong frontend của worktree này'; continue; }
      }
      if (this.active?.cancelled) break;
      row.status = 'RUNNING'; row.startedAt = new Date().toISOString();
      await this.persist(report);
      const started = Date.now();
      const cmd = suite.command;
      const screenshotDir = path.join(this.root, 'screenshots', report.id, row.id);
      if (cmd.evidence === 'playwright') await mkdir(screenshotDir, { recursive: true });
      const result = await this.exec(cmd.executable, cmd.args, { cwd: path.join(repoDir, cmd.cwd), timeoutMs: suite.timeoutMs, envExtra: { ...(cmd.envExtra ?? {}), ...(cmd.evidence === 'playwright' ? { SOOPI_QA_SCREENSHOT_DIR: screenshotDir } : {}) },
        onChild: child => { if (this.active) this.active.child = child; }, wasCancelled: () => this.active?.cancelled ?? false });
      row.durationMs = Date.now() - started;
      const log = Buffer.from(result.output ?? '', 'utf8');
      const xmlPaths = cmd.evidence === 'junit' ? await xmlFiles(path.join(repoDir, cmd.results), started) : [];
      const xmlTexts = await Promise.all(xmlPaths.map(file => readFile(file, 'utf8')));
      row.counts = countEvidence(cmd.evidence, result.output ?? '', xmlTexts);
      row.exitCode = result.code;
      row.status = (result.cancelled||this.active?.cancelled) ? 'CANCELLED' : result.timedOut ? 'TIMEOUT' : result.code !== 0 ? 'FAIL' : row.counts?.fail ? 'FAIL' : row.counts?.tests && row.counts.skip===row.counts.tests ? 'SKIP' : row.counts?.tests ? 'PASS' : 'NOT_RUN';
      row.actual = `${row.status}: exit ${result.code}, ${row.counts?.tests ?? 0} test, ${row.counts?.pass ?? 0} PASS, ${row.counts?.fail ?? 0} FAIL, ${row.counts?.skip ?? 0} SKIP`;
      if (!forbidden.test(log.toString()) && !xmlTexts.some(forbidden.test.bind(forbidden))) {
        const logName = `${row.id}.log`;
        await writeFile(path.join(dir, logName), log);
        row.log = logName;
        row.junit = [];
        for (let n = 0; n < xmlPaths.length; n++) {
          const name = `${row.id}-${path.basename(xmlPaths[n])}`;
          await writeFile(path.join(dir, 'junit', name), await readFile(xmlPaths[n]));
          row.junit.push(`junit/${name}`);
        }
      } else {row.evidenceWarning = 'Raw log/XML chứa chuỗi giống secret; không lưu artifact.'; if(row.status==='PASS')row.status='BLOCKED';}
      if (cmd.evidence === 'playwright') {
        row.screenshots = (await readdir(screenshotDir)).filter(name => /^[a-zA-Z0-9_.-]+\.png$/.test(name)).map(name => `screenshots/${report.id}/${row.id}/${name}`);
        row.metadata=(await readdir(screenshotDir)).filter(name=>/^[a-zA-Z0-9_.-]+\.json$/.test(name)).map(name=>`screenshots/${report.id}/${row.id}/${name}`);
        for(const name of row.metadata){const entries=JSON.parse(await readFile(path.join(this.root,name),'utf8'));const issue=entries.find(e=>e.kind==='network'||e.kind==='pageerror'||e.status>=500);if(issue){row.endpoint=issue.endpoint;row.httpStatus=issue.status;}}
        if (row.screenshots.length !== (row.counts?.tests??0)-(row.counts?.skip??0)) {
          if (row.status === 'PASS') row.status = 'FAIL';
          row.actual += '; thiếu screenshot của một hoặc nhiều test';
        }
      }
      row.bugs=diagnose(row,suite,report,result.output??'');
      await this.persist(report);
    }
    report.status = this.active?.cancelled?'CANCELLED':report.suites.some(s => s.status === 'FAIL') ? 'FAIL' : report.suites.some(s => s.status === 'TIMEOUT') ? 'TIMEOUT' : report.suites.some(s => s.status === 'CANCELLED') ? 'CANCELLED' : report.suites.every(s => s.status === 'SKIP')?'SKIP':report.suites.every(s => s.status === 'PASS') ? 'PASS' : 'PARTIAL';
    report.finishedAt = new Date().toISOString();
    await this.persist(report);
  }
  async persist(report) {
    const dir=path.join(this.root,report.id);await mkdir(dir,{recursive:true});
    await writeFile(path.join(dir, 'report.json'), JSON.stringify(report, null, 2));
    await writeFile(path.join(dir, 'report.html'), renderHtml(report));
    await writeFile(path.join(dir, 'report.csv'), renderCsv(report));
    const files = ['report.json', 'report.html', 'report.csv', ...report.suites.flatMap(s => [s.log, ...(s.junit ?? [])]).filter(Boolean)];
    const hashes = [];
    for (const file of files) hashes.push(`${sha256(await readFile(path.join(dir, file)))}  ${file}`);
    for (const screenshot of report.suites.flatMap(s => [...(s.screenshots ?? []),...(s.metadata??[])])) hashes.push(`${sha256(await readFile(path.join(this.root, screenshot)))}  ${screenshot}`);
    await writeFile(path.join(dir, 'SHA256SUMS.txt'), hashes.join('\n') + '\n');
  }
}
