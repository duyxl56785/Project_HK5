package com.projecthk5.sellerservice.security;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.projecthk5.security.CurrentAccountClient;
import com.projecthk5.sellerservice.seller.domain.*;
class CurrentAccountSellerPolicyTest {
    @Test void checksExactSubjectAndCurrentAdminRole() {
        var account=mock(CurrentAccountClient.CurrentAccount.class);
        when(account.subject()).thenReturn("owner");when(account.roles()).thenReturn(Set.of("CUSTOMER"));
        var policy=new CurrentAccountSellerPolicy(account);
        policy.requireAuthenticated(new SellerIdentity("owner"));
        assertThrows(SellerRuleException.class,()->policy.requireAuthenticated(new SellerIdentity("other")));
        assertThrows(SellerRuleException.class,()->policy.requireSellerModerationPermission(new SellerIdentity("owner")));
        when(account.roles()).thenReturn(Set.of("ADMIN"));
        policy.requireSellerModerationPermission(new SellerIdentity("owner"));
    }
}
