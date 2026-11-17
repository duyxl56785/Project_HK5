package com.projecthk5.sellerservice.voucher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
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
    void serviceIsAbsentByDefaultAndWhenExplicitlyDisabled() {
        context.run(started -> assertThat(started).hasNotFailed().doesNotHaveBean(VoucherRedemptionService.class));
        context.withPropertyValues("hk5.voucher-c2.enabled=false")
                .run(started -> assertThat(started).hasNotFailed().doesNotHaveBean(VoucherRedemptionService.class));
    }

    @Test
    void serviceRequiresTheSeparateVoucherFlag() {
        context.withPropertyValues("hk5.voucher-c2.enabled=true")
                .run(started -> assertThat(started).hasNotFailed().hasSingleBean(VoucherRedemptionService.class));
    }
}
