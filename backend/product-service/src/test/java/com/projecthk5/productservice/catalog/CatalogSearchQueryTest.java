package com.projecthk5.productservice.catalog;

import static org.junit.jupiter.api.Assertions.*;
import com.projecthk5.productservice.product.ProductRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogSearchQueryTest {
    @ParameterizedTest @ValueSource(strings={"-1:10","0:0","0:-1","0:101","2147483647:100"})
    void invalidPageOrSizeIsRejected(String value) {
        String[] parts = value.split(":");
        invalid(() -> new CatalogSearchQuery(null,null,null,null,null,Integer.parseInt(parts[0]),Integer.parseInt(parts[1])));
    }

    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","1000000001"})
    void priceBoundsUseTheExistingExactWholeVndPolicy(String amount) {
        var value = new BigDecimal(amount);
        invalid(() -> new CatalogSearchQuery(null,null,value,null,null,0,10));
        invalid(() -> new CatalogSearchQuery(null,null,null,value,null,0,10));
    }

    @Test void inverseRangeAndInvalidTextAreRejected() {
        invalid(() -> new CatalogSearchQuery(null,null,BigDecimal.TEN,BigDecimal.ONE,null,0,10));
        for (String name : new String[]{"x".repeat(256),"bad\u0000name","bad\ud800","bad\udc00"}) {
            invalid(() -> new CatalogSearchQuery(name,null,null,null,null,0,10));
        }
    }

    @Test void blanksDefaultsAndIntegralScaleAreExplicitWithoutRounding() {
        var blank = new CatalogSearchQuery(" \t ",null,null,null,null,0,10);
        assertNull(blank.name());
        assertEquals(CatalogSearchQuery.Sort.NAME_ASC,blank.sort());
        BigDecimal min = new BigDecimal("1.0000");
        var query = new CatalogSearchQuery(" Áo ",null,min,new BigDecimal("1000000000"),CatalogSearchQuery.Sort.PRICE_ASC,2,10);
        assertEquals("Áo",query.name());
        assertSame(min,query.minimumVnd());
        assertEquals(20,query.offset());
    }

    private void invalid(Runnable operation) {
        assertEquals(ProductRuleException.Code.INVALID_INPUT,assertThrows(ProductRuleException.class,operation::run).getCode());
    }
}
