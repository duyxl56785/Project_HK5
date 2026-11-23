/** One buyable size/color option of a Product; its id is the productId that Cart and Order use. */
export interface VariantOffer {
  readonly id: string; readonly size: string | null; readonly color: string | null; readonly sku: string;
  readonly unitPrice: string; readonly baseUnitPrice: string;
  readonly promotion: { readonly kind: string; readonly endsAt: string } | null; readonly available: number;
}

export interface Selection { readonly size: string | null; readonly color: string | null }

const distinct = (values: readonly (string | null)[]) =>
  values.filter((value, index): value is string => value !== null && values.indexOf(value) === index);

export function optionValues(offers: readonly VariantOffer[]) {
  return { sizes: distinct(offers.map((o) => o.size)), colors: distinct(offers.map((o) => o.color)) };
}

export function pickVariant(offers: readonly VariantOffer[], selection: Selection): VariantOffer | undefined {
  return offers.find((o) => o.size === selection.size && o.color === selection.color);
}

/** The variant the page was opened with, else the first in stock, else the first. */
export function initialSelection(offers: readonly VariantOffer[], requestedId: string): Selection {
  const chosen = offers.find((o) => o.id === requestedId) ?? offers.find((o) => o.available > 0) ?? offers[0];
  return chosen ? { size: chosen.size, color: chosen.color } : { size: null, color: null };
}

/** Same format as the server: "M / Đỏ", or the one part present. */
export function variantLabel(size: string | null, color: string | null) {
  return size !== null && color !== null ? `${size} / ${color}` : size ?? color ?? '';
}

export function baseName(name: string, size: string | null, color: string | null) {
  const suffix = ` (${variantLabel(size, color)})`;
  return name.endsWith(suffix) ? name.slice(0, -suffix.length) : name;
}
