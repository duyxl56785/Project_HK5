package com.projecthk5.sellerservice.http;
import com.projecthk5.security.*;
import com.projecthk5.sellerservice.security.CurrentAccountSellerPolicy;
import com.projecthk5.sellerservice.seller.service.*;
import com.projecthk5.sellerservice.seller.domain.*;
import com.projecthk5.sellerservice.seller.repository.SellerProfileRepository;
import com.projecthk5.sellerservice.store.*;
import jakarta.persistence.EntityManager;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.RequestScope;

@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name="hk5.seller.http.read.enabled",havingValue="true")
public class SellerHttpReadConfiguration {
    private static final String PROOF=SellerHttpReadConfiguration.class.getName()+".current";
    @Bean CurrentAccountClient sellerHttpCurrentAccount(Environment env){
        URI endpoint=URI.create(env.getRequiredProperty("HK5_AUTH_CURRENT_ACCOUNT_URL"));
        if(!"/internal/auth/current-account/seller".equals(endpoint.getRawPath()))throw new IllegalArgumentException("Seller current-account pair endpoint required");
        return new CurrentAccountClient(endpoint,JwtVerifierSettings.read(env::getProperty,Clock.systemUTC()),()->env.getProperty("HK5_SELLER_AUTH_CURRENT_ACCOUNT_SECRET"));
    }
    @Bean @RequestScope SellerPersistenceService sellerHttpProfiles(SellerProfileRepository repository,HttpServletRequest request){
        return new SellerPersistenceService(repository,new CurrentAccountSellerPolicy(proof(request)));
    }
    @Bean @RequestScope StorePersistenceService sellerHttpStores(StoreRepository stores,EntityManager em,SellerPersistenceService profiles,HttpServletRequest request){
        return new StorePersistenceService(stores,em,actor->{var account=proof(request);account.requireSubject(actor.reference());account.requireRole("SELLER");return profiles.findOwn(actor);});
    }
    @Bean @Order(10) SecurityFilterChain sellerReadChain(HttpSecurity http,CurrentAccountClient current) throws Exception {
        http.securityMatcher("/api/sellers/**","/api/stores/**").httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable).csrf(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.anyRequest().access((authentication,ctx)->new org.springframework.security.authorization.AuthorizationDecision(ctx.getRequest().getAttribute(PROOF) instanceof CurrentAccountClient.CurrentAccount)))
            .addFilterBefore((ServletRequest raw,ServletResponse out,FilterChain chain)->{
                var request=(HttpServletRequest)raw;var response=(HttpServletResponse)out;response.setHeader("Cache-Control","no-store");
                if(!"GET".equals(request.getMethod()) || !Set.of("/api/sellers/me","/api/stores/me").contains(request.getRequestURI())){error(response,403,"SELLER_WRITE_AUTHORITY_UNAVAILABLE");return;}
                if(request.getQueryString()!=null){error(response,400,"VALIDATION_FAILED");return;}
                var values=Collections.list(request.getHeaders("Authorization"));
                if(values.size()!=1 || !values.getFirst().startsWith("Bearer ") || values.getFirst().contains(",")){error(response,401,"AUTH_ACCESS_INVALID");return;}
                if(TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isSynchronizationActive()){error(response,503,"AUTHORIZATION_DEPENDENCY_UNAVAILABLE");return;}
                try{var account=current.resolve(values.getFirst().substring(7));if("/api/stores/me".equals(request.getRequestURI()))account.requireRole("SELLER");request.setAttribute(PROOF,account);}
                catch(CurrentAccountClient.Failure failure){error(response,failure.status(),failure.status()==401?"AUTH_ACCESS_INVALID":failure.status()==403?"AUTH_FORBIDDEN":"AUTHORIZATION_DEPENDENCY_UNAVAILABLE");return;}
                chain.doFilter(request,response);
            },AnonymousAuthenticationFilter.class);
        return http.build();
    }
    private static CurrentAccountClient.CurrentAccount proof(HttpServletRequest request){Object value=request.getAttribute(PROOF);if(!(value instanceof CurrentAccountClient.CurrentAccount account))throw new IllegalStateException("Verified request authority required");return account;}
    private static void error(HttpServletResponse response,int status,String code) throws IOException {response.setStatus(status);response.setContentType("application/json");response.getWriter().write("{\"code\":\""+code+"\",\"message\":\"Seller request rejected\"}");}
    @RestController @ConditionalOnProperty(name="hk5.seller.http.read.enabled",havingValue="true")
    public static final class Reader {
        private final SellerPersistenceService profiles;private final StorePersistenceService stores;
        public Reader(SellerPersistenceService profiles,StorePersistenceService stores){this.profiles=profiles;this.stores=stores;}
        private SellerStoreReadHandler handler(HttpServletRequest request){return new SellerStoreReadHandler(new CurrentAccountSellerPolicy(proof(request)),profiles,stores);}
        @GetMapping("/api/sellers/me") public ResponseEntity<?> seller(HttpServletRequest request){return read(()->handler(request).sellerOwn());}
        @GetMapping("/api/stores/me") public ResponseEntity<?> store(HttpServletRequest request){return read(()->handler(request).storeOwn());}
        private ResponseEntity<?> read(java.util.function.Supplier<?> action){
            try{return ResponseEntity.ok().header("Cache-Control","no-store").body(action.get());}
            catch(SellerPersistenceException missing){return denied(missing.getCode()==SellerPersistenceException.Code.SELLER_NOT_FOUND?404:503);}
            catch(StorePersistenceException missing){return denied(missing.getCode()==StorePersistenceException.Code.STORE_NOT_FOUND?404:503);}
            catch(SellerRuleException | StoreRuleException denied){return denied(403);}
            catch(CurrentAccountClient.Failure denied){return denied(denied.status());}
            catch(RuntimeException unavailable){return denied(503);}
        }
        private ResponseEntity<?> denied(int status){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("code",status==404?"OWN_RESOURCE_NOT_FOUND":status==403?"AUTH_FORBIDDEN":"AUTHORIZATION_DEPENDENCY_UNAVAILABLE","message","Seller request rejected"));}
    }
}
