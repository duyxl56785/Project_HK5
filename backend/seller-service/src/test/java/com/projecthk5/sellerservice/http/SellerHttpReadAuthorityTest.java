package com.projecthk5.sellerservice.http;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.net.*;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import com.projecthk5.security.*;
import com.projecthk5.sellerservice.seller.domain.*;
import com.projecthk5.sellerservice.seller.service.*;
import com.projecthk5.sellerservice.store.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

class SellerHttpReadAuthorityTest {
    HttpServer auth;AnnotationConfigWebApplicationContext context;MockMvc mvc;String token;String subject=UUID.randomUUID().toString();
    Set<String> roles=Set.of("CUSTOMER","SELLER");int authStatus=200;SellerPersistenceService profiles;StorePersistenceService stores;
    @Configuration @EnableWebMvc @EnableWebSecurity @Import({SellerHttpReadConfiguration.class,SellerHttpReadConfiguration.Reader.class}) static class Host{}
    @BeforeEach void start() throws Exception {
        ObjectMapper json=new ObjectMapper();byte[] key=new byte[32];var now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).keyID("test").build(),new JWTClaimsSet.Builder()
            .issuer("https://issuer.example.invalid").audience("aud").subject(subject).jwtID("read-jti").issueTime(Date.from(now)).notBeforeTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(900))).claim("roles",List.of("CUSTOMER","SELLER")).build());jwt.sign(new MACSigner(key));token=jwt.serialize();
        auth=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);auth.createContext("/internal/auth/current-account/seller",exchange->{
            var request=json.readTree(exchange.getRequestBody());byte[] body=json.writeValueAsBytes(Map.of("requestId",request.get("requestId").textValue(),"subject",subject,"tokenId","read-jti","effectiveRoles",roles));
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(authStatus,body.length);exchange.getResponseBody().write(body);exchange.close();});auth.start();
        profiles=mock(SellerPersistenceService.class);stores=mock(StorePersistenceService.class);
        when(profiles.findOwn(any())).thenAnswer(call->new SellerPersistenceService.PersistedSeller(UUID.fromString("11111111-1111-1111-1111-111111111111"),1,
            new SellerApplication(new SellerIdentity(subject),SellerStatus.PENDING_REVIEW,null)));
        when(stores.findOwn(any())).thenAnswer(call->new StorePersistenceService.PersistedStore(new StoreProfile(UUID.randomUUID(),UUID.randomUUID(),subject,"Own","own",false),2,Instant.EPOCH,Instant.EPOCH));
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,"hk5.seller.http.read.enabled=true","HK5_AUTH_CURRENT_ACCOUNT_URL=http://127.0.0.1:"+auth.getAddress().getPort()+"/internal/auth/current-account/seller",
            "HK5_SELLER_AUTH_CURRENT_ACCOUNT_SECRET=test-only-read-current-pair-credential-0123456789",
            "HK5_AUTH_JWT_ISSUER=https://issuer.example.invalid","HK5_AUTH_JWT_AUDIENCE=aud","HK5_AUTH_JWT_CURRENT_KID=test","HK5_AUTH_JWT_CURRENT_KEY_B64="+Base64.getEncoder().encodeToString(key));
        context.register(Host.class);context.addBeanFactoryPostProcessor(factory->{
            var registry=(org.springframework.beans.factory.support.BeanDefinitionRegistry)factory;
            for(String name:List.of("sellerHttpProfiles","sellerHttpStores"))if(registry.containsBeanDefinition(name))registry.removeBeanDefinition(name);
            factory.registerSingleton("testedProfiles",profiles);factory.registerSingleton("testedStores",stores);
        });context.refresh();mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",jakarta.servlet.Filter.class)).build();
    }
    @AfterEach void close(){if(context!=null)context.close();if(auth!=null)auth.stop(0);}
    @Test void signedCurrentIdentityCanReadOwnProfileWithoutSellerRole() throws Exception {
        roles=Set.of("CUSTOMER");mvc.perform(get("/api/sellers/me").header("Authorization","Bearer "+token).header("X-User-Id","forged"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_REVIEW")).andExpect(jsonPath("$.ownerReference").doesNotExist());
        verify(profiles).findOwn(new SellerIdentity(subject));
        mvc.perform(get("/api/stores/me").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());verifyNoInteractions(stores);
    }
    @Test void storeReadPreservesClosedStateAndVersionButRejectsForgedIdentity() throws Exception {
        mvc.perform(get("/api/stores/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.open").value(false)).andExpect(jsonPath("$.version").value("2"));
        clearInvocations(profiles,stores);mvc.perform(get("/api/stores/me").header("X-User-Id",subject).header("X-Roles","ADMIN,SELLER")).andExpect(status().isUnauthorized());verifyNoInteractions(profiles,stores);
    }
    @Test void invalidJwtDependencyFailureAndAllWritesStayDenied() throws Exception {
        mvc.perform(get("/api/sellers/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        authStatus=503;mvc.perform(get("/api/sellers/me").header("Authorization","Bearer "+token)).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/sellers").header("Authorization","Bearer "+token).contentType("application/json").content("{}")) .andExpect(status().isForbidden());verifyNoInteractions(profiles,stores);
    }
}
