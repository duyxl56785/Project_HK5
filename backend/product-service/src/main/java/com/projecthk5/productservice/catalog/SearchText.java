package com.projecthk5.productservice.catalog;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Accent folding for Catalog search, so "ao thun" finds "Áo thun". The query text is folded here and
 * the stored name is folded in SQL with translate(lower(name), VIETNAMESE, ASCII), which needs no
 * database extension. Both sides use the same letters because ASCII is built with {@link #fold}.
 */
public final class SearchText {
    /** Lower-case Vietnamese letters with marks; lower() in SQL turns capitals into these first. */
    public static final String VIETNAMESE =
            "àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ";
    public static final String ASCII = VIETNAMESE.codePoints()
            .mapToObj(Character::toString).map(SearchText::fold).reduce("", String::concat);

    private SearchText() {}

    /** Lower case without diacritics: "Áo Đà Lạt" becomes "ao da lat". */
    public static String fold(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace('đ', 'd');
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }
}
