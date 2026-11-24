import type { FormEvent } from 'react';

export interface PriceRange { readonly minPrice: string; readonly maxPrice: string }

/**
 * Price bounds for the Catalog. Enter, the button or leaving a field applies both bounds together,
 * so a typed "đến" is never left showing without being used. An emptied field clears its bound.
 */
export function PriceFilter({ min, max, onApply }: {
  readonly min: string; readonly max: string; readonly onApply: (range: PriceRange) => void;
}) {
  const apply = (form: HTMLFormElement) => {
    const value = (name: string) => (form.elements.namedItem(name) as HTMLInputElement).value.trim();
    const range = { minPrice: value('minPrice'), maxPrice: value('maxPrice') };
    // Leaving a field without changing it must not reset the page the shopper is on.
    if (range.minPrice !== min || range.maxPrice !== max) onApply(range);
  };
  return <form className="row row--wrap" aria-label="Lọc theo giá" noValidate
    onSubmit={(event: FormEvent<HTMLFormElement>) => { event.preventDefault(); apply(event.currentTarget); }}>
    <label className="field field--inline">Giá từ
      <input name="minPrice" type="number" min={0} inputMode="numeric" defaultValue={min} onBlur={(e) => e.currentTarget.form && apply(e.currentTarget.form)} /></label>
    <label className="field field--inline">đến
      <input name="maxPrice" type="number" min={0} inputMode="numeric" defaultValue={max} onBlur={(e) => e.currentTarget.form && apply(e.currentTarget.form)} /></label>
    <button type="submit" className="btn">Áp dụng</button>
  </form>;
}
