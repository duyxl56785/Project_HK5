package com.projecthk5.productservice.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projecthk5.productservice.category.CategoryRepository;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class ProductApplicationServiceTest {
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
