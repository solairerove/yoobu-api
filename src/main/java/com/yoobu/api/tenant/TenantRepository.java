package com.yoobu.api.tenant;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    List<Tenant> findAllByOrderByCreatedAtDesc();
}
