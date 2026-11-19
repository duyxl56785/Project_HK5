package com.projecthk5.sellerservice.voucher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.projecthk5.security.ApiException;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class VoucherRedemptionFeatureFlagTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(VoucherRedemptionService.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(VoucherService.class, () -> mock(VoucherService.class))
            .withBean("voucherClock", Clock.class, Clock::systemUTC);

    @Test
    void newQuoteAndRedeemAreBlockedByDefaultWhileLedgerReadsRemainAvailable() {
        context.run(started -> verifyDisabled(started.getBean(VoucherRedemptionService.class)));
        context.withPropertyValues("hk5.voucher-c2.enabled=false")
                .run(started -> verifyDisabled(started.getBean(VoucherRedemptionService.class)));
    }

    @Test
    void serviceRequiresTheSeparateVoucherFlag() {
        context.withPropertyValues("hk5.voucher-c2.enabled=true")
                .run(started -> {
                    var service = started.getBean(VoucherRedemptionService.class);
                    assertThat(assertThrows(ApiException.class,
                            () -> service.quote(UUID.randomUUID(), "customer", "", 200)).code())
                            .isEqualTo("VALIDATION_FAILED");
                });
    }

    private void verifyDisabled(VoucherRedemptionService service) {
        var store = UUID.randomUUID();
        var order = UUID.randomUUID();
        var request = new VoucherRedemptionService.Redeem(order, store, "customer", UUID.randomUUID(), 1, 200, 100);
        assertThat(assertThrows(ApiException.class, () -> service.quote(store, "customer", "CODE", 200)).code())
                .isEqualTo("VOUCHER_C2_DISABLED");
        assertThat(assertThrows(ApiException.class, () -> service.redeem(request)).code()).isEqualTo("VOUCHER_C2_DISABLED");
        assertThat(assertThrows(ApiException.class, () -> service.status(store, order)).code())
                .isEqualTo("VOUCHER_REDEMPTION_NOT_FOUND");
    }
}
