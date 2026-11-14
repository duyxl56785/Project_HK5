/** C1 form time is always civil time in Asia/Ho_Chi_Minh (UTC+07, no DST). */
export function toIsoVN(value: string): string {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) throw new Error('Ngày giờ Việt Nam không hợp lệ');
  const instant = new Date(value + '+07:00');
  if (!Number.isFinite(instant.getTime()) || toVNInput(instant.toISOString()) !== value)
    throw new Error('Ngày giờ Việt Nam không hợp lệ');
  return instant.toISOString();
}
export function toVNInput(value: string): string {
  const time=Date.parse(value);
  return Number.isFinite(time) ? new Date(time+7*60*60*1000).toISOString().slice(0,16) : '';
}
