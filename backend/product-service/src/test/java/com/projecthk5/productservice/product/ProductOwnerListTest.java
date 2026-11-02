package com.projecthk5.productservice.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.projecthk5.productservice.category.CategoryRepository;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class ProductOwnerListTest {
    private final ProductPersistenceAdapter persistence=mock(ProductPersistenceAdapter.class);
    private final CategoryRepository categories=mock(CategoryRepository.class);
    private final StaticListableBeanFactory factory=new StaticListableBeanFactory();
    private ProductApplicationService service() {
        return new ProductApplicationService(persistence,categories,factory.getBeanProvider(ProductAccessPolicy.class));
    }
    private ProductOwnerQuery query() {return new ProductOwnerQuery(null,null,0,10);}
    @Test void missingAndAmbiguousPoliciesRejectBeforeResourceAccess() {
        assertEquals(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,assertThrows(ProductRuleException.class,()->service().listOwned("owner",query())).getCode());
        factory.addBean("first",mock(ProductAccessPolicy.class));factory.addBean("second",mock(ProductAccessPolicy.class));
        assertEquals(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,assertThrows(ProductRuleException.class,()->service().listOwned("owner",query())).getCode());
        verifyNoInteractions(persistence,categories);
    }
    @ParameterizedTest @ValueSource(strings={"null-scope","null-facts","wrong-scope","other-owner","closed","unapproved"})
    void scopeMustBeIndependentlyVerifiedBeforeAnyRead(String mode) {
        UUID store=UUID.randomUUID();var policy=mock(ProductAccessPolicy.class);factory.addBean("policy",policy);
        when(policy.currentManagementStore("owner")).thenReturn(mode.equals("null-scope")?null:store);
        if(!mode.equals("null-facts"))when(policy.requireSellerManagement("owner",store)).thenReturn(
                new ProductAccessPolicy.StoreAccess(mode.equals("wrong-scope")?UUID.randomUUID():store,UUID.randomUUID(),
                        mode.equals("other-owner")?"foreign":"owner",!mode.equals("unapproved"),!mode.equals("closed"),false));
        var expected=List.of("null-scope","null-facts","wrong-scope").contains(mode)?ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE:ProductRuleException.Code.ACCESS_DENIED;
        assertEquals(expected,assertThrows(ProductRuleException.class,()->service().listOwned("owner",query())).getCode());
        verifyNoInteractions(persistence,categories);
    }
    @Test void verifiedScopeAndCriteriaReachPersistenceInOrder() {
        UUID store=UUID.randomUUID();var policy=mock(ProductAccessPolicy.class);factory.addBean("policy",policy);
        when(policy.currentManagementStore("owner")).thenReturn(store);
        when(policy.requireSellerManagement("owner",store)).thenReturn(new ProductAccessPolicy.StoreAccess(store,UUID.randomUUID(),"owner",true,true,false));
        var filter=query();var page=new ProductOwnerPage(List.of(),0,0,10);when(persistence.listOwned(store,filter)).thenReturn(page);
        assertSame(page,service().listOwned("owner",filter));
        var order=inOrder(policy,persistence);order.verify(policy).currentManagementStore("owner");
        order.verify(policy).requireSellerManagement("owner",store);order.verify(persistence).listOwned(store,filter);verifyNoInteractions(categories);
    }
    @Test void invalidActorsCannotReachAuthorityOrResources() {
        var policy=mock(ProductAccessPolicy.class);factory.addBean("policy",policy);
        for(String actor:new String[]{null,""," ","x".repeat(129)})
            assertEquals(ProductRuleException.Code.INVALID_INPUT,assertThrows(ProductRuleException.class,()->service().listOwned(actor,query())).getCode());
        verifyNoInteractions(policy,persistence,categories);
    }
    @Test void criteriaBoundOffsetAndRejectInvalidTextWithoutRewritingIdentity() {
        assertEquals(Integer.MAX_VALUE,new ProductOwnerQuery(null,null,Integer.MAX_VALUE,1).offset());
        assertEquals("Áo_É",new ProductOwnerQuery("  Áo_É  ",ProductStatus.REJECTED,1,100).term());
        assertNull(new ProductOwnerQuery(" \t",null,0,1).term());
        for(int[] pair:new int[][]{{-1,10},{0,0},{0,101},{Integer.MAX_VALUE,2}})
            assertThrows(ProductRuleException.class,()->new ProductOwnerQuery(null,null,pair[0],pair[1]));
        for(String term:new String[]{"x".repeat(256),"a\0b","\ud800","\udc00"})
            assertThrows(ProductRuleException.class,()->new ProductOwnerQuery(term,null,0,10));
    }
}
