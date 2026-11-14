package com.projecthk5.sellerservice.voucher;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VoucherClockConfig {
    @Bean("voucherClock")
    public Clock voucherClock() { return Clock.systemUTC(); }
}
