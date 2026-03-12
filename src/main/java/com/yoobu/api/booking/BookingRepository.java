package com.yoobu.api.booking;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByTenantIdAndTelegramUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long tenantId, Long telegramUserId);

    Optional<Booking> findByIdAndTenantIdAndDeletedAtIsNull(Long id, Long tenantId);

    Optional<Booking> findByIdAndTenantIdAndTelegramUserIdAndDeletedAtIsNull(Long id, Long tenantId, Long telegramUserId);
}
