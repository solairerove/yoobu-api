package com.yoobu.api.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByTenantIdAndTelegramUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long tenantId, Long telegramUserId);

    Optional<Booking> findByIdAndTenantIdAndDeletedAtIsNull(Long id, Long tenantId);

    Optional<Booking> findByIdAndTenantIdAndTelegramUserIdAndDeletedAtIsNull(Long id, Long tenantId, Long telegramUserId);

    @Query("""
            select b
            from Booking b
            where b.tenant.id = :tenantId
              and b.deletedAt is null
              and (:status is null or b.status = :status)
              and (:deliveryDate is null or b.deliveryDate = :deliveryDate)
            order by b.createdAt desc
            """)
    List<Booking> findAdminBookings(
            @Param("tenantId") Long tenantId,
            @Param("status") BookingStatus status,
            @Param("deliveryDate") LocalDate deliveryDate
    );
}
