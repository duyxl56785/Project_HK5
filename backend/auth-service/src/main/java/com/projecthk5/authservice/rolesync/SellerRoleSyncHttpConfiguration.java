package com.projecthk5.authservice.rolesync;

import com.projecthk5.security.InternalPairVerifier;
import com.projecthk5.security.fencing.RevocationBarrier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.bind.annotation.*;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SellerRoleSyncHttpConfiguration {
    public static final String PATH = "/internal/auth/seller-role-sync";
    static final String HEADER = "X-HK5-Service-Credential";

    @Bean @ConditionalOnMissingBean(SellerRoleSyncService.class)
    @ConditionalOnProperty(name="hk5.auth.role-sync.enabled", havingValue="true")
    SellerRoleSyncService sellerRoleSyncService(JdbcTemplate jdbc) {return new SellerRoleSyncService(jdbc);}


    @Bean @Order(1)
    SecurityFilterChain sellerRoleSyncChain(HttpSecurity http, Environment env) throws Exception {
        boolean enabled="true".equals(env.getProperty("hk5.auth.role-sync.enabled","false"));
        var verifier=new InternalPairVerifier("seller-service","auth-service",Set.of(
            new InternalPairVerifier.Scope("POST",PATH,"SELLER_ROLE_SYNC")), () -> {
                return com.projecthk5.security.IsolatedPairCredentials.read(env::getProperty,"HK5_SELLER_AUTH_ROLE_SYNC_");
            }, Clock.systemUTC());
        http.securityMatcher("/internal/**").csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers(PATH).access((authentication,context)-> new org.springframework.security.authorization.AuthorizationDecision(Boolean.TRUE.equals(context.getRequest().getAttribute(Receiver.VERIFIED)))).anyRequest().denyAll())
            .addFilterBefore(new jakarta.servlet.Filter() {
                @Override public void doFilter(ServletRequest raw, ServletResponse out, FilterChain chain)
                        throws IOException, ServletException {
                    var request=(HttpServletRequest)raw; var response=(HttpServletResponse)out;
                    response.setHeader("Cache-Control","no-store");
                    if(!enabled) {error(response,403,"INTERNAL_FORBIDDEN"); return;}
                    var values=Collections.list(request.getHeaders(HEADER));
                    if(values.size()!=1 || values.getFirst().contains(",")) {
                        error(response,401,"INTERNAL_AUTH_INVALID"); return;
                    }
                    try {verifier.verify(values.getFirst(),request.getMethod(),request.getRequestURI());}
                    catch(InternalPairVerifier.Failure failure) {error(response,failure.status(),failure.code()); return;}
                    // Only this exact scope bypasses the catch-all deny rule; MVC still owns dispatch.
                    request.setAttribute(Receiver.VERIFIED,Boolean.TRUE);
                    chain.doFilter(request,response);
                }
            }, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    static void error(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status);response.setContentType("application/json");
        response.getWriter().write("{\"code\":\""+code+"\",\"message\":\"Role sync request rejected\"}");
    }

    @RestController
    @ConditionalOnProperty(name="hk5.auth.role-sync.enabled", havingValue="true")
    public static final class Receiver {
        static final String VERIFIED=Receiver.class.getName()+".verified";
        private final SellerRoleSyncJson codec=new SellerRoleSyncJson();
        private final SellerRoleRevocationRecovery recovery;
        public Receiver(SellerRoleSyncService service) {this.recovery=null;}
        @Autowired public Receiver(SellerRoleSyncService service,ObjectProvider<SellerRoleRevocationRecovery> recovery) {
            this.recovery=recovery.getIfAvailable();
        }
        @PostMapping(value=PATH,consumes="application/json",produces="application/json")
        public ResponseEntity<String> receive(HttpServletRequest request) throws IOException {
            if(!Boolean.TRUE.equals(request.getAttribute(VERIFIED)))return reply(403,"INTERNAL_FORBIDDEN");
            byte[] bytes=request.getInputStream().readNBytes(16385);
            if(bytes.length>16384)return reply(413,"ROLE_SYNC_PAYLOAD_TOO_LARGE");
            SellerRoleSyncService.Command command;
            try {
                String json=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                command=codec.read(json);
            } catch(IllegalArgumentException | CharacterCodingException invalid) {return reply(400,"VALIDATION_FAILED");}
            try {
                if(recovery==null)return reply(503,"ROLE_SYNC_UNAVAILABLE");
                var result=recovery.apply(command);
                return ResponseEntity.ok().header("Cache-Control","no-store").body(codec.ack(command,result));
            } catch(SellerRoleSyncService.Conflict conflict) {return reply(409,conflict.code());}
            catch(RevocationBarrier.Conflict conflict) {return reply(409,"ROLE_SYNC_IDENTITY_CONFLICT");}
            catch(Exception unavailable) {return reply(503,"ROLE_SYNC_UNAVAILABLE");}
        }
        private ResponseEntity<String> reply(int status,String code) {
            return ResponseEntity.status(status).header("Cache-Control","no-store")
                .body("{\"code\":\""+code+"\",\"message\":\"Role sync request rejected\"}");
        }
    }
}
