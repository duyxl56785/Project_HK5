package com.projecthk5.authservice.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileProvisioningTaskRepository extends JpaRepository<ProfileProvisioningTask, UUID> {}
