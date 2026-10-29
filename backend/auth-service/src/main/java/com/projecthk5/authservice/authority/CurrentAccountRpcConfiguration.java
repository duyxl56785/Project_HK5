package com.projecthk5.authservice.authority;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.projecthk5.authservice.security.*;
import com.projecthk5.security.InternalPairVerifier;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.bind.annotation.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class CurrentAccountRpcConfiguration {
    public static final String ROOT="/internal/auth/current-account/";
    private static final String VERIFIED=CurrentAccountRpcConfiguration.class.getName()+".verified";
    @Bean @Order(0)
    SecurityFilterChain currentAccountRpcChain(HttpSecurity http,Environment env) throws Exception {
        boolean enabled="true".equals(env.getProperty("hk5.auth.current-account.enabled","false"));
        var pairs=new HashMap<String,InternalPairVerifier>();
        for(String caller:List.of("seller","product")) {
            String prefix="HK5_"+caller.toUpperCase(Locale.ROOT)+"_AUTH_CURRENT_ACCOUNT_";
            pairs.put(ROOT+caller,new InternalPairVerifier(caller+"-service","auth-service",Set.of(
                new InternalPairVerifier.Scope("POST",ROOT+caller,"CURRENT_ACCOUNT_READ")),()->{
                    return credentials(env,prefix);
                },Clock.systemUTC()));
        }
        http.securityMatcher(ROOT+"**").csrf(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.anyRequest().access((auth,context)->new org.springframework.security.authorization.AuthorizationDecision(
                Boolean.TRUE.equals(context.getRequest().getAttribute(VERIFIED)))))
            .addFilterBefore((ServletRequest raw,ServletResponse out,FilterChain chain)->{
                var request=(HttpServletRequest)raw;var response=(HttpServletResponse)out;
                response.setHeader("Cache-Control","no-store");
                if(!enabled){error(response,403,"INTERNAL_FORBIDDEN");return;}
                var pair=pairs.get(request.getRequestURI());
                if(pair==null){error(response,403,"INTERNAL_FORBIDDEN");return;}
                var values=Collections.list(request.getHeaders("X-HK5-Service-Credential"));
                if(values.size()!=1 || values.getFirst().contains(",")){error(response,401,"INTERNAL_AUTH_INVALID");return;}
                try {pair.verify(values.getFirst(),request.getMethod(),request.getRequestURI());}
                catch(InternalPairVerifier.Failure failure){error(response,failure.status(),failure.code());return;}
                request.setAttribute(VERIFIED,Boolean.TRUE);chain.doFilter(request,response);
            },AnonymousAuthenticationFilter.class);
        return http.build();
    }
    private static InternalPairVerifier.Credentials credentials(Environment env,String prefix) {
        String current=env.getProperty(prefix+"SECRET"),previous=env.getProperty(prefix+"PREVIOUS_SECRET"),until=env.getProperty(prefix+"PREVIOUS_UNTIL");
        for(String other:List.of("HK5_SELLER_AUTH_CURRENT_ACCOUNT_","HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_","HK5_SELLER_AUTH_ROLE_SYNC_")) {
            if(other.equals(prefix))continue;
            for(String field:List.of("SECRET","PREVIOUS_SECRET")) {
                String value=env.getProperty(other+field);
                if(value!=null && (value.equals(current) || value.equals(previous)))throw new IllegalArgumentException("Internal pair credentials must be distinct");
            }
        }
        return new InternalPairVerifier.Credentials(current,previous,until==null?null:Instant.parse(until));
    }    static void error(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status);response.setContentType("application/json");
        response.getWriter().write("{\"code\":\""+code+"\",\"message\":\"Current authority request rejected\"}");
    }
    @RestController
    @ConditionalOnProperty(name="hk5.auth.current-account.enabled",havingValue="true")
    public static final class Receiver {
        private final ObjectProvider<Hk5JwtCodec> codecs;
        private final ObjectProvider<CurrentAccountAuthority> authorities;
        private final ObjectMapper json=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        public Receiver(ObjectProvider<Hk5JwtCodec> codecs,ObjectProvider<CurrentAccountAuthority> authorities){this.codecs=codecs;this.authorities=authorities;}
        @PostMapping(value={ROOT+"seller",ROOT+"product"},consumes="application/json",produces="application/json")
        public ResponseEntity<?> current(HttpServletRequest request) throws IOException {
            if(!Boolean.TRUE.equals(request.getAttribute(VERIFIED)))return failure(403,"INTERNAL_FORBIDDEN");
            var headers=Collections.list(request.getHeaders("Authorization"));
            if(headers.size()!=1 || !headers.getFirst().startsWith("Bearer ") || headers.getFirst().contains(","))return failure(401,"AUTH_ACCESS_INVALID");
            var codec=codecs.getIfAvailable();var authority=authorities.getIfAvailable();
            if(codec==null || authority==null)return failure(503,"AUTHORIZATION_DEPENDENCY_UNAVAILABLE");
            Hk5JwtCodec.VerifiedPrincipal principal;
            try {principal=codec.verify(headers.getFirst().substring(7));}catch(JwtException invalid){return failure(401,"AUTH_ACCESS_INVALID");}
            UUID requestId;
            byte[] bytes=request.getInputStream().readNBytes(1025);
            if(bytes.length>1024)return failure(413,"VALIDATION_FAILED");
            try {
                var text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                var node=json.readTree(text);
                if(node==null || !node.isObject() || node.size()!=1 || !node.has("requestId") || !node.get("requestId").isTextual())throw new IllegalArgumentException();
                String value=node.get("requestId").textValue(); requestId=UUID.fromString(value);
                if(!value.equals(requestId.toString()))throw new IllegalArgumentException();
            }catch(Exception invalid){return failure(400,"VALIDATION_FAILED");}
            try {
                var current=authority.requireCurrentForRequest(principal);
                return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("requestId",requestId.toString(),
                    "subject",current.accountId().toString(),"tokenId",principal.tokenId(),
                    "effectiveRoles",current.roles().stream().map(Enum::name).sorted().toList()));
            }catch(CurrentAccountAuthority.DecisionFailure denied){return failure(denied.httpStatus(),denied.code());}
            catch(RuntimeException unavailable){return failure(503,"AUTHORIZATION_DEPENDENCY_UNAVAILABLE");}
        }
        private ResponseEntity<?> failure(int status,String code){return ResponseEntity.status(status).header("Cache-Control","no-store")
            .body(Map.of("code",code,"message","Current authority request rejected"));}
    }
}
