package com.projecthk5.productservice.image;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projecthk5.productservice.image.persistence.ProductImageMetadataRepository;
import com.projecthk5.productservice.product.ProductAccessPolicy;
import com.projecthk5.productservice.product.ProductRuleException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class ProductImageMetadataServiceTest {
    @ParameterizedTest
    @ValueSource(strings = {"absent", "ambiguous", "default", "null-scope", "null-facts", "wrong-store", "wrong-owner", "closed", "unapproved"})
    void authorityMustSucceedBeforeAnyDatabaseLookup(String mode) {
        var repository = mock(ProductImageMetadataRepository.class);
        var em = mock(EntityManager.class);
        var factory = new StaticListableBeanFactory();
        var policy = mock(ProductAccessPolicy.class, CALLS_REAL_METHODS);
        UUID store = UUID.randomUUID();
        if (!mode.equals("absent")) factory.addBean("policy", policy);
        if (mode.equals("ambiguous")) factory.addBean("other", mock(ProductAccessPolicy.class));
        if (!mode.equals("default")) doReturn(mode.equals("null-scope") ? null : store).when(policy).currentManagementStore("owner");
        doReturn(mode.equals("null-facts") ? null : new ProductAccessPolicy.StoreAccess(
                mode.equals("wrong-store") ? UUID.randomUUID() : store, UUID.randomUUID(),
                mode.equals("wrong-owner") ? "someone-else" : "owner", !mode.equals("unapproved"), !mode.equals("closed"), false))
                .when(policy).requireSellerManagement("owner", store);
        var service = new ProductImageMetadataService(repository, em, factory.getBeanProvider(ProductAccessPolicy.class));
        var expected = List.of("wrong-owner", "closed", "unapproved").contains(mode)
                ? ProductRuleException.Code.ACCESS_DENIED : ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE;
        assertEquals(expected, assertThrows(ProductRuleException.class,
                () -> service.list("owner", UUID.randomUUID())).getCode());
        verifyNoInteractions(repository, em);
    }

    @Test void invalidActorDoesNotReachAuthorityOrDatabase() {
        var repository = mock(ProductImageMetadataRepository.class);
        var em = mock(EntityManager.class);
        var policy = mock(ProductAccessPolicy.class);
        var factory = new StaticListableBeanFactory(); factory.addBean("policy", policy);
        var service = new ProductImageMetadataService(repository, em, factory.getBeanProvider(ProductAccessPolicy.class));
        for (String actor : new String[] {null, "", " ", "x".repeat(129)}) {
            assertEquals(ProductRuleException.Code.INVALID_INPUT, assertThrows(ProductRuleException.class,
                    () -> service.list(actor, UUID.randomUUID())).getCode());
        }
        verifyNoInteractions(policy, repository, em);
    }
}
