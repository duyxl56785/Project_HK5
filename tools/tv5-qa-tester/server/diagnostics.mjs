export const redact = value => String(value ?? '').replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi,'[email ẩn]')
  .replace(/Bearer\s+\S+/gi,'Bearer [ẩn]').replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[JWT ẩn]')
  .replace(/((?:password|token|cookie|authorization|secret)\s*[=:]\s*)[^\s,;]+/gi,'$1[ẩn]');
export function diagnose(row,suite,report,output='') {
 if (['PASS','SKIP','NOT_RUN','CANCELLED'].includes(row.status))return [];
 const refused=/ECONNREFUSED|ERR_CONNECTION_REFUSED/i.test(output); const infrastructure=/EACCES|EPERM|ENOENT|executable doesn't exist/i.test(output);
 const blocked=row.status.startsWith('BLOCKED');
 const type=refused||row.status==='BLOCKED_ENV'?'ENVIRONMENT_FAILURE':blocked?'BLOCKED_DEPENDENCY':infrastructure||['TIMEOUT','INTERRUPTED','CANCELLED'].includes(row.status)?'TEST_INFRA_FAILURE':'APPLICATION_BUG';
 const code=refused?'ECONNREFUSED':output.match(/\b(EACCES|EPERM|ENOENT)\b/)?.[1]??row.status;
 return [{id:`BUG-${report.id}-${row.id}`,title:refused?'Không kết nối được API local':row.status==='INTERRUPTED'?'Lượt chạy bị gián đoạn khi máy chủ khởi động lại':row.status==='CANCELLED'?'Lượt chạy đã được hủy':blocked?'Kiểm thử bị chặn bởi điều kiện chạy':row.status==='TIMEOUT'?'Kiểm thử vượt thời gian cho phép':'Kiểm thử không đạt hoặc thiếu bằng chứng',
  module:suite.module,endpoint:row.endpoint??null,httpStatus:row.httpStatus??null,code,type,severity:['CANCELLED','INTERRUPTED'].includes(row.status)?'INFO':blocked?'P2':'P1',
  steps:[`Chọn ${row.id} trên QA Tester`, 'Chạy Run Selected và đối chiếu raw log của run này'],expected:row.expected,actual:row.actual,
  stackTrace:redact(output.split('\n').filter(l=>/Error| at |not ok|failed|ECONNREFUSED/i.test(l)).slice(0,16).join('\n')),
  source:suite.source,suspectedCause:refused?'Dịch vụ chưa khởi động hoặc sai proxy target':'Cần đối chiếu assertion, cấu hình và log; chưa suy nguyên nhân nghiệp vụ',
  verifiedCause:refused?'Kết nối TCP tới endpoint bị từ chối; chưa xác minh lý do dịch vụ không lắng nghe':row.status==='BLOCKED_ENV'?row.actual:null,
  remediation:refused?'Kiểm tra gateway 127.0.0.1:18080, docker compose ps/logs và Vite proxy trước khi thử lại':blocked?'Hoàn thiện dependency/owner gate được nêu trong actual':'Mở raw log và assertion; sửa đúng owner rồi chạy lại cùng suite',
  owner:type==='APPLICATION_BUG'?'Owner module (cần triage)':'TV5 QA / người vận hành local',status:row.status,sha:report.sha,environment:report.environment,timestamp:new Date().toISOString(),screenshots:row.screenshots??[],rawLog:row.log??null,junit:row.junit??[]}];
}
