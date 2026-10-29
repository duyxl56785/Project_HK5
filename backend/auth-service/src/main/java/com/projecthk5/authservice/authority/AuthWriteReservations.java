package com.projecthk5.authservice.authority;

import com.projecthk5.security.JwtAccessVerifier;
import com.projecthk5.security.fencing.*;
import com.projecthk5.security.fencing.DurableAuthorityReservations.*;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

public final class AuthWriteReservations {
    private final DurableAuthorityReservations reservations;
    private final JwtAccessVerifier verifier;
    public AuthWriteReservations(DataSource source,JwtAccessVerifier verifier) {
        this.reservations=new DurableAuthorityReservations(source,Participant.AUTH);this.verifier=Objects.requireNonNull(verifier);
    }
    public Grant admit(WriteIntent intent,String jwt) throws SQLException {
        var principal=verifier.verify(jwt);
        String role=intent.mode()==WriteIntent.Mode.MODERATE?"ADMIN":"SELLER";
        if(!intent.accountId().toString().equals(principal.subject()) || !intent.tokenId().equals(principal.tokenId()) || !principal.tokenRoles().contains(role))
            throw new SecurityException("AUTHORITY_DENIED");
        return reservations.admit(intent,c->{
            verifier.verify(jwt);
            try(var query=c.prepareStatement("SELECT EXISTS(SELECT 1 FROM accounts a JOIN account_role_grants g ON g.account_id=a.id WHERE a.id=? AND a.status='ACTIVE' AND g.role=?)")) {
                query.setObject(1,intent.accountId());query.setString(2,role);
                try(var rows=query.executeQuery()){if(!rows.next() || !rows.getBoolean(1))throw new SecurityException("AUTHORITY_DENIED");}
            }
        });
    }
    public void release(Grant grant,TerminalSource source) throws Exception {reservations.release(grant,source);}
}
