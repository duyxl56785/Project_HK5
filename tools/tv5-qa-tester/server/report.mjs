const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
const cell = value => `"${String(value ?? '').replace(/^[=+@-]/, "'$&").replace(/"/g, '""')}"`;
export function renderHtml(report) {
  const rows = report.suites.map(s => `<tr><td>${esc(s.id)}</td><td>${esc(s.status)}</td><td>${esc(s.expected)}</td><td>${esc(s.actual)}</td><td>${esc(s.durationMs ?? '')}</td></tr>`).join('');
  return `<!doctype html><html lang="vi"><meta charset="utf-8"><title>Soopi TV5 QA Tester ${esc(report.id)}</title><style>body{font:16px system-ui;margin:2rem;color:#142339}table{border-collapse:collapse;width:100%}td,th{border:1px solid #ccd4df;padding:.6rem;text-align:left}th{background:#eaf0fa}</style><h1>Soopi TV5 QA Tester</h1><p>Người kiểm thử: Nguyễn Minh Duy (TV5)</p><p>Run: ${esc(report.id)} | SHA: ${esc(report.sha)} | ${esc(report.timestamp)} | ${esc(report.status)}</p><p>Môi trường: ${esc(JSON.stringify(report.environment))}</p><table><tr><th>ID</th><th>Kết quả</th><th>Mong đợi</th><th>Thực tế</th><th>ms</th></tr>${rows}</table><p>Đối chiếu SHA256SUMS.txt với raw log và JUnit XML trong cùng thư mục. PASS unit không chứng minh production E2E.</p></html>`;
}
export function renderCsv(report) {
  return ['id,status,expected,actual,durationMs,sha,environment', ...report.suites.map(s => [s.id,s.status,s.expected,s.actual,s.durationMs ?? '',report.sha,JSON.stringify(report.environment)].map(cell).join(','))].join('\r\n') + '\r\n';
}
