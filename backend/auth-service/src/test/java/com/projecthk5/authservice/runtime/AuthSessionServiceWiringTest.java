package com.projecthk5.authservice.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.projecthk5.authservice.persistence.AccountPersistence;
import com.projecthk5.authservice.persistence.AccountRepository;
import com.projecthk5.authservice.persistence.RefreshFamilyRepository;
import com.projecthk5.authservice.persistence.RefreshTokenRepository;
import com.projecthk5.authservice.security.CurrentAccountAuthority;
import com.projecthk5.authservice.security.Hk5JwtCodec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Actual Spring injection boundary; previous pure unit construction missed the error. */
class AuthSessionServiceWiringTest {
    @Test
    void explicitHttpOptInSelectsProductionConstructor() {
        new ApplicationContextRunner()
                .withPropertyValues("hk5.auth.http.enabled=true")
                .withBean(AccountRepository.class, () -> mock(AccountRepository.class))
                .withBean(AccountPersistence.class, () -> mock(AccountPersistence.class))
                .withBean(RefreshFamilyRepository.class, () -> mock(RefreshFamilyRepository.class))
                .withBean(RefreshTokenRepository.class, () -> mock(RefreshTokenRepository.class))
                .withBean(CurrentAccountAuthority.class, () -> mock(CurrentAccountAuthority.class))
                .withBean(Hk5JwtCodec.class, () -> mock(Hk5JwtCodec.class))
                .withUserConfiguration(AuthSessionService.class)
                .run(context -> assertThat(context)
                        .hasNotFailed().hasSingleBean(AuthSessionService.class));
    }

    @Test
    void disabledHttpNeverCreatesServiceBean() {
        new ApplicationContextRunner()
                .withPropertyValues("hk5.auth.http.enabled=false")
                .withUserConfiguration(AuthSessionService.class)
                .run(context -> assertThat(context)
                        .hasNotFailed().doesNotHaveBean(AuthSessionService.class));
    }
}
