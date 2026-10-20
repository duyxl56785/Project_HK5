package com.projecthk5.orderservice.catalog;

import com.projecthk5.orderservice.catalog.CatalogCheckoutBatchPreflight.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Contract-candidate tests only; no TV2 Catalog server, Auth, DB or Inventory traffic. */
class CatalogCheckoutBatchPreflightTest {
    private static final String VERSION = CatalogCheckoutBatchPreflight.CONTRACT_VERSION;

    private static Expected expected(String p, String store, String price) {
        return new Expected(p, store, "seller-" + store, new BigDecimal(price), "VND");
    }
    private static VerifiedProduct product(Expected e, String sku) {
        return new VerifiedProduct(e.productId(), sku, e.storeId(), e.sellerId(),
                e.unitPrice(), e.currency(), true, true, true, false);
    }
    private static ItemResult ready(Expected e, String sku) {
        return new ItemResult(e.productId(), "READY", product(e, sku));
    }
    private static BatchResponse allReady(ItemResult... items) {
        return new BatchResponse(VERSION, true, List.of(items));
    }
    private static void denied(List<Expected> expected, BatchResponse reply) {
        assertThrows(IllegalStateException.class,
                () -> CatalogCheckoutBatchPreflight.requireFullyReady(expected, reply));
    }

    @Test void acceptsTwoStoresAndOnlyAuthoritativeSkuInOriginalOrder() {
        Expected a = expected("product-1", "store-1", "19.00");
        Expected b = expected("product-2", "store-2", "25.50");
        var lines = CatalogCheckoutBatchPreflight.requireFullyReady(List.of(a, b),
                allReady(ready(a, "authoritative-sku-A"), ready(b, "authoritative-sku-B")));
        assertEquals(List.of("authoritative-sku-A","authoritative-sku-B"),
                lines.stream().map(VerifiedProduct::inventorySku).toList());
        assertEquals("store-2", lines.get(1).storeId());
        assertThrows(UnsupportedOperationException.class, () -> lines.add(product(a,"x")));
    }

    @Test void enforcesBatchCardinalityOneThroughOneHundredAndUniqueProductIds() {
        Expected e = expected("p", "s", "9");
        assertThrows(IllegalArgumentException.class,
                () -> CatalogCheckoutBatchPreflight.requireFullyReady(List.of(), allReady()));
        denied(List.of(e, e), allReady(ready(e,"SKU-A"),ready(e,"SKU-B")));
        var hundred = new ArrayList<Expected>();
        var results = new ArrayList<ItemResult>();
        for (int i = 0; i < 100; i++) {
            Expected line = expected("p" + i,"s"+i,"12");
            hundred.add(line);
            results.add(ready(line,"SKU-"+i));
        }
        assertEquals(100, CatalogCheckoutBatchPreflight.requireFullyReady(hundred,
                new BatchResponse(VERSION,true,results)).size());
        hundred.add(expected("p100","s100","12"));
        assertThrows(IllegalArgumentException.class, () ->
                CatalogCheckoutBatchPreflight.requireFullyReady(hundred,
                        new BatchResponse(VERSION,true,results)));
    }

    @Test void rejectsPartialBatchMissingRowWrongVersionOrUnverifiedResponse() {
        Expected a=expected("pA","sA","12"), b=expected("pB","sB","13");
        denied(List.of(a,b), allReady(ready(a,"A")));
        denied(List.of(a,b), new BatchResponse("catalog.checkout.v0",true,
                List.of(ready(a,"A"),ready(b,"B"))));
        denied(List.of(a,b), null);
        denied(List.of(a,b), new BatchResponse(VERSION,false,
                List.of(ready(a,"A"),ready(b,"B"))));
    }

    @Test void rejectsNonReadyItemEvenIfProviderIncorrectlyClaimsAllReady() {
        Expected a=expected("pA","sA","12"), b=expected("pB","sB","13");
        denied(List.of(a,b),allReady(ready(a,"SKU-A"),
                new ItemResult("pB","INELIGIBLE",product(b,"SKU-B"))));
        denied(List.of(a,b),allReady(ready(a,"SKU-A"),
                new ItemResult("pB","READY",null)));
    }

    @Test void rejectsReorderedExtraDuplicatedAndMismatchedProductRows() {
        Expected a=expected("pA","sA","12"), b=expected("pB","sB","13");
        denied(List.of(a,b), allReady(ready(b,"SKU-B"),ready(a,"SKU-A")));
        denied(List.of(a,b), allReady(ready(a,"SKU-A"),ready(a,"SKU-B")));
        denied(List.of(a,b), new BatchResponse(VERSION,true,List.of(
                ready(a,"SKU-A"),ready(b,"SKU-B"),ready(a,"SKU-C"))));
    }

    @Test void rejectsPriceAndCurrencyMismatchButNoImplicitMoneyPolicyIsSelected() {
        Expected a=expected("pA","sA","12");
        VerifiedProduct changed=new VerifiedProduct("pA","SKU-A","sA","seller-sA",
                new BigDecimal("13"),"VND",true,true,true,false);
        denied(List.of(a),allReady(new ItemResult("pA","READY",changed)));
        VerifiedProduct wrongCurrency=new VerifiedProduct("pA","SKU-A","sA","seller-sA",
                new BigDecimal("12"),"USD",true,true,true,false);
        denied(List.of(a),allReady(new ItemResult("pA","READY",wrongCurrency)));
        var missingCurrency=new Expected("pA","sA","seller-sA",BigDecimal.TEN,null);
        denied(List.of(missingCurrency),allReady(ready(a,"SKU-A")));
        // BigDecimal comparison is exact by value, never through float/double.
        VerifiedProduct exactScaled = new VerifiedProduct("pA","SKU-A","sA","seller-sA",
                new BigDecimal("12.00"),"VND",true,true,true,false);
        assertEquals(1,CatalogCheckoutBatchPreflight.requireFullyReady(List.of(a),
                allReady(new ItemResult("pA","READY",exactScaled))).size());
    }

    @Test void rejectsIncorrectStoreSellerOrUnavailableSource() {
        Expected a=expected("pA","sA","12");
        var wrongStore=new VerifiedProduct("pA","SKU-A","other","seller-sA",
                new BigDecimal("12"),"VND",true,true,true,false);
        denied(List.of(a),allReady(new ItemResult("pA","READY",wrongStore)));
        var wrongSeller=new VerifiedProduct("pA","SKU-A","sA","other",
                new BigDecimal("12"),"VND",true,true,true,false);
        denied(List.of(a),allReady(new ItemResult("pA","READY",wrongSeller)));
        var closed=new VerifiedProduct("pA","SKU-A","sA","seller-sA",
                new BigDecimal("12"),"VND",true,true,false,false);
        denied(List.of(a),allReady(new ItemResult("pA","READY",closed)));
        var sellerSuspended=new VerifiedProduct("pA","SKU-A","sA","seller-sA",
                new BigDecimal("12"),"VND",true,false,true,false);
        denied(List.of(a),allReady(new ItemResult("pA","READY",sellerSuspended)));
        var unlisted=new VerifiedProduct("pA","SKU-A","sA","seller-sA",
                new BigDecimal("12"),"VND",false,true,true,false);
        denied(List.of(a),allReady(new ItemResult("pA","READY",unlisted)));
    }

    @Test void rejectsMissingOrDuplicatedTrustedSkuAcrossProducts() {
        Expected a=expected("pA","sA","12"),b=expected("pB","sB","13");
        denied(List.of(a,b),allReady(ready(a,"SAME"),ready(b,"SAME")));
        denied(List.of(a), allReady(ready(a,"")));
        denied(List.of(a), allReady(ready(a," sku A ")));
        denied(List.of(a), allReady(ready(a,"x".repeat(129))));
    }

    @Test void rejectsUnrepresentableOrderPriceBeforeAnyStockSideEffect() {
        Expected a=expected("pA","sA","1.001");
        assertThrows(IllegalArgumentException.class, () ->
                CatalogCheckoutBatchPreflight.requireFullyReady(List.of(a),allReady(ready(a,"SKU-A"))));
        var huge=expected("pA","sA","99999999999999999999999999");
        assertThrows(IllegalArgumentException.class, () ->
                CatalogCheckoutBatchPreflight.requireFullyReady(List.of(huge),allReady(ready(huge,"SKU-A"))));
    }

    @Test void doesNotDeriveSkuFromProductId() {
        Expected a=expected("product-uuid-1","store-1","1");
        var result=CatalogCheckoutBatchPreflight.requireFullyReady(List.of(a),
                allReady(ready(a,"product-owned-canonical-SKU")));
        assertEquals("product-owned-canonical-SKU",result.get(0).inventorySku());
        assertNotEquals(a.productId(),result.get(0).inventorySku());
    }
}
