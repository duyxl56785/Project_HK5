package com.projecthk5.orderservice.finance;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.orderservice.domain.MoneyRules;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** Configuration/consumer regression only: fixture approval is never production sign-off. */
class FinancePolicyConfigurationTest {
    private ApplicationContextRunner context(Map<String, Object> values) {
        return new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("finance-fixture-env", values)))
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(MoneyRules.class);
    }

    @Test void realYamlDefaultsClosedEvenWithAllPricingInputs() {
        context(Map.of("HK5_FINANCE_PLATFORM_RATE", "0.05",
            "HK5_FINANCE_CURRENCY_SCALE", "2", "HK5_FINANCE_HOUSE_EXEMPT", "true"))
            .run(ctx -> {
                assertNull(ctx.getStartupFailure());
                var error = assertThrows(IllegalStateException.class,
                    () -> ctx.getBean(MoneyRules.class).fee(new BigDecimal("10.00"), false));
                assertEquals("FINANCE_POLICY_NOT_APPROVED", error.getMessage());
            });
    }

    @Test void fixtureApprovalWithoutCompleteInputsFailsStartup() {
        context(Map.of("HK5_FINANCE_POLICY_APPROVED", "true"))
            .run(ctx -> assertNotNull(ctx.getStartupFailure()));
    }

    @Test void explicitFixtureEnvironmentBindsRateScaleAndHouseExemptionThroughYaml() {
        context(Map.of("HK5_FINANCE_POLICY_APPROVED", "true",
            "HK5_FINANCE_PLATFORM_RATE", "0.05", "HK5_FINANCE_CURRENCY_SCALE", "2",
            "HK5_FINANCE_HOUSE_EXEMPT", "true")).run(ctx -> {
                assertNull(ctx.getStartupFailure());
                var rules = ctx.getBean(MoneyRules.class);
                assertEquals(new BigDecimal("0.01"), rules.fee(new BigDecimal("0.10"), false));
                assertEquals(new BigDecimal("0.00"), rules.fee(new BigDecimal("0.10"), true));
                assertThrows(ArithmeticException.class,
                    () -> rules.fee(new BigDecimal("0.101"), false));
            });
    }

    @Test void malformedFixturePolicyCannotCreateReadyMoneyRules() {
        for (var entry : Map.of("HK5_FINANCE_PLATFORM_RATE", "1.01",
                "HK5_FINANCE_CURRENCY_SCALE", "5", "HK5_FINANCE_HOUSE_EXEMPT", "TRUE").entrySet()) {
            var values = new java.util.HashMap<String, Object>(Map.of(
                "HK5_FINANCE_POLICY_APPROVED", "true", "HK5_FINANCE_PLATFORM_RATE", "0.05",
                "HK5_FINANCE_CURRENCY_SCALE", "2", "HK5_FINANCE_HOUSE_EXEMPT", "true"));
            values.put(entry.getKey(), entry.getValue());
            context(values).run(ctx -> assertNotNull(ctx.getStartupFailure(), entry.getKey()));
        }
    }
}
