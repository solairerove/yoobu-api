package com.yoobu.api.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByTenantIdAndTelegramUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long tenantId, Long telegramUserId);

    Optional<Booking> findByIdAndTenantIdAndDeletedAtIsNull(Long id, Long tenantId);

    Optional<Booking> findByIdAndTenantIdAndTelegramUserIdAndDeletedAtIsNull(Long id, Long tenantId, Long telegramUserId);

    List<Booking> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long tenantId);

    List<Booking> findByTenantIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(Long tenantId, BookingStatus status);

    List<Booking> findByTenantIdAndDeletedAtIsNullAndDeliveryDateOrderByCreatedAtDesc(Long tenantId, LocalDate deliveryDate);

    List<Booking> findByTenantIdAndDeletedAtIsNullAndStatusAndDeliveryDateOrderByCreatedAtDesc(
            Long tenantId,
            BookingStatus status,
            LocalDate deliveryDate
    );

    Page<Booking> findByTenantIdAndDeletedAtIsNull(Long tenantId, Pageable pageable);

    Page<Booking> findByTenantIdAndDeletedAtIsNullAndStatus(Long tenantId, BookingStatus status, Pageable pageable);

    Page<Booking> findByTenantIdAndDeletedAtIsNullAndDeliveryDate(Long tenantId, LocalDate deliveryDate, Pageable pageable);

    Page<Booking> findByTenantIdAndDeletedAtIsNullAndStatusAndDeliveryDate(
            Long tenantId,
            BookingStatus status,
            LocalDate deliveryDate,
            Pageable pageable
    );

    long countByTenantIdAndDeletedAtIsNullAndStatus(Long tenantId, BookingStatus status);
}
