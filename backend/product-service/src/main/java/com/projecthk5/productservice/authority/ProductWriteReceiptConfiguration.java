package com.projecthk5.productservice.authority;

import com.projecthk5.security.*;
import com.projecthk5.security.fencing.*;
import com.projecthk5.security.fencing.DurableAuthorityReservations.Participant;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name="hk5.product.receipts.enabled",havingValue="true")
public class ProductWriteReceiptConfiguration {
    public static final String AUTH_PATH="/internal/product/write-receipts/auth";
    public static final String SELLER_PATH="/internal/product/write-receipts/seller";

    @Bean @Order(-100)
    SecurityFilterChain productWriteReceiptChain(HttpSecurity http,Environment env,DataSource source) throws Exception {
        var receipts=new ProductTerminalReceiptSource(source);
        var auth=verifier(env,"auth-service",AUTH_PATH,"HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_");
        var seller=verifier(env,"seller-service",SELLER_PATH,"HK5_PRODUCT_SELLER_STORE_FACTS_");
        return http.securityMatcher("/internal/product/write-receipts/**")
            .csrf(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.anyRequest().denyAll())
            .addFilterBefore((ServletRequest raw,ServletResponse out,FilterChain chain)->{
                var request=(HttpServletRequest)raw;var response=(HttpServletResponse)out;
                response.setHeader("Cache-Control","no-store");
                var credentials=Collections.list(request.getHeaders("X-HK5-Service-Credential"));
                if(credentials.size()!=1 || credentials.getFirst().contains(",")){error(response,401,"INTERNAL_AUTH_INVALID");return;}
                String path=request.getRequestURI();
                // Unknown paths still verify the pair before rejecting its scope; no body or database access.
                var participant=SELLER_PATH.equals(path)?Participant.SELLER:Participant.AUTH;
                try {(participant==Participant.AUTH?auth:seller).verify(credentials.getFirst(),request.getMethod(),path);}
                catch(InternalPairVerifier.Failure failure){
                    if(!AUTH_PATH.equals(path) && !SELLER_PATH.equals(path) && failure.status()==401) {
                        try {seller.verify(credentials.getFirst(),request.getMethod(),path);}
                        catch(InternalPairVerifier.Failure other){error(response,other.status(),other.code());return;}
                    }
                    error(response,failure.status(),failure.code());return;
                }
                try {
                    var type=org.springframework.http.MediaType.parseMediaType(Objects.requireNonNull(request.getContentType()));
                    if(!"application".equalsIgnoreCase(type.getType()) || !"json".equalsIgnoreCase(type.getSubtype()))throw new IllegalArgumentException();
                } catch(Exception unsupported){error(response,415,"UNSUPPORTED_MEDIA_TYPE");return;}
                ReservationWire.Request envelope;
                try {
                    byte[] body=request.getInputStream().readNBytes(16385);
                    if(body.length>16384){error(response,413,"VALIDATION_FAILED");return;}
                    envelope=ReservationWire.readRequest("receipt",body);
                } catch(IllegalArgumentException invalid){error(response,400,"VALIDATION_FAILED");return;}
                try {
                    var receipt=receipts.terminal(envelope.intent().requestId());
                    if(!receipt.bindingHash().equals(envelope.intent().fingerprint())
                        || !Objects.equals(receipt.grants().get(participant),envelope.epoch()))
                        throw new IllegalStateException();
                    String result=ReservationWire.receiptReply(envelope.nonce(),envelope.intent(),participant,envelope.epoch(),receipt);
                    response.setStatus(200);response.setContentType("application/json");response.getWriter().write(result);
                } catch(Exception unavailable){error(response,503,"TERMINAL_RECEIPT_UNAVAILABLE");}
            },AnonymousAuthenticationFilter.class).build();
    }
    private static InternalPairVerifier verifier(Environment env,String caller,String path,String prefix) {
        return new InternalPairVerifier(caller,"product-service",Set.of(new InternalPairVerifier.Scope(
            "POST",path,"AUTHORITY_TERMINAL_RECEIPT_READ")),()->IsolatedPairCredentials.read(env::getProperty,prefix),Clock.systemUTC());
    }
    private static void error(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status);response.setContentType("application/json");
        response.getWriter().write("{\"code\":\""+code+"\",\"message\":\"Terminal receipt request rejected\"}");
    }
}
