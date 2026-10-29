package com.projecthk5.productservice.security;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.projecthk5.security.CurrentAccountClient;
import com.projecthk5.productservice.product.*;
class CurrentAccountProductPolicyTest {
    @Test void doesNotReachStoreProviderWithoutSignedCurrentRoleAndSubject() {
        var account=mock(CurrentAccountClient.CurrentAccount.class);
        when(account.subject()).thenReturn("owner");when(account.roles()).thenReturn(Set.of("CUSTOMER"));
        var stores=mock(ProductAccessPolicy.class);var policy=new CurrentAccountProductPolicy(account,stores);
        assertThrows(ProductRuleException.class,()->policy.requireSellerManagement("owner",UUID.randomUUID()));
        assertThrows(ProductRuleException.class,()->policy.requireModeration("other",UUID.randomUUID()));
        verifyNoInteractions(stores);
        when(account.roles()).thenReturn(Set.of("SELLER"));UUID id=UUID.randomUUID();
        var facts=new ProductAccessPolicy.StoreAccess(id,UUID.randomUUID(),"owner",true,true,false);
        when(stores.requireSellerManagement("owner",id)).thenReturn(facts);
        assertEquals(facts,policy.requireSellerManagement("owner",id));
        assertThrows(ProductRuleException.class,()->policy.requireModeration("owner",id));
    }
}
