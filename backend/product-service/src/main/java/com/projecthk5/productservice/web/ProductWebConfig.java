package com.projecthk5.productservice.web;

import com.projecthk5.productservice.catalog.CatalogSnapshotSource;
import com.projecthk5.productservice.catalog.CurrentStoreFactsSource;
import com.projecthk5.productservice.catalog.ProductCatalogSnapshotSource;
import com.projecthk5.productservice.product.persistence.ProductRepository;
import org.springframework.beans.factory.ObjectProvider;
import com.projecthk5.productservice.image.ImageStorage;
import com.projecthk5.productservice.image.LocalFileSystemImageStorage;
import com.projecthk5.productservice.image.ProductImageValidator;
import com.projecthk5.productservice.product.ProductApplicationService;
import com.projecthk5.productservice.product.http.ProductManagementHandler;
import com.projecthk5.productservice.product.http.ProductManagementJson;
import com.projecthk5.security.Actor;
import com.projecthk5.security.JwtResourceServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ProductWebConfig {
    @Bean
    @Order(2)
    @ConditionalOnWebApplication
    SecurityFilterChain productSecurity(HttpSecurity http) throws Exception {
        return JwtResourceServer.configure(http.securityMatcher("/api/catalog/**", "/api/seller/**", "/api/admin/**",
                        "/api/reviews/**", "/internal/**", "/error"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                        // Internal pair routes authenticate with X-Internal-Token inside the controller.
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/api/seller/**").hasRole("SELLER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/reviews/**").hasRole("CUSTOMER")
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    ProductManagementHandler productManagementHandler(ProductApplicationService application) {
        return new ProductManagementHandler(() -> Actor.current().id(), application, new ProductManagementJson());
    }

    @Bean
    CatalogSnapshotSource catalogSnapshotSource(ProductRepository products, ObjectProvider<CurrentStoreFactsSource> stores) {
        return new ProductCatalogSnapshotSource(products, stores);
    }

    @Bean
    ProductImageValidator productImageValidator() { return new ProductImageValidator(); }

    @Bean
    ImageStorage imageStorage(@Value("${PRODUCT_IMAGE_DIR:}") String dir) throws IOException {
        Path root = (dir.isBlank() ? Path.of(System.getProperty("java.io.tmpdir"), "hk5-product-images") : Path.of(dir))
                .toAbsolutePath().normalize();
        Files.createDirectories(root);
        return new LocalFileSystemImageStorage(root);
    }
}
