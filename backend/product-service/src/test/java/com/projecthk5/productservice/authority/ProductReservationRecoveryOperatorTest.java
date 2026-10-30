package com.projecthk5.productservice.authority;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
class ProductReservationRecoveryOperatorTest {
    @Test void disabledAndForceCommandsCannotAccessDatabase() {
        var source=mock(DataSource.class);
        assertThrows(IllegalStateException.class,()->ProductReservationRecoveryOperator.execute(new String[]{"status","id"},source,false));
        for(String action:new String[]{"force","unlock","mark-committed","release"})
            assertThrows(IllegalArgumentException.class,()->ProductReservationRecoveryOperator.execute(new String[]{action,"id"},source,true));
        verifyNoInteractions(source);
    }
}
