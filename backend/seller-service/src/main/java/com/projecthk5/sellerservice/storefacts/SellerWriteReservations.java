package com.projecthk5.sellerservice.storefacts;

import com.projecthk5.security.fencing.*;
import com.projecthk5.security.fencing.DurableAuthorityReservations.*;
import java.sql.SQLException;
import javax.sql.DataSource;

public final class SellerWriteReservations {
    private final DurableAuthorityReservations reservations;
    public SellerWriteReservations(DataSource source) {reservations=new DurableAuthorityReservations(source,Participant.SELLER);}
    public Grant admit(WriteIntent intent) throws SQLException {
        return reservations.admit(intent,c->{
            try(var query=c.prepareStatement("SELECT p.owner_reference,p.status,p.version,s.status,s.version FROM stores s JOIN seller_profiles p ON p.id=s.seller_id WHERE s.id=?")) {
                query.setObject(1,intent.storeId());try(var rows=query.executeQuery()) {
                    if(!rows.next() || !"APPROVED".equals(rows.getString(2)) || !"OPEN".equals(rows.getString(4))
                            || rows.getLong(3)!=intent.sellerVersion() || rows.getLong(5)!=intent.storeVersion()
                            || (intent.mode()==WriteIntent.Mode.MANAGE)!=intent.accountId().toString().equals(rows.getString(1)))
                        throw new SecurityException("STORE_AUTHORITY_DENIED");
                }
            }
        });
    }
    public void release(Grant grant,TerminalSource source) throws Exception {reservations.release(grant,source);}
}
