package com.projecthk5.sellerservice.storefacts;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.projecthk5.security.InternalPairVerifier;
import com.projecthk5.sellerservice.store.StoreCurrentSource;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.bind.annotation.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class StoreFactsHttpConfiguration {
    public static final String PATH="/internal/seller/store-facts/batch";
    private static final String VERIFIED=StoreFactsHttpConfiguration.class.getName()+".verified";
    @Bean @Order(0)
    SecurityFilterChain storeFactsChain(HttpSecurity http,Environment env) throws Exception {
        boolean enabled="true".equals(env.getProperty("hk5.seller.store-facts.enabled","false"));
        String prefix="HK5_PRODUCT_SELLER_STORE_FACTS_";
        var verifier=new InternalPairVerifier("product-service","seller-service",Set.of(
            new InternalPairVerifier.Scope("POST",PATH,"CURRENT_STORE_FACTS_BATCH_READ")),()->{
                return com.projecthk5.security.IsolatedPairCredentials.read(env::getProperty,prefix);
            },Clock.systemUTC());
        http.securityMatcher("/internal/seller/**").csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.anyRequest().access((auth,context)->new org.springframework.security.authorization.AuthorizationDecision(
                Boolean.TRUE.equals(context.getRequest().getAttribute(VERIFIED)))))
            .addFilterBefore((ServletRequest raw,ServletResponse out,FilterChain chain)->{
                var request=(HttpServletRequest)raw;var response=(HttpServletResponse)out;response.setHeader("Cache-Control","no-store");
                if(!enabled){error(response,403,"INTERNAL_FORBIDDEN");return;}
                var values=Collections.list(request.getHeaders("X-HK5-Service-Credential"));
                if(values.size()!=1 || values.getFirst().contains(",")){error(response,401,"INTERNAL_AUTH_INVALID");return;}
                try{verifier.verify(values.getFirst(),request.getMethod(),request.getRequestURI());}
                catch(InternalPairVerifier.Failure failure){error(response,failure.status(),failure.code());return;}
                var nonces=Collections.list(request.getHeaders("X-HK5-Facts-Request-Id"));
                if(!nonces.isEmpty()) {
                    try {
                        if(nonces.size()!=1 || !UUID.fromString(nonces.getFirst()).toString().equals(nonces.getFirst()))throw new IllegalArgumentException();
                        response.setHeader("X-HK5-Facts-Request-Id",nonces.getFirst());
                    }catch(IllegalArgumentException invalid){error(response,400,"VALIDATION_FAILED");return;}
                }
                request.setAttribute(VERIFIED,Boolean.TRUE);chain.doFilter(request,response);
            },AnonymousAuthenticationFilter.class);
        return http.build();
    }
    private static void error(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status);response.setContentType("application/json");
        response.getWriter().write("{\"code\":\""+code+"\",\"message\":\"Store facts request rejected\"}");
    }
    @RestController
    @ConditionalOnProperty(name="hk5.seller.store-facts.enabled",havingValue="true")
    public static final class Receiver {
        private final StoreCurrentSource stores;
        private final ObjectMapper json=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        public Receiver(StoreCurrentSource stores){this.stores=stores;}
        @PostMapping(value=PATH,consumes="application/json",produces="application/json")
        public ResponseEntity<?> read(HttpServletRequest request) throws IOException {
            if(!Boolean.TRUE.equals(request.getAttribute(VERIFIED)))return failure(403,"INTERNAL_FORBIDDEN");
            byte[] bytes=request.getInputStream().readNBytes(8193);
            if(bytes.length>8192)return failure(413,"VALIDATION_FAILED");
            List<UUID> ids=new ArrayList<>();
            try {
                var text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                var node=json.readTree(text);
                if(node==null || !node.isObject() || node.size()!=1 || !node.has("storeIds") || !node.get("storeIds").isArray()
                    || node.get("storeIds").isEmpty() || node.get("storeIds").size()>100)throw new IllegalArgumentException();
                for(var value:node.get("storeIds")) {
                    if(!value.isTextual())throw new IllegalArgumentException();
                    UUID id=UUID.fromString(value.textValue());
                    if(!id.toString().equals(value.textValue()) || ids.contains(id))throw new IllegalArgumentException();ids.add(id);
                }
            }catch(Exception invalid){return failure(400,"VALIDATION_FAILED");}
            try {
                var facts=stores.findBatch(ids);var byId=new HashMap<UUID,StoreCurrentSource.OwnedFacts>();
                for(var fact:facts)if(!ids.contains(fact.storeId()) || byId.put(fact.storeId(),fact)!=null)throw new IllegalStateException();
                List<Map<String,Object>> results=new ArrayList<>();
                for(UUID id:ids) {
                    var fact=byId.get(id);
                    if(fact==null)results.add(Map.of("storeId",id.toString(),"result","ABSENT"));
                    else results.add(Map.of("storeId",id.toString(),"result","FOUND","sellerId",fact.sellerId().toString(),
                        "storeStatus",fact.storeStatus().name(),"sellerStatus",fact.sellerStatus().name(),"ownerReference",fact.ownerReference(),
                        "storeVersion",Long.toString(fact.storeVersion()),"sellerVersion",Long.toString(fact.sellerVersion())));
                }
                return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("results",results));
            }catch(RuntimeException unavailable){return failure(503,"STORE_FACTS_UNAVAILABLE");}
        }
        private ResponseEntity<?> failure(int status,String code){return ResponseEntity.status(status).header("Cache-Control","no-store")
            .body(Map.of("code",code,"message","Store facts request rejected"));}
    }
}
