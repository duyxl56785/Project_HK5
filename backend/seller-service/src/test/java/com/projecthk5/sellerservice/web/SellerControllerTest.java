package com.projecthk5.sellerservice.web;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import org.junit.jupiter.api.Test;

class SellerControllerTest {
    @Test void onlyApprovedAndSuspendedSellersHoldTheRole() {
        for (SellerStatus status : SellerStatus.values()) {
            assertEquals(status == SellerStatus.APPROVED || status == SellerStatus.SUSPENDED,
                    SellerController.keepsSellerRole(status), status.name());
        }
    }

    @Test void adminSeesOnlyTheLastFourDigitsOfIdAndBankAccount() {
        assertEquals("********7890", SellerController.mask("001234567890"));
        assertEquals("******6789", SellerController.mask("0123456789"));
        assertEquals("****", SellerController.mask("1234"));
        assertNull(SellerController.mask(null));
    }
}
