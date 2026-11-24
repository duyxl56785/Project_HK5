/** Seller-side draft of size/color variants before the Product is created. Values stay text until sent. */
export interface VariantRow {
  readonly size: string | null; readonly color: string | null; readonly sku: string;
  readonly unitPrice: string; readonly initialStock: string;
}
export type RowErrors = Partial<Record<'sku' | 'unitPrice' | 'initialStock', string>>;

export const SIZE_SUGGESTIONS = 'S, M, L, XL, XXL';
export const MAX_VARIANTS = 30;

/** Comma-separated options, trimmed; a repeat (ignoring case) keeps the first spelling. */
export function splitOptions(text: string): string[] {
  const seen = new Set<string>();
  return text.split(',').map((part) => part.trim()).filter((part) => {
    const key = part.toLocaleLowerCase('vi');
    if (!part || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

/** ASCII SKU fragment: Vietnamese marks removed, spaces to dashes, upper case. */
export function skuPart(text: string) {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[đĐ]/g, 'D')
    .replace(/[^A-Za-z0-9]+/g, '-').replace(/^-+|-+$/g, '').toUpperCase();
}

/** Every size × color pair (or each one alone when the other list is empty). */
export function buildRows(sizes: readonly string[], colors: readonly string[], baseSku: string, unitPrice: string): VariantRow[] {
  const pairs: [string | null, string | null][] = sizes.length && colors.length
    ? sizes.flatMap((size) => colors.map((color): [string, string] => [size, color]))
    : sizes.length ? sizes.map((size): [string, null] => [size, null]) : colors.map((color): [null, string] => [null, color]);
  return pairs.map(([size, color]) => ({
    size, color, unitPrice, initialStock: '0',
    sku: [baseSku.trim(), size, color].filter((part): part is string => !!part).map(skuPart).filter(Boolean).join('-'),
  }));
}

const wholeNumber = (text: string) => /^\d+$/.test(text.trim()) ? Number(text.trim()) : NaN;

export function rowErrors(rows: readonly VariantRow[]): RowErrors[] {
  const seen = new Set<string>();
  return rows.map((row) => {
    const errors: RowErrors = {};
    const sku = row.sku.trim();
    if (!sku) errors.sku = 'Nhập SKU';
    else if (sku.length > 128) errors.sku = 'SKU tối đa 128 ký tự';
    else if (seen.has(sku.toLowerCase())) errors.sku = 'SKU bị trùng';
    seen.add(sku.toLowerCase());
    const price = wholeNumber(row.unitPrice);
    if (!(price >= 1 && price <= 1_000_000_000)) errors.unitPrice = 'Giá phải là số nguyên từ 1';
    const stock = wholeNumber(row.initialStock);
    if (!(stock >= 0 && stock <= 1_000_000)) errors.initialStock = 'Tồn kho từ 0 đến 1.000.000';
    return errors;
  });
}

export function toVariantBody(rows: readonly VariantRow[]) {
  return rows.map((row) => ({
    size: row.size, color: row.color, sku: row.sku.trim(),
    unitPrice: Number(row.unitPrice.trim()), initialStock: Number(row.initialStock.trim()),
  }));
}
