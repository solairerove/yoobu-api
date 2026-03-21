package com.yoobu.api.booking;

import com.yoobu.api.audit.AuditLogService;
import com.yoobu.api.booking.dto.BookingItemRequest;
import com.yoobu.api.booking.dto.BookingResponse;
import com.yoobu.api.booking.dto.CreateBookingRequest;
import com.yoobu.api.catalog.CatalogService;
import com.yoobu.api.catalog.CatalogServiceRepository;
import com.yoobu.api.tenant.Tenant;
import com.yoobu.api.tenant.TenantContext;
import com.yoobu.api.tenant.TenantSettings;
import com.yoobu.api.tenant.TenantSettingsService;
import com.yoobu.api.tenant.TenantTimeService;
import com.yoobu.api.tenant.TenantType;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final String ENTITY_NAME = "booking";

    private final AuditLogService auditLogService;
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingMapper bookingMapper;
    private final CatalogServiceRepository catalogServiceRepository;
    private final TenantSettingsService tenantSettingsService;
    private final TenantTimeService tenantTimeService;

    @Transactional
    public BookingResponse createFoodOrder(CreateBookingRequest request, Long telegramUserId) {
        Tenant tenant = requireFoodOrderTenant();
        TenantSettings tenantSettings = tenantSettingsService.getSettings(tenant.getId());
        validateDeliveryDate(request.deliveryDate(), tenant, tenantSettings);
        String currency = resolveBookingCurrency(tenantSettings);
        OffsetDateTime now = nowUtc();

        Booking booking = new Booking();
        booking.setTenant(tenant);
        booking.setType(BookingType.ORDER);
        booking.setTelegramUserId(telegramUserId);
        booking.setCustomerName(request.customerName());
        booking.setCustomerPhone(request.customerPhone());
        booking.setDeliveryAddress(request.deliveryAddress());
        booking.setStatus(BookingStatus.NEW);
        booking.setNote(request.note());
        booking.setDeliveryDate(request.deliveryDate());
        booking.setCreatedAt(now);
        booking.setUpdatedAt(now);

        Booking savedBooking = bookingRepository.save(booking);

        List<BookingItem> bookingItems = request.items().stream()
                .map(item -> toBookingItem(savedBooking, item, tenant.getId(), currency))
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
                telegramUserId.toString(),
                toAuditSnapshot(persistedBooking, bookingItems)
        );

        return toResponse(persistedBooking, bookingItems);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(Long telegramUserId) {
        requireFoodOrderTenant();

        List<Booking> bookings = bookingRepository.findByTenantIdAndTelegramUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                TenantContext.getRequiredTenantId(),
                telegramUserId
        );
        return toResponses(bookings);
    }

    @Transactional(readOnly = true)
    public BookingResponse getMyBooking(Long bookingId, Long telegramUserId) {
        requireFoodOrderTenant();
        return toResponse(findCustomerBooking(bookingId, telegramUserId));
    }

    @Transactional
    public BookingResponse cancelMyBooking(Long bookingId, Long telegramUserId) {
        requireFoodOrderTenant();
        Booking booking = findCustomerBooking(bookingId, telegramUserId);

        if (booking.getStatus() == BookingStatus.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed booking cannot be cancelled");
        }

        Map<String, Object> oldSnapshot = toAuditSnapshot(booking);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setUpdatedAt(nowUtc());

        Booking savedBooking = persistBookingWithConflictGuard(booking);
        auditLogService.logAction(
                savedBooking.getTenant().getId(),
                ENTITY_NAME,
                savedBooking.getId(),
                "CANCEL",
                telegramUserId.toString(),
                oldSnapshot,
                toAuditSnapshot(savedBooking)
        );

        return toResponse(savedBooking);
    }

    @Transactional
    public BookingResponse confirmMyBookingPayment(Long bookingId, Long telegramUserId) {
        requireFoodOrderTenant();
        Booking booking = findCustomerBooking(bookingId, telegramUserId);

        if (booking.getStatus() != BookingStatus.NEW) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment can only be confirmed for booking in NEW status"
            );
        }

        Map<String, Object> oldSnapshot = toAuditSnapshot(booking);
        booking.setStatus(BookingStatus.PAYMENT_PENDING);
        booking.setUpdatedAt(nowUtc());

        Booking savedBooking = persistBookingWithConflictGuard(booking);
        auditLogService.logAction(
                savedBooking.getTenant().getId(),
                ENTITY_NAME,
                savedBooking.getId(),
                "CONFIRM_PAYMENT",
                telegramUserId.toString(),
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

        return toResponses(bookings);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getAdminBookingsPage(BookingStatus status, LocalDate deliveryDate, int page, int size) {
        requireFoodOrderTenant();

        Long tenantId = TenantContext.getRequiredTenantId();
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizePageSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<Booking> bookingPage;

        if (status != null && deliveryDate != null) {
            bookingPage = bookingRepository.findByTenantIdAndDeletedAtIsNullAndStatusAndDeliveryDate(
                    tenantId, status, deliveryDate, pageable);
        } else if (status != null) {
            bookingPage = bookingRepository.findByTenantIdAndDeletedAtIsNullAndStatus(tenantId, status, pageable);
        } else if (deliveryDate != null) {
            bookingPage = bookingRepository.findByTenantIdAndDeletedAtIsNullAndDeliveryDate(tenantId, deliveryDate, pageable);
        } else {
            bookingPage = bookingRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);
        }

        List<BookingResponse> responses = toResponses(bookingPage.getContent());
        return new PageImpl<>(responses, pageable, bookingPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public BookingResponse getAdminBooking(Long bookingId) {
        requireFoodOrderTenant();
        return toResponse(findAdminBooking(bookingId));
    }

    @Transactional
    public BookingResponse updateBookingStatus(Long bookingId, BookingStatus status) {
        return updateBookingStatus(bookingId, status, null);
    }

    @Transactional
    public BookingResponse updateBookingStatus(Long bookingId, BookingStatus status, String trackingUrl) {
        requireFoodOrderTenant();
        Booking booking = findAdminBooking(bookingId);
        validateStatusTransition(booking.getStatus(), status);
        Map<String, Object> oldSnapshot = toAuditSnapshot(booking);

        booking.setStatus(status);
        if (trackingUrl != null) {
            booking.setTrackingUrl(normalizeTrackingUrl(trackingUrl));
        }
        booking.setUpdatedAt(nowUtc());

        Booking savedBooking = persistBookingWithConflictGuard(booking);
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

    public List<BookingStatus> getAllowedAdminStatuses(BookingStatus currentStatus) {
        EnumSet<BookingStatus> allowedStatuses = switch (currentStatus) {
            case NEW -> EnumSet.of(BookingStatus.NEW, BookingStatus.CANCELLED);
            case PAYMENT_PENDING -> EnumSet.of(
                    BookingStatus.PAYMENT_PENDING,
                    BookingStatus.CONFIRMED,
                    BookingStatus.CANCELLED
            );
            case CONFIRMED -> EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.DELIVERING, BookingStatus.CANCELLED);
            case DELIVERING -> EnumSet.of(BookingStatus.DELIVERING, BookingStatus.DONE, BookingStatus.CANCELLED);
            case DONE -> EnumSet.of(BookingStatus.DONE, BookingStatus.CANCELLED);
            case CANCELLED -> EnumSet.of(BookingStatus.CANCELLED);
        };
        return List.copyOf(allowedStatuses);
    }

    private Tenant requireFoodOrderTenant() {
        Tenant tenant = TenantContext.requireCurrentTenant();
        if (tenant.getType() != TenantType.FOOD_ORDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant does not support food ordering");
        }
        return tenant;
    }

    private void validateDeliveryDate(LocalDate deliveryDate, Tenant tenant, TenantSettings tenantSettings) {
        LocalDate earliestAllowedDate = tenantTimeService.earliestDeliveryDate(
                tenant,
                tenantSettings
        );
        if (deliveryDate.isBefore(earliestAllowedDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Delivery date must be on or after " + earliestAllowedDate
            );
        }
    }

    private Booking findCustomerBooking(Long bookingId, Long telegramUserId) {
        return bookingRepository.findByIdAndTenantIdAndTelegramUserIdAndDeletedAtIsNull(
                        bookingId,
                        TenantContext.getRequiredTenantId(),
                        telegramUserId
                )
                .orElseThrow(this::bookingNotFound);
    }

    private Booking findAdminBooking(Long bookingId) {
        return bookingRepository.findByIdAndTenantIdAndDeletedAtIsNull(
                        bookingId,
                        TenantContext.getRequiredTenantId()
                )
                .orElseThrow(this::bookingNotFound);
    }

    private BookingItem toBookingItem(Booking booking, BookingItemRequest item, Long tenantId, String currency) {
        CatalogService service = catalogServiceRepository.findByIdAndTenantIdAndStatus(
                        item.serviceId(), tenantId, com.yoobu.api.catalog.ServiceStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service not found"));

        if (service.getPrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service price is missing");
        }

        BookingItem bookingItem = new BookingItem();
        bookingItem.setBooking(booking);
        bookingItem.setService(service);
        bookingItem.setQuantity(item.quantity());
        bookingItem.setUnitPrice(service.getPrice());
        bookingItem.setCurrency(currency);
        return bookingItem;
    }

    private BookingResponse toResponse(Booking booking) {
        return toResponse(booking, bookingItemRepository.findByBookingIdOrderByIdAsc(booking.getId()));
    }

    private List<BookingResponse> toResponses(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return List.of();
        }

        Map<Long, List<BookingItem>> itemsByBookingId = loadItemsByBookingId(bookings);
        return bookings.stream()
                .map(booking -> toResponse(booking, itemsByBookingId.getOrDefault(booking.getId(), List.of())))
                .toList();
    }

    private BookingResponse toResponse(Booking booking, List<BookingItem> items) {
        return bookingMapper.toResponse(booking, items, resolveBookingCurrency(items));
    }

    private Map<String, Object> toAuditSnapshot(Booking booking) {
        return toAuditSnapshot(booking, bookingItemRepository.findByBookingIdOrderByIdAsc(booking.getId()));
    }

    private Map<String, Object> toAuditSnapshot(Booking booking, List<BookingItem> items) {
        List<Map<String, Object>> itemSnapshots = items.stream()
                .map(item -> {
                    Map<String, Object> itemSnapshot = new LinkedHashMap<>();
                    itemSnapshot.put("serviceId", item.getService().getId());
                    itemSnapshot.put("serviceName", item.getService().getName());
                    itemSnapshot.put("quantity", item.getQuantity());
                    itemSnapshot.put("unitPrice", item.getUnitPrice());
                    itemSnapshot.put("currency", item.getCurrency());
                    return itemSnapshot;
                })
                .toList();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", booking.getId());
        snapshot.put("tenantId", booking.getTenant().getId());
        snapshot.put("type", booking.getType());
        snapshot.put("telegramUserId", booking.getTelegramUserId());
        snapshot.put("customerName", booking.getCustomerName());
        snapshot.put("customerPhone", booking.getCustomerPhone());
        snapshot.put("deliveryAddress", booking.getDeliveryAddress());
        snapshot.put("status", booking.getStatus());
        snapshot.put("trackingUrl", booking.getTrackingUrl());
        snapshot.put("note", booking.getNote());
        snapshot.put("totalPrice", booking.getTotalPrice());
        snapshot.put("deliveryDate", booking.getDeliveryDate());
        snapshot.put("deletedAt", booking.getDeletedAt());
        snapshot.put("items", itemSnapshots);
        return snapshot;
    }

    private Map<Long, List<BookingItem>> loadItemsByBookingId(List<Booking> bookings) {
        List<Long> bookingIds = bookings.stream()
                .map(Booking::getId)
                .toList();

        return bookingItemRepository.findByBookingIdInOrderByBookingIdAscIdAsc(bookingIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        item -> item.getBooking().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
    }

    private String resolveBookingCurrency(TenantSettings settings) {
        String configuredCurrency = settings.pricing().currency();
        if (StringUtils.hasText(configuredCurrency)) {
            return configuredCurrency.trim();
        }
        return TenantSettings.DEFAULT_CURRENCY;
    }

    private String resolveBookingCurrency(List<BookingItem> items) {
        for (BookingItem item : items) {
            if (StringUtils.hasText(item.getCurrency())) {
                return item.getCurrency().trim();
            }
        }
        return TenantSettings.DEFAULT_CURRENCY;
    }

    private ResponseStatusException bookingNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
    }

    private void validateStatusTransition(BookingStatus currentStatus, BookingStatus nextStatus) {
        if (getAllowedAdminStatuses(currentStatus).contains(nextStatus)) {
            return;
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Invalid booking status transition from %s to %s".formatted(currentStatus, nextStatus)
        );
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String normalizeTrackingUrl(String trackingUrl) {
        String normalized = trackingUrl.trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tracking URL must use http or https");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tracking URL must include a host");
            }
            return normalized;
        } catch (URISyntaxException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tracking URL is invalid", ex);
        }
    }

    private Booking persistBookingWithConflictGuard(Booking booking) {
        try {
            return bookingRepository.saveAndFlush(booking);
        } catch (OptimisticLockingFailureException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking was modified by another request. Refresh and retry.",
                    ex
            );
        }
    }

    private int normalizePageSize(int requestedSize) {
        if (requestedSize < 1) {
            return 10;
        }
        return Math.min(requestedSize, 100);
    }
}
