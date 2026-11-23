import type { RefinementCtx } from 'zod';
import { internationalPhone } from './forms';

/** Calling codes offered next to a phone field; Vietnam first, as most buyers are there. */
export const PHONE_COUNTRIES = [
  { code: '+84', name: 'Việt Nam' },
  { code: '+1', name: 'Hoa Kỳ / Canada' },
  { code: '+65', name: 'Singapore' },
  { code: '+66', name: 'Thái Lan' },
  { code: '+81', name: 'Nhật Bản' },
  { code: '+82', name: 'Hàn Quốc' },
  { code: '+86', name: 'Trung Quốc' },
] as const;
export const DEFAULT_PHONE_CODE = PHONE_COUNTRIES[0].code;

/**
 * Joins the chosen code and the number as typed at home ("0901 234 567") into E.164, as User stores it.
 * A number typed with its own "+" is kept as is; an empty field stays empty.
 */
export function toE164(code: string, local: string): string {
  const typed = local.replace(/[\s.()-]/g, '');
  if (typed === '' || typed.startsWith('+')) return typed;
  return code + typed.replace(/^0/, '');
}

/** The reverse of toE164 for a stored number; a code not in the list stays in the number field. */
export function splitE164(value: string | null): { code: string; local: string } {
  const country = [...PHONE_COUNTRIES].sort((a, b) => b.code.length - a.code.length)
    .find(({ code }) => value?.startsWith(code));
  return country ? { code: country.code, local: value!.slice(country.code.length) } : { code: DEFAULT_PHONE_CODE, local: value ?? '' };
}

/** Form-level check for a { phoneCode, phone } pair: the joined number must be E.164 with at least 6 national digits. */
export const checkPhone = (required: boolean) => (value: { phoneCode: string; phone: string }, ctx: RefinementCtx) => {
  const phone = toE164(value.phoneCode, value.phone);
  if (phone === '' && !required) return;
  const country = splitE164(phone);
  if (phone === '' || !internationalPhone.safeParse(phone).success || country.local.replace('+', '').length < 6) {
    ctx.addIssue({ code: 'custom', path: ['phone'], message: phone === '' ? 'Nhập số điện thoại' : 'Số điện thoại không hợp lệ' });
  }
};
