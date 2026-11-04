package com.projecthk5.orderservice.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

class CheckoutDraftRetentionConfigurationTest {

    @Test
    void purgeSchedulerIsDisabledUnlessOperationsOptIn() {
        try (var context = context(Map.of())) {
            assertFalse(context.containsBean("checkoutDraftRetention"));
        }
        try (var context = context(Map.of("app.checkout.draft-purge-enabled", "true"))) {
            assertTrue(context.containsBean("checkoutDraftRetention"));
        }
    }

    private AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        var context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
        var resolved = new HashMap<String, Object>();
        resolved.put("app.checkout.draft-retention", "P7D");
        resolved.put("app.checkout.draft-purge-batch", "500");
        resolved.putAll(properties);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", resolved));
        context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
        context.register(CheckoutDraftRetention.class);
        context.refresh();
        return context;
    }
}
