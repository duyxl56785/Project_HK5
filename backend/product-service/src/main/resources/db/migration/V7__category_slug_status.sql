-- Existing rows get the slug CategoryService.slugOf would give; repeats take part of the id so a real "x-2" cannot clash.
ALTER TABLE categories
    ADD COLUMN slug VARCHAR(140),
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

WITH plain AS (
    SELECT id, lower(translate(normalize(name, NFC),
        'àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ',
        'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyydAAAAAAAAAAAAAAAAAEEEEEEEEEEEIIIIIOOOOOOOOOOOOOOOOOUUUUUUUUUUUYYYYYD')) AS text
    FROM categories
), base AS (
    SELECT id, COALESCE(NULLIF(btrim(left(btrim(regexp_replace(text, '[^a-z0-9]+', '-', 'g'), '-'), 120), '-'), ''), 'danh-muc') AS slug
    FROM plain
), numbered AS (
    SELECT id, slug, row_number() OVER (PARTITION BY slug ORDER BY id) AS position FROM base
)
UPDATE categories c
SET slug = CASE WHEN n.position = 1 THEN n.slug ELSE n.slug || '-' || left(c.id::text, 8) END
FROM numbered n
WHERE c.id = n.id;

-- The default covers direct SQL inserts only; CategoryService always writes a slug derived from the name.
ALTER TABLE categories
    ALTER COLUMN slug SET DEFAULT 'danh-muc-' || replace(gen_random_uuid()::text, '-', ''),
    ALTER COLUMN slug SET NOT NULL,
    ADD CONSTRAINT categories_slug_uk UNIQUE (slug),
    ADD CONSTRAINT categories_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    ADD CONSTRAINT categories_status_known CHECK (status IN ('ACTIVE', 'HIDDEN'));
