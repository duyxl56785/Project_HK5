package com.projecthk5.productservice.web;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetailsRepository extends JpaRepository<ProductExtras.Details, UUID> {}
