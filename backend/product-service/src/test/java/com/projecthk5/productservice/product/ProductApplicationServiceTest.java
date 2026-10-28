package com.projecthk5.productservice.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projecthk5.productservice.category.CategoryRepository;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class ProductApplicationServiceTest {
    @ParameterizedTest
    @ValueSource(strings = {"default", "null-scope", "denied", "unavailable", "null-facts", "mismatched", "foreign-owner", "unapproved", "closed"})
    void independentCurrentScopeMustPassBeforeAnyOwnerProductLookup(String mode) {
        var persistence = mock(ProductPersistenceAdapter.class);
        var categories = mock(CategoryRepository.class);
        var policy = mock(ProductAccessPolicy.class, CALLS_REAL_METHODS);
        UUID store = UUID.randomUUID(), target = UUID.randomUUID(), category = UUID.randomUUID();
        if (!mode.equals("default")) doReturn(mode.equals("null-scope") ? null : store).when(policy).currentManagementStore("owner");
        if (mode.equals("denied") || mode.equals("unavailable")) {
            doThrow(new ProductRuleException(mode.equals("denied") ? ProductRuleException.Code.ACCESS_DENIED
                    : ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE, "Fixture scope refused"))
                    .when(policy).currentManagementStore("owner");
        }
        var facts = mode.equals("null-facts") ? null : new ProductAccessPolicy.StoreAccess(
                mode.equals("mismatched") ? UUID.randomUUID() : store, UUID.randomUUID(),
                mode.equals("foreign-owner") ? "someone-else" : "owner", !mode.equals("unapproved"), !mode.equals("closed"), false);
        doReturn(facts).when(policy).requireSellerManagement("owner", store);
        var factory = new StaticListableBeanFactory(); factory.addBean("policy", policy);
        var service = new ProductApplicationService(persistence, categories, factory.getBeanProvider(ProductAccessPolicy.class));
        var expected = List.of("denied", "foreign-owner", "unapproved", "closed").contains(mode)
                ? ProductRuleException.Code.ACCESS_DENIED : ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE;
        for (Runnable operation : List.<Runnable>of(() -> service.findById("owner", target),
                () -> service.edit("owner", target, 0, category, "Name", BigDecimal.ONE, "VND"),
                () -> service.submit("owner", target, 0), () -> service.deactivate("owner", target, 0))) {
            assertEquals(expected, assertThrows(ProductRuleException.class, operation::run).getCode());
        }
        verifyNoInteractions(persistence, categories);
    }

    @Test void currentStoreAndFreshManagementFactsAreVerifiedBeforeMissingProductLookup() {
        var persistence = mock(ProductPersistenceAdapter.class);
        var categories = mock(CategoryRepository.class);
        var policy = mock(ProductAccessPolicy.class);
        UUID store = UUID.randomUUID(), target = UUID.randomUUID();
        when(policy.currentManagementStore("owner")).thenReturn(store);
        when(policy.requireSellerManagement("owner", store)).thenReturn(
                new ProductAccessPolicy.StoreAccess(store, UUID.randomUUID(), "owner", true, true, false));
        var factory = new StaticListableBeanFactory(); factory.addBean("policy", policy);
        var service = new ProductApplicationService(persistence, categories, factory.getBeanProvider(ProductAccessPolicy.class));
        assertEquals(com.projecthk5.productservice.product.persistence.ProductPersistenceException.Code.NOT_FOUND,
                assertThrows(com.projecthk5.productservice.product.persistence.ProductPersistenceException.class,
                        () -> service.findById("owner", target)).getCode());
        var order = inOrder(policy, persistence);
        order.verify(policy).currentManagementStore("owner");
        order.verify(policy).requireSellerManagement("owner", store);
        order.verify(persistence).findFreshById(target);
        verifyNoInteractions(categories);
    }

    @Test
    void ambiguousPoliciesFailClosedInsteadOfSelectingAnAuthority() {
        ProductPersistenceAdapter persistence = mock(ProductPersistenceAdapter.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        var factory = new StaticListableBeanFactory();
        factory.addBean("first", mock(ProductAccessPolicy.class));
        factory.addBean("second", mock(ProductAccessPolicy.class));
        var service = new ProductApplicationService(persistence, categories, factory.getBeanProvider(ProductAccessPolicy.class));
        assertEquals(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                assertThrows(ProductRuleException.class, () -> service.findById("owner", UUID.randomUUID())).getCode());
        verifyNoInteractions(persistence, categories);
    }

    @Test
    void invalidActorsCannotReachAuthorityOrPersistence() {
        ProductPersistenceAdapter persistence = mock(ProductPersistenceAdapter.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<ProductAccessPolicy> policies = mock(ObjectProvider.class);
        var service = new ProductApplicationService(persistence, categories, policies);
        for (String actor : new String[]{null, "", " ", "x".repeat(129)}) {
            assertEquals(ProductRuleException.Code.INVALID_INPUT,
                    assertThrows(ProductRuleException.class, () -> service.findById(actor, UUID.randomUUID())).getCode());
        }
        verifyNoInteractions(policies, persistence, categories);
    }

    @Test
    void missingProductionAuthorityRejectsEveryOperationBeforeLookupOrWrite() {
        ProductPersistenceAdapter persistence = mock(ProductPersistenceAdapter.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        var policies = new StaticListableBeanFactory().getBeanProvider(ProductAccessPolicy.class);
        ProductApplicationService service = new ProductApplicationService(persistence, categories, policies);
        UUID id = UUID.randomUUID(), store = UUID.randomUUID(), category = UUID.randomUUID();
        for (Runnable operation : List.<Runnable>of(
                () -> service.create("owner", store, category, "SKU", "Name", BigDecimal.ONE, "VND"),
                () -> service.edit("owner", id, 0, category, "Name", BigDecimal.ONE, "VND"),
                () -> service.submit("owner", id, 0), () -> service.approve("admin", id, 0),
                () -> service.reject("admin", id, 0, "Reason"), () -> service.suspend("admin", id, 0, "Reason"),
                () -> service.deactivate("owner", id, 0), () -> service.findById("owner", id))) {
            assertEquals(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                    assertThrows(ProductRuleException.class, operation::run).getCode());
        }
        verifyNoInteractions(persistence, categories);
    }
}
