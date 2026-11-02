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
}
