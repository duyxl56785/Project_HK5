package com.projecthk5.sellerservice.outbox;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.*;

public final class DeliveryStore {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;
    public DeliveryStore(DataSource source){jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));}
    public record Claim(UUID eventId,UUID sellerId,String accountId,String status,long version,int attempt,UUID token){}
    public record Counts(long pending,long leased,long delivered,long blocked,long exhausted,long missingCurrentSnapshots){}
    public static void outsideTransaction(){
        if(TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isSynchronizationActive())throw new IllegalStateException("Dispatcher must run outside business transactions");
    }
    private int discover(){return jdbc.update("INSERT INTO seller_role_delivery(event_id) SELECT o.event_id FROM seller_status_outbox o WHERE NOT EXISTS (SELECT 1 FROM seller_role_delivery d WHERE d.event_id=o.event_id) ORDER BY EXISTS (SELECT 1 FROM seller_profiles p WHERE p.id=o.seller_id AND p.version=o.source_version AND p.owner_reference=o.owner_reference AND p.status=o.seller_status) DESC,o.recorded_at,o.event_id LIMIT 100 ON CONFLICT DO NOTHING");}
    public Counts reconcile(){
        outsideTransaction();return tx.execute(status->{discover();
            jdbc.update("UPDATE seller_role_delivery SET state='EXHAUSTED',claim_token=NULL,lease_until=NULL,last_error='LEASE_EXHAUSTED',updated_at=clock_timestamp() WHERE state='LEASED' AND lease_until<=clock_timestamp() AND attempts=8");
            Map<String,Long> counts=new HashMap<>();jdbc.query("SELECT state,count(*) AS n FROM seller_role_delivery GROUP BY state",row->{counts.put(row.getString("state"),row.getLong("n"));});
            long gaps=jdbc.queryForObject("SELECT count(*) FROM seller_profiles s WHERE NOT EXISTS (SELECT 1 FROM seller_status_outbox o WHERE o.seller_id=s.id AND o.source_version=s.version AND o.owner_reference=s.owner_reference AND o.seller_status=s.status)",Long.class);
            return new Counts(counts.getOrDefault("PENDING",0L),counts.getOrDefault("LEASED",0L),counts.getOrDefault("DELIVERED",0L),counts.getOrDefault("BLOCKED",0L),counts.getOrDefault("EXHAUSTED",0L),gaps);
        });
    }
    public Optional<Claim> claim(){
        outsideTransaction();return tx.execute(status->{discover();
            jdbc.update("UPDATE seller_role_delivery SET state='EXHAUSTED',claim_token=NULL,lease_until=NULL,last_error='LEASE_EXHAUSTED',updated_at=clock_timestamp() WHERE state='LEASED' AND lease_until<=clock_timestamp() AND attempts=8");
            var rows=jdbc.queryForList("SELECT o.event_id,o.seller_id,o.owner_reference,o.seller_status,o.source_version,d.attempts FROM seller_role_delivery d JOIN seller_status_outbox o USING(event_id) WHERE d.attempts<8 AND ((d.state='PENDING' AND d.next_attempt_at<=clock_timestamp()) OR (d.state='LEASED' AND d.lease_until<=clock_timestamp())) AND EXISTS (SELECT 1 FROM seller_profiles p JOIN seller_status_outbox latest ON latest.seller_id=p.id AND latest.source_version=p.version AND latest.owner_reference=p.owner_reference AND latest.seller_status=p.status WHERE p.id=o.seller_id AND (o.source_version=p.version OR (o.source_version<p.version AND EXISTS (SELECT 1 FROM seller_role_delivery newest WHERE newest.event_id=latest.event_id AND newest.state='DELIVERED')))) ORDER BY (o.source_version=(SELECT max(v.source_version) FROM seller_status_outbox v WHERE v.seller_id=o.seller_id)) DESC,o.recorded_at,o.event_id LIMIT 1 FOR UPDATE OF d SKIP LOCKED");
            if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();UUID event=(UUID)row.get("event_id"),token=UUID.randomUUID();int attempt=((Number)row.get("attempts")).intValue()+1;
            jdbc.update("UPDATE seller_role_delivery SET state='LEASED',attempts=?,claim_token=?,lease_until=clock_timestamp()+interval '30 seconds',updated_at=clock_timestamp() WHERE event_id=?",attempt,token,event);
            return Optional.of(new Claim(event,(UUID)row.get("seller_id"),(String)row.get("owner_reference"),(String)row.get("seller_status"),((Number)row.get("source_version")).longValue(),attempt,token));
        });
    }
    public boolean delivered(Claim claim,String result){
        outsideTransaction();if(!Set.of("APPLIED","DUPLICATE","STALE_IGNORED").contains(result))throw new IllegalArgumentException("Invalid ACK result");
        return jdbc.update("UPDATE seller_role_delivery SET state='DELIVERED',ack_result=?,claim_token=NULL,lease_until=NULL,last_error=NULL,updated_at=clock_timestamp() WHERE event_id=? AND state='LEASED' AND claim_token=? AND lease_until>clock_timestamp()",result,claim.eventId(),claim.token())==1;
    }
    public boolean failed(Claim claim,boolean blocked){
        outsideTransaction();String state=blocked?"BLOCKED":claim.attempt()>=8?"EXHAUSTED":"PENDING";
        int delay=Math.min(300,1<<Math.min(claim.attempt()-1,8));
        return jdbc.update("UPDATE seller_role_delivery SET state=?,claim_token=NULL,lease_until=NULL,last_error=?,next_attempt_at=clock_timestamp()+(?*interval '1 second'),updated_at=clock_timestamp() WHERE event_id=? AND state='LEASED' AND claim_token=? AND lease_until>clock_timestamp()",state,blocked?"WIRE_CONFLICT":"DELIVERY_RETRY",delay,claim.eventId(),claim.token())==1;
    }
}
