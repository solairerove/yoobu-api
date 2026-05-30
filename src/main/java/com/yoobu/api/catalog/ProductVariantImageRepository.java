package com.yoobu.api.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantImageRepository extends JpaRepository<ProductVariantImage, Long> {

    List<ProductVariantImage> findByVariantIdOrderBySortOrderAscIdAsc(Long variantId);

    long countByVariantId(Long variantId);

    Optional<ProductVariantImage> findByIdAndVariantId(Long id, Long variantId);

    Optional<ProductVariantImage> findFirstByVariantIdOrderBySortOrderAscIdAsc(Long variantId);
}
