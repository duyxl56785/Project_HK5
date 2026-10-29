package com.projecthk5.authservice.rolesync;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal transactional core only; deliberately no component, controller or production bean. */
@Transactional(propagation = Propagation.REQUIRED)
public class SellerRoleSyncService {
    private final JdbcTemplate jdbc;
    public SellerRoleSyncService(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }
    public enum Status { PENDING_REVIEW, APPROVED, REJECTED, SUSPENDED }
    public enum Result { APPLIED, DUPLICATE, STALE_IGNORED }
    public record Command(UUID eventId, UUID sellerId, String accountId, Status sellerStatus, long sourceVersion) {
        public Command {
            if (eventId == null || sellerId == null || sellerStatus == null || accountId == null || sourceVersion < 0)
                throw new IllegalArgumentException("Invalid role sync command");
            if(accountId.isBlank() || accountId.codePointCount(0,accountId.length())>128)
                throw new IllegalArgumentException("Invalid Auth account identity");
        }
    }
    public static final class Conflict extends RuntimeException {
        private final String code;
        private Conflict(String code) { super(code); this.code = code; }
        public String code() { return code; }
    }

    public Result apply(Command command) {
        Objects.requireNonNull(command);
        // One durable lock gives a consistent order across event, Seller and Account uniqueness races.
        jdbc.queryForObject("SELECT id FROM seller_role_sync_lock WHERE id=1 FOR UPDATE", Integer.class);
        var existingEvent = jdbc.queryForList("SELECT r.seller_id,r.source_version,v.seller_status,s.account_id "
                + "FROM seller_role_sync_receipts r JOIN seller_role_sync_versions v USING(seller_id,source_version) "
                + "JOIN seller_role_sync_state s USING(seller_id) WHERE r.event_id=?", command.eventId());
        if (!existingEvent.isEmpty()) {
            var event = existingEvent.getFirst();
            if (!command.sellerId().equals(event.get("seller_id")) || !command.accountId().equals(event.get("account_id").toString())
                    || command.sourceVersion() != ((Number)event.get("source_version")).longValue()
                    || !command.sellerStatus().name().equals(event.get("seller_status"))) throw conflict();
            return Result.DUPLICATE;
        }
        UUID account;
        try {
            account = UUID.fromString(command.accountId());
            if(!account.toString().equals(command.accountId())) throw new IllegalArgumentException();
        } catch(IllegalArgumentException missing) {throw new Conflict("ROLE_SYNC_ACCOUNT_NOT_FOUND");}
        if (jdbc.queryForList("SELECT id FROM accounts WHERE id=? FOR UPDATE", account).isEmpty())
            throw new Conflict("ROLE_SYNC_ACCOUNT_NOT_FOUND");
        var bindings = jdbc.queryForList("SELECT seller_id,account_id,latest_version FROM seller_role_sync_state "
                + "WHERE seller_id=? OR account_id=?", command.sellerId(), account);
        if (bindings.stream().anyMatch(row -> !command.sellerId().equals(row.get("seller_id"))
                || !account.equals(row.get("account_id")))) throw conflict();
        boolean first = bindings.isEmpty();
        long latest = first ? -1 : ((Number)bindings.getFirst().get("latest_version")).longValue();
        var version = jdbc.queryForList("SELECT seller_status FROM seller_role_sync_versions WHERE seller_id=? AND source_version=?",
                command.sellerId(), command.sourceVersion());
        if (!version.isEmpty() && !command.sellerStatus().name().equals(version.getFirst().get("seller_status"))) throw conflict();
        if (first) jdbc.update("INSERT INTO seller_role_sync_state VALUES(?,?,?)", command.sellerId(), account, command.sourceVersion());
        if (version.isEmpty()) jdbc.update("INSERT INTO seller_role_sync_versions VALUES(?,?,?)", command.sellerId(), command.sourceVersion(), command.sellerStatus().name());
        jdbc.update("INSERT INTO seller_role_sync_receipts(event_id,seller_id,source_version) VALUES(?,?,?)",
                command.eventId(), command.sellerId(), command.sourceVersion());
        if (!version.isEmpty()) return Result.DUPLICATE;
        if (command.sourceVersion() < latest) return Result.STALE_IGNORED;
        jdbc.update("UPDATE seller_role_sync_state SET latest_version=? WHERE seller_id=?", command.sourceVersion(), command.sellerId());
        if (command.sellerStatus() == Status.APPROVED)
            jdbc.update("INSERT INTO account_role_grants(account_id,role) VALUES(?,'SELLER') ON CONFLICT DO NOTHING", account);
        else jdbc.update("DELETE FROM account_role_grants WHERE account_id=? AND role='SELLER'", account);
        return Result.APPLIED;
    }
    private static Conflict conflict() { return new Conflict("ROLE_SYNC_IDENTITY_CONFLICT"); }
}
