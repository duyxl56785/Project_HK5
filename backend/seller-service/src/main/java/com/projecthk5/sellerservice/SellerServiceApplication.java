package com.projecthk5.sellerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.projecthk5.sellerservice", "com.projecthk5.security"})
@org.springframework.scheduling.annotation.EnableScheduling
public class SellerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SellerServiceApplication.class, args);
    }
}
