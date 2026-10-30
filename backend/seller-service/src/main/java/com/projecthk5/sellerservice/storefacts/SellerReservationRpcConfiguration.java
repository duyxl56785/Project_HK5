package com.projecthk5.sellerservice.storefacts;

import com.projecthk5.security.*;
import com.projecthk5.security.fencing.*;
import com.projecthk5.security.fencing.DurableAuthorityReservations.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.time.Clock;
import java.util.*;
import javax.sql.DataSource;
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

/** Private service-pair RPC; identity headers never grant authority. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class SellerReservationRpcConfiguration {
    public static final String ROOT="/internal/seller/write-reservations/";
    private static final String VERIFIED=SellerReservationRpcConfiguration.class.getName()+".verified";
    private static final String PREFIX="HK5_PRODUCT_SELLER_STORE_FACTS_";
    @Bean @Order(-10)
    SecurityFilterChain sellerReservationChain(HttpSecurity http,Environment env) throws Exception {
        boolean enabled="true".equals(env.getProperty("hk5.seller.reservations.enabled","false"));
        var scopes=Set.of(new InternalPairVerifier.Scope("POST",ROOT+"product/admit","AUTHORITY_RESERVE"),
            new InternalPairVerifier.Scope("POST",ROOT+"product/lookup","AUTHORITY_LOOKUP"),
            new InternalPairVerifier.Scope("POST",ROOT+"product/release","AUTHORITY_RELEASE"),
            new InternalPairVerifier.Scope("POST",ROOT+"product/seal","AUTHORITY_SEAL"));
        var product=verifier(env,"product-service",PREFIX,scopes);
        var pairs=new HashMap<String,InternalPairVerifier>();
        for(var scope:scopes)pairs.put(scope.path(),product);
        http.securityMatcher(ROOT+"**").csrf(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.anyRequest().access((authentication,context)->new org.springframework.security.authorization.AuthorizationDecision(
                Boolean.TRUE.equals(context.getRequest().getAttribute(VERIFIED)))))
            .addFilterBefore((ServletRequest raw,ServletResponse out,FilterChain chain)->{
                var request=(HttpServletRequest)raw;var response=(HttpServletResponse)out;
                response.setHeader("Cache-Control","no-store");
                if(!enabled || (request.getRequestURI().equals(ROOT+"product/seal") && !"true".equals(env.getProperty("hk5.seller.reservations.recovery.enabled","false")))){error(response,403,"INTERNAL_FORBIDDEN");return;}
                var pair=pairs.get(request.getRequestURI());
                if(pair==null){error(response,403,"INTERNAL_FORBIDDEN");return;}
                var values=Collections.list(request.getHeaders("X-HK5-Service-Credential"));
                if(values.size()!=1 || values.getFirst().contains(",")){error(response,401,"INTERNAL_AUTH_INVALID");return;}
                try{pair.verify(values.getFirst(),request.getMethod(),request.getRequestURI());}
                catch(InternalPairVerifier.Failure failure){error(response,failure.status(),failure.code());return;}
                request.setAttribute(VERIFIED,Boolean.TRUE);chain.doFilter(request,response);
            },AnonymousAuthenticationFilter.class);
        return http.build();
    }
    private static InternalPairVerifier verifier(Environment env,String caller,String prefix,Set<InternalPairVerifier.Scope> scopes) {
        return new InternalPairVerifier(caller,"seller-service",scopes,
            ()->IsolatedPairCredentials.read(env::getProperty,prefix),Clock.systemUTC());
    }
    private static String credential(Environment env,String prefix) {
        IsolatedPairCredentials.read(env::getProperty,prefix);
        return env.getProperty(prefix+"SECRET");
    }
    private static URI requiredUri(Environment env,String name) {
        String value=env.getProperty(name);
        if(value==null || value.isBlank() || !value.equals(value.strip()))throw new IllegalArgumentException("Explicit reservation endpoint required");
        return URI.create(value);
    }
    private static void error(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status);response.setContentType("application/json");
        response.getWriter().write("{\"code\":\""+code+"\",\"message\":\"Reservation request rejected\"}");
    }
    @RestController
    @ConditionalOnProperty(name="hk5.seller.reservations.enabled",havingValue="true")
    public static final class Receiver {
        private final DataSource source;
        private final DurableAuthorityReservations reservations;
        private final TerminalReceiptClient receipts;
        private final TerminalClosureClient closures;
        private final ReservationRpcClient auth;
        public Receiver(DataSource source,Environment env) {
            this.source=source;
            reservations=new DurableAuthorityReservations(source,Participant.SELLER);
            // The reverse callback shares this pair secret, with a separate Product endpoint scope.
            closures=new TerminalClosureClient(URI.create(requiredUri(env,"HK5_SELLER_PRODUCT_TERMINAL_URL").toString()+"/closure"),
                ()->credential(env,PREFIX),Participant.SELLER);
            receipts=new TerminalReceiptClient(requiredUri(env,"HK5_SELLER_PRODUCT_TERMINAL_URL"),
                ()->credential(env,PREFIX),Participant.SELLER);
            URI authRoot=requiredUri(env,"HK5_SELLER_AUTH_RESERVATION_URL");
            if(!"/internal/auth/write-reservations/seller/".equals(authRoot.getPath()))
                throw new IllegalArgumentException("Explicit Seller-to-Auth lookup endpoint required");
            auth=new ReservationRpcClient(authRoot,
                ()->credential(env,"HK5_SELLER_AUTH_CURRENT_ACCOUNT_"),Participant.AUTH,()->null);
        }
        @PostMapping(value={ROOT+"product/admit",ROOT+"product/lookup",ROOT+"product/release",ROOT+"product/seal"},consumes="application/json",produces="application/json")
        public ResponseEntity<String> receive(HttpServletRequest request) throws IOException {
            if(!Boolean.TRUE.equals(request.getAttribute(VERIFIED)))return failure(403,"INTERNAL_FORBIDDEN");
            byte[] bytes=request.getInputStream().readNBytes(16385);
            if(bytes.length>16384)return failure(413,"VALIDATION_FAILED");
            String action=request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/')+1);
            ReservationWire.Request wire;
            try{wire=ReservationWire.readRequest(action,bytes);}
            catch(IllegalArgumentException invalid){return failure(400,"VALIDATION_FAILED");}
            try {
                Grant grant;
                if("admit".equals(action)) {
                    // Resolve Auth admission before Seller opens its local transaction.
                    var admission=auth.lookup(wire.intent());
                    if(admission.grant().isEmpty() || !"ACTIVE".equals(admission.state()))return failure(403,"AUTHORITY_DENIED");
                    grant=new SellerWriteReservations(source).admit(wire.intent());
                    return success(ReservationWire.grantReply(wire.nonce(),grant));
                }
                if("seal".equals(action)) {
                    reservations.seal(wire.intent(),wire.epoch(),id->closures.read(wire.intent(),wire.epoch()));
                    return success(ReservationWire.sealReply(wire.nonce(),wire.intent(),Participant.SELLER,wire.epoch()));
                }
                var stored=lookup(wire.intent());
                if("lookup".equals(action))return success(ReservationWire.lookupReply(wire.nonce(),stored.grant(),stored.state()));
                grant=stored.grant();
                if(grant==null || grant.epoch()!=wire.epoch())return failure(409,"RESERVATION_CONFLICT");
                // DurableAuthorityReservations resolves this callback before starting its local transaction.
                reservations.release(grant,id->receipts.read(wire.intent(),grant.epoch()));
                return success(ReservationWire.releaseReply(wire.nonce(),grant));
            }catch(JwtException invalid){return failure(401,"AUTH_ACCESS_INVALID");}
            catch(SecurityException denied){return failure(403,"AUTHORITY_DENIED");}
            catch(ReservationRpcClient.Failure unavailable){return failure(unavailable.status()==409?409:503,"RESERVATION_DEPENDENCY_UNAVAILABLE");}
            catch(IllegalStateException conflict){return failure("FENCE_CONFLICT_OR_UNAVAILABLE".equals(conflict.getMessage())?409:503,"RESERVATION_CONFLICT_OR_UNAVAILABLE");}
            catch(Exception unavailable){return failure(503,"RESERVATION_DEPENDENCY_UNAVAILABLE");}
        }
        private record Snapshot(Grant grant,String state){}
        private Snapshot lookup(WriteIntent intent) throws SQLException {
            // Grant and terminal state come from one local row snapshot, never from caller evidence.
            try(var connection=source.getConnection();var query=connection.prepareStatement(
                    "SELECT binding_hash,epoch,state FROM authority_write_reservations WHERE request_id=?")) {
                query.setObject(1,intent.requestId());
                try(var rows=query.executeQuery()) {
                    if(!rows.next())return new Snapshot(null,null);
                    if(!intent.fingerprint().equals(rows.getString(1)))throw new IllegalStateException("FENCE_CONFLICT_OR_UNAVAILABLE");
                    String state=rows.getString(3);
                    if(!Set.of("ACTIVE","RELEASED").contains(state))throw new SQLException("Invalid reservation state");
                    return new Snapshot(new Grant(intent.requestId(),intent.fingerprint(),Participant.SELLER,rows.getLong(2)),state);
                }
            }
        }
        private static ResponseEntity<String> success(String body){return ResponseEntity.ok().header("Cache-Control","no-store").header("Content-Type","application/json").body(body);}
        private static ResponseEntity<String> failure(int status,String code){return ResponseEntity.status(status).header("Cache-Control","no-store")
            .header("Content-Type","application/json").body("{\"code\":\""+code+"\",\"message\":\"Reservation request rejected\"}");}
    }
}
