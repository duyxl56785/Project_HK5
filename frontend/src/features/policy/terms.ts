/** Must equal SellerInputs.TERMS_VERSION; the server rejects an application that accepted another version. */
export const TERMS_VERSION = '2026-11-11';

export const BUSINESS_TYPES = {
  INDIVIDUAL: 'Cá nhân',
  HOUSEHOLD: 'Hộ kinh doanh',
  COMPANY: 'Công ty',
} as const;

export type BusinessType = keyof typeof BUSINESS_TYPES;
