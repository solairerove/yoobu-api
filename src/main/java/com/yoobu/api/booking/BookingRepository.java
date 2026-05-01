package com.yoobu.api.booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
            SELECT COUNT(b) FROM Booking b
            WHERE b.tenant.id = :tenantId AND b.deletedAt IS NULL
              AND b.createdAt >= :from AND b.createdAt < :to
            """)
    long countBookingsInPeriod(@Param("tenantId") Long tenantId,
                               @Param("from") OffsetDateTime from,
                               @Param("to") OffsetDateTime to);

    @Query("""
            SELECT SUM(b.totalPrice) FROM Booking b
            WHERE b.tenant.id = :tenantId AND b.deletedAt IS NULL
              AND b.createdAt >= :from AND b.createdAt < :to
            """)
    BigDecimal sumRevenueInPeriod(@Param("tenantId") Long tenantId,
                                  @Param("from") OffsetDateTime from,
                                  @Param("to") OffsetDateTime to);

    @Query("""
            SELECT b.telegramUserId, MAX(b.customerName), COUNT(b), SUM(b.totalPrice)
            FROM Booking b
            WHERE b.tenant.id = :tenantId AND b.deletedAt IS NULL
            GROUP BY b.telegramUserId
            ORDER BY SUM(b.totalPrice) DESC
            """)
    List<Object[]> findTopBuyersRaw(@Param("tenantId") Long tenantId, Pageable pageable);
}
