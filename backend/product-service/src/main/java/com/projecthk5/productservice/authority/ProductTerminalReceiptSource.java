package com.projecthk5.productservice.authority;

import com.projecthk5.security.fencing.DurableWriteLedger;
import com.projecthk5.security.fencing.DurableAuthorityReservations.Participant;
import java.sql.SQLException;
import java.util.*;
import javax.sql.DataSource;

/** A single statement snapshot allows the callback identity to hold SELECT privileges only. */
public final class ProductTerminalReceiptSource {
    private final DataSource source;
    public ProductTerminalReceiptSource(DataSource source){this.source=Objects.requireNonNull(source);}

    public DurableWriteLedger.Receipt terminal(UUID requestId) throws SQLException {
        try(var connection=source.getConnection();var query=connection.prepareStatement(
            "SELECT o.binding_hash,o.epoch,o.state,g.participant,g.authority_epoch "
            +"FROM authority_write_operations o LEFT JOIN authority_write_grants g ON g.request_id=o.request_id WHERE o.request_id=?")) {
            query.setObject(1,requestId);
            try(var rows=query.executeQuery()) {
                String hash=null;long epoch=0;DurableWriteLedger.State state=null;
                var grants=new EnumMap<Participant,Long>(Participant.class);
                while(rows.next()) {
                    String rowHash=rows.getString(1);long rowEpoch=rows.getLong(2);
                    var rowState=DurableWriteLedger.State.valueOf(rows.getString(3));
                    if(rowHash==null || !rowHash.matches("[0-9a-f]{64}") || rowEpoch<=0
                        || rowState!=DurableWriteLedger.State.COMMITTED && rowState!=DurableWriteLedger.State.ABORTED)
                        throw new IllegalStateException("Terminal receipt unavailable");
                    if(hash!=null && (!hash.equals(rowHash) || epoch!=rowEpoch || state!=rowState))
                        throw new IllegalStateException("Terminal receipt unavailable");
                    hash=rowHash;epoch=rowEpoch;state=rowState;
                    String participant=rows.getString(4);
                    if(participant!=null) {
                        long authorityEpoch=rows.getLong(5);
                        if(rows.wasNull() || authorityEpoch<=0 || grants.put(Participant.valueOf(participant),authorityEpoch)!=null)
                            throw new IllegalStateException("Terminal receipt unavailable");
                    } else if(rows.getObject(5)!=null)throw new IllegalStateException("Terminal receipt unavailable");
                }
                if(hash==null)throw new IllegalStateException("Terminal receipt unavailable");
                return new DurableWriteLedger.Receipt(requestId,hash,epoch,state,grants);
            }
        }
    }
}
