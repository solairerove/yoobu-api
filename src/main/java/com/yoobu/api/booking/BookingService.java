package com.yoobu.api.booking;

import com.yoobu.api.audit.AuditLogService;
import com.yoobu.api.booking.dto.BookingItemRequest;
import com.yoobu.api.booking.dto.BookingItemResponse;
import com.yoobu.api.booking.dto.BookingResponse;
import com.yoobu.api.booking.dto.CreateBookingRequest;
import com.yoobu.api.catalog.CatalogService;
import com.yoobu.api.catalog.CatalogServiceRepository;
import com.yoobu.api.tenant.Tenant;
import com.yoobu.api.tenant.TenantConfig;
import com.yoobu.api.tenant.TenantConfigRepository;
import com.yoobu.api.tenant.TenantContext;
import com.yoobu.api.tenant.TenantTimeService;
import com.yoobu.api.tenant.TenantType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final String ENTITY_NAME = "booking";

    private final AuditLogService auditLogService;
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final CatalogServiceRepository catalogServiceRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final TenantTimeService tenantTimeService;

    @Transactional
    public BookingResponse createFoodOrder(CreateBookingRequest request, Long telegramUserId) {
        Tenant tenant = requireFoodOrderTenant();
        validateDeliveryDate(request.deliveryDate(), tenant);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Booking booking = new Booking();
        booking.setTenant(tenant);
        booking.setType(BookingType.ORDER);
        booking.setTelegramUserId(telegramUserId);
        booking.setCustomerName(request.customerName());
        booking.setCustomerPhone(request.customerPhone());
        booking.setStatus(BookingStatus.NEW);
        booking.setNote(request.note());
        booking.setDeliveryDate(request.deliveryDate());
        booking.setCreatedAt(now);
        booking.setUpdatedAt(now);

        Booking savedBooking = bookingRepository.save(booking);

        List<BookingItem> bookingItems = request.items().stream()
                .map(item -> toBookingItem(savedBooking, item, tenant.getId()))
                .toList();

        bookingItemRepository.saveAll(bookingItems);

        BigDecimal totalPrice = bookingItems.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        savedBooking.setTotalPrice(totalPrice);
        savedBooking.setUpdatedAt(now);

        Booking persistedBooking = bookingRepository.save(savedBooking);
        auditLogService.logCreate(
                tenant.getId(),
                ENTITY_NAME,
                persistedBooking.getId(),
                telegramUserId,
                toAuditSnapshot(persistedBooking, bookingItems)
        );

        return toResponse(persistedBooking, bookingItems);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(Long telegramUserId) {
        requireFoodOrderTenant();

        return bookingRepository.findByTenantIdAndTelegramUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                        TenantContext.getRequiredTenantId(), telegramUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse getMyBooking(Long bookingId, Long telegramUserId) {
        requireFoodOrderTenant();

        Booking booking = bookingRepository.findByIdAndTenantIdAndTelegramUserIdAndDeletedAtIsNull(
                        bookingId, TenantContext.getRequiredTenantId(), telegramUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        return toResponse(booking);
    }

    @Transactional
    public BookingResponse cancelMyBooking(Long bookingId, Long telegramUserId) {
        requireFoodOrderTenant();

        Booking booking = bookingRepository.findByIdAndTenantIdAndTelegramUserIdAndDeletedAtIsNull(
                        bookingId, TenantContext.getRequiredTenantId(), telegramUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (booking.getStatus() == BookingStatus.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed booking cannot be cancelled");
        }

        BookingAuditSnapshot oldSnapshot = toAuditSnapshot(booking);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        Booking savedBooking = bookingRepository.save(booking);
        auditLogService.logAction(
                savedBooking.getTenant().getId(),
                ENTITY_NAME,
                savedBooking.getId(),
                "CANCEL",
                telegramUserId,
                oldSnapshot,
                toAuditSnapshot(savedBooking)
        );

        return toResponse(savedBooking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getAdminBookings(BookingStatus status, LocalDate deliveryDate) {
        requireFoodOrderTenant();

        Long tenantId = TenantContext.getRequiredTenantId();
        List<Booking> bookings;

        if (status != null && deliveryDate != null) {
            bookings = bookingRepository.findByTenantIdAndDeletedAtIsNullAndStatusAndDeliveryDateOrderByCreatedAtDesc(
                    tenantId, status, deliveryDate);
        } else if (status != null) {
            bookings = bookingRepository.findByTenantIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(tenantId, status);
        } else if (deliveryDate != null) {
            bookings = bookingRepository.findByTenantIdAndDeletedAtIsNullAndDeliveryDateOrderByCreatedAtDesc(
                    tenantId, deliveryDate);
        } else {
            bookings = bookingRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId);
        }

        return bookings
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse getAdminBooking(Long bookingId) {
        requireFoodOrderTenant();

        Booking booking = bookingRepository.findByIdAndTenantIdAndDeletedAtIsNull(
                        bookingId, TenantContext.getRequiredTenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        return toResponse(booking);
    }

    @Transactional
    public BookingResponse updateBookingStatus(Long bookingId, BookingStatus status) {
        requireFoodOrderTenant();

        Booking booking = bookingRepository.findByIdAndTenantIdAndDeletedAtIsNull(
                        bookingId, TenantContext.getRequiredTenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        BookingAuditSnapshot oldSnapshot = toAuditSnapshot(booking);

        booking.setStatus(status);
        booking.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        Booking savedBooking = bookingRepository.save(booking);
        auditLogService.logAction(
                savedBooking.getTenant().getId(),
                ENTITY_NAME,
                savedBooking.getId(),
                "UPDATE_STATUS",
                auditLogService.currentActorId(),
                oldSnapshot,
                toAuditSnapshot(savedBooking)
        );

        return toResponse(savedBooking);
    }

    private Tenant requireFoodOrderTenant() {
        Tenant tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new IllegalStateException("Tenant context is not available");
        }
        if (tenant.getType() != TenantType.FOOD_ORDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant does not support food ordering");
        }
        return tenant;
    }

    private void validateDeliveryDate(LocalDate deliveryDate, Tenant tenant) {
        LocalDate earliestAllowedDate = tenantTimeService.earliestDeliveryDate(tenant, loadTenantConfig(tenant.getId()));
        if (deliveryDate.isBefore(earliestAllowedDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Delivery date must be on or after " + earliestAllowedDate
            );
        }
    }

    private Map<String, String> loadTenantConfig(Long tenantId) {
        return tenantConfigRepository.findByTenantId(tenantId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        TenantConfig::getKey,
                        TenantConfig::getValue,
                        (left, right) -> right
                ));
    }

    private BookingItem toBookingItem(Booking booking, BookingItemRequest item, Long tenantId) {
        CatalogService service = catalogServiceRepository.findByIdAndTenantIdAndActiveTrueAndDeletedAtIsNull(
                        item.serviceId(), tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service not found"));

        if (service.getPrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service price is missing");
        }

        BookingItem bookingItem = new BookingItem();
        bookingItem.setBooking(booking);
        bookingItem.setService(service);
        bookingItem.setQuantity(item.quantity());
        bookingItem.setUnitPrice(service.getPrice());
        return bookingItem;
    }

    private BookingResponse toResponse(Booking booking) {
        return toResponse(booking, bookingItemRepository.findByBookingIdOrderByIdAsc(booking.getId()));
    }

    private BookingResponse toResponse(Booking booking, List<BookingItem> items) {
        List<BookingItemResponse> itemResponses = items.stream()
                .map(item -> new BookingItemResponse(
                        item.getService().getName(),
                        item.getQuantity(),
                        item.getUnitPrice()))
                .toList();

        return new BookingResponse(
                booking.getId(),
                booking.getType(),
                booking.getStatus(),
                booking.getCustomerName(),
                booking.getTotalPrice(),
                booking.getDeliveryDate(),
                booking.getNote(),
                itemResponses,
                booking.getCreatedAt()
        );
    }

    private BookingAuditSnapshot toAuditSnapshot(Booking booking) {
        return toAuditSnapshot(booking, bookingItemRepository.findByBookingIdOrderByIdAsc(booking.getId()));
    }

    private BookingAuditSnapshot toAuditSnapshot(Booking booking, List<BookingItem> items) {
        List<BookingItemAuditSnapshot> itemSnapshots = items.stream()
                .map(item -> new BookingItemAuditSnapshot(
                        item.getService().getId(),
                        item.getService().getName(),
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
                .toList();

        return new BookingAuditSnapshot(
                booking.getId(),
                booking.getTenant().getId(),
                booking.getType(),
                booking.getTelegramUserId(),
                booking.getCustomerName(),
                booking.getCustomerPhone(),
                booking.getStatus(),
                booking.getNote(),
                booking.getTotalPrice(),
                booking.getDeliveryDate(),
                booking.getDeletedAt(),
                itemSnapshots
        );
    }

    private record BookingAuditSnapshot(
            Long id,
            Long tenantId,
            BookingType type,
            Long telegramUserId,
            String customerName,
            String customerPhone,
            BookingStatus status,
            String note,
            BigDecimal totalPrice,
            LocalDate deliveryDate,
            OffsetDateTime deletedAt,
            List<BookingItemAuditSnapshot> items
    ) {
    }

    private record BookingItemAuditSnapshot(
            Long serviceId,
            String serviceName,
            int quantity,
            BigDecimal unitPrice
    ) {
    }
}
