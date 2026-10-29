package com.projecthk5.authservice.rolesync;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import jakarta.servlet.Filter;

class SellerRoleSyncHttpTest {
    static final String PATH = "/internal/auth/seller-role-sync";
    static final String KEY = "test-only-role-sync-credential-0123456789";
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    static SellerRoleSyncService service;
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SellerRoleSyncHttpConfiguration.class, SellerRoleSyncHttpConfiguration.Receiver.class})
    static class Host {

    }
    void host(boolean enabled, String key) {
        service = mock(SellerRoleSyncService.class);
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
            "hk5.auth.role-sync.enabled="+enabled, "HK5_SELLER_AUTH_ROLE_SYNC_SECRET="+key);
        context.addBeanFactoryPostProcessor(factory->{ if(factory.containsBeanDefinition("sellerRoleSyncService")) ((org.springframework.beans.factory.support.BeanDefinitionRegistry)factory).removeBeanDefinition("sellerRoleSyncService"); factory.registerSingleton("testedService",service); });
        context.register(Host.class); context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }
    @AfterEach void close() {if(context!=null)context.close();}
    String payload() {return "{\"eventId\":\""+UUID.randomUUID()+"\",\"sellerId\":\""+UUID.randomUUID()
        +"\",\"accountId\":\""+UUID.randomUUID()+"\",\"sellerStatus\":\"APPROVED\",\"sourceVersion\":\"1\"}";}
    @Test void defaultOffDeniesEvenCorrectCredential() throws Exception {
        host(false,KEY); mvc.perform(post(PATH).header("X-HK5-Service-Credential",KEY)
            .contentType("application/json").content(payload())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void authenticatesBeforeParsingAndRejectsDuplicateCredentials() throws Exception {
        host(true,KEY);
        mvc.perform(post(PATH).contentType("application/json").content("invalid")) .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("X-HK5-Service-Credential",KEY,KEY).contentType("application/json").content(payload()))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("X-HK5-Service-Credential","wrong").content(payload())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void strictPayloadAndScopeCannotReachService() throws Exception {
        host(true,KEY);
        mvc.perform(post(PATH).header("X-HK5-Service-Credential",KEY).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get(PATH).header("X-HK5-Service-Credential",KEY)).andExpect(status().isForbidden());
        mvc.perform(post(PATH+"/other").header("X-HK5-Service-Credential",KEY)).andExpect(status().isForbidden());
        mvc.perform(post(PATH).header("X-HK5-Service-Credential",KEY).contentType("text/plain").content(payload()))
            .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(service);
    }
    @Test void commitsBeforeAckAndSanitizesFailure() throws Exception {
        host(true,KEY); when(service.apply(any())).thenReturn(SellerRoleSyncService.Result.APPLIED);
        mvc.perform(post(PATH).header("X-HK5-Service-Credential",KEY).contentType("application/json").content(payload()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("APPLIED"))
            .andExpect(header().string("Cache-Control","no-store"));
        when(service.apply(any())).thenThrow(new IllegalStateException("private-db-password"));
        mvc.perform(post(PATH).header("X-HK5-Service-Credential",KEY).contentType("application/json").content(payload()))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ROLE_SYNC_UNAVAILABLE"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-db-password"))));
    }
    @Test void unavailableCredentialSourceFailsClosed() throws Exception {
        host(true,""); mvc.perform(post(PATH).header("X-HK5-Service-Credential",KEY).content(payload()))
            .andExpect(status().isServiceUnavailable()); verifyNoInteractions(service);
    }
    @Test void boundsBodyAndRejectsMalformedUtf8AndDuplicateJson() throws Exception {
        host(true,KEY);
        for(byte[] body:List.of("x".repeat(16385).getBytes(java.nio.charset.StandardCharsets.UTF_8),new byte[]{(byte)0xc3,0x28},
                payload().replace("\"sourceVersion\":\"1\"","\"sourceVersion\":\"1\",\"sourceVersion\":\"2\"").getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            mvc.perform(post(PATH).header("X-HK5-Service-Credential",KEY).contentType("application/json").content(body))
                .andExpect(status().is(body.length>16384?413:400));
        }
        verifyNoInteractions(service);
    }
    @Test void previousCredentialOnlyWorksBeforeExplicitDeadline() throws Exception {
        host(true,KEY); String previous="old-test-only-role-sync-credential-0123456789";
        var properties=new HashMap<String,Object>();
        properties.put("HK5_SELLER_AUTH_ROLE_SYNC_PREVIOUS_SECRET",previous);
        properties.put("HK5_SELLER_AUTH_ROLE_SYNC_PREVIOUS_UNTIL",java.time.Instant.now().plusSeconds(60).toString());
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("rotation",properties));
        when(service.apply(any())).thenReturn(SellerRoleSyncService.Result.DUPLICATE);
        mvc.perform(post(PATH).header("X-HK5-Service-Credential",previous).contentType("application/json").content(payload()))
            .andExpect(status().isOk());
        properties.put("HK5_SELLER_AUTH_ROLE_SYNC_PREVIOUS_UNTIL",java.time.Instant.now().minusSeconds(1).toString());
        mvc.perform(post(PATH).header("X-HK5-Service-Credential",previous).contentType("application/json").content(payload()))
            .andExpect(status().isUnauthorized());
        verify(service,times(1)).apply(any());
    }    @Test void crossPairAliasesCannotReachRoleMutation() throws Exception {
        host(true,KEY);when(service.apply(any())).thenReturn(SellerRoleSyncService.Result.APPLIED);
        String previous="old-test-only-role-sync-credential-0123456789";
        for(String foreign:List.of("HK5_SELLER_AUTH_CURRENT_ACCOUNT_","HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_"))
        for(String own:List.of(KEY,previous))for(String slot:List.of("SECRET","PREVIOUS_SECRET")) {
            var values=new HashMap<String,Object>();values.put("HK5_SELLER_AUTH_ROLE_SYNC_PREVIOUS_SECRET",previous);
            values.put("HK5_SELLER_AUTH_ROLE_SYNC_PREVIOUS_UNTIL",java.time.Instant.now().plusSeconds(60).toString());
            values.put(foreign+slot,own);values.put(foreign+"PREVIOUS_UNTIL",java.time.Instant.now().plusSeconds(60).toString());
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("cross-alias",values));
            try {mvc.perform(post(PATH).header("X-HK5-Service-Credential",own).contentType("application/json").content(payload()))
                .andExpect(status().isServiceUnavailable());verifyNoInteractions(service);}
            finally{context.getEnvironment().getPropertySources().remove("cross-alias");}
        }
    }
}
