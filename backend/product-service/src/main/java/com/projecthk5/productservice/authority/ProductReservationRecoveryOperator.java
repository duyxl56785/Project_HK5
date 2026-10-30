package com.projecthk5.productservice.authority;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.security.fencing.ReservationWire;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class ProductReservationRecoveryOperator {
    private ProductReservationRecoveryOperator() { }
    public static void main(String[] args) throws Exception {
        if(!"true".equals(System.getenv("HK5_PRODUCT_RECOVERY_OPERATOR_ENABLED")))throw new IllegalStateException("Recovery operator is disabled");
        var source=new DriverManagerDataSource(required("HK5_PRODUCT_RECOVERY_DB_URL"),required("HK5_PRODUCT_RECOVERY_DB_USERNAME"),required("HK5_PRODUCT_RECOVERY_DB_PASSWORD"));
        var options=new Properties();options.setProperty("options","-c lock_timeout=500ms -c statement_timeout=5000ms");source.setConnectionProperties(options);
        System.out.println(execute(args,source,true));
    }
    public static String execute(String[] args,DataSource source,boolean enabled) throws Exception {
        if(!enabled)throw new IllegalStateException("Recovery operator is disabled");
        if(args.length<2 || !Set.of("status","cancel","retry","enlist").contains(args[0]))throw new IllegalArgumentException("Unsupported recovery command");
        var store=new ProductReservationRecoveryStore(source,Duration.ZERO);
        if("enlist".equals(args[0])) {
            if(args.length!=3)throw new IllegalArgumentException("Expected intent file and operation epoch");
            byte[] bytes;
            try(var input=Files.newInputStream(Path.of(args[1]))){bytes=input.readNBytes(16385);}
            var intent=ReservationWire.readRequest("lookup",bytes).intent();long expected=epoch(args[2]);store.enlist(intent,expected);
            store.requestRetry(intent.requestId(),expected,"CLI_enlist:"+actor(source));
            return "ENLISTED";
        }
        UUID id=UUID.fromString(args[1]);if(!id.toString().equals(args[1]))throw new IllegalArgumentException("Canonical request ID required");
        if("status".equals(args[0])) {
            if(args.length!=2)throw new IllegalArgumentException("Expected request ID only");
            return new ObjectMapper().writeValueAsString(store.inspect(id).orElseThrow());
        }
        if(args.length!=3)throw new IllegalArgumentException("Expected request ID and operation epoch");
        store.requestRetry(id,epoch(args[2]),"CLI_"+args[0]+":"+actor(source));
        return "QUEUED";
    }
    private static long epoch(String value){if(!value.matches("[1-9][0-9]*"))throw new IllegalArgumentException("Canonical positive epoch required");return Long.parseLong(value);}
    private static String actor(DataSource source) throws SQLException {try(var connection=source.getConnection();var query=connection.createStatement();var rows=query.executeQuery("SELECT current_user")){rows.next();return rows.getString(1);}}
    private static String required(String name){String value=System.getenv(name);if(value==null || value.isBlank())throw new IllegalArgumentException("Missing recovery connection setting");return value;}
}
