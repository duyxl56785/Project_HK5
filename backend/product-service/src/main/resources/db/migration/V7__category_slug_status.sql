-- Existing rows get the slug CategoryService.slugOf would give.
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
)
UPDATE categories c SET slug = b.slug FROM base b WHERE c.id = b.id;

-- Each first holder keeps its slug; a repeat takes the next number free across all rows, as CategoryService.uniqueSlug does.
DO $$
DECLARE
    repeated RECORD;
    suffix INT;
BEGIN
    FOR repeated IN
        SELECT id, slug FROM (SELECT id, slug, row_number() OVER (PARTITION BY slug ORDER BY id) AS position FROM categories) numbered
        WHERE position > 1 ORDER BY id
    LOOP
        suffix := 2;
        WHILE EXISTS (SELECT 1 FROM categories WHERE slug = repeated.slug || '-' || suffix) LOOP
            suffix := suffix + 1;
        END LOOP;
        UPDATE categories SET slug = repeated.slug || '-' || suffix WHERE id = repeated.id;
    END LOOP;
END $$;

-- The default covers direct SQL inserts only; CategoryService always writes a slug derived from the name.
ALTER TABLE categories
    ALTER COLUMN slug SET DEFAULT 'danh-muc-' || replace(gen_random_uuid()::text, '-', ''),
    ALTER COLUMN slug SET NOT NULL,
    ADD CONSTRAINT categories_slug_uk UNIQUE (slug),
    ADD CONSTRAINT categories_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    ADD CONSTRAINT categories_status_known CHECK (status IN ('ACTIVE', 'HIDDEN'));
