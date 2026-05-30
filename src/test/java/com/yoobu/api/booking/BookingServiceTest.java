package com.yoobu.api.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoobu.api.audit.AuditLogRepository;
import com.yoobu.api.audit.AuditLogService;
import com.yoobu.api.notification.NotificationOutboxService;
import com.yoobu.api.booking.dto.BookingItemRequest;
import com.yoobu.api.booking.dto.BookingItemResponse;
import com.yoobu.api.booking.dto.BookingResponse;
import com.yoobu.api.booking.dto.CreateBookingRequest;
import com.yoobu.api.catalog.CatalogService;
import com.yoobu.api.catalog.CatalogServiceRepository;
import com.yoobu.api.catalog.ServiceStatus;
import com.yoobu.api.tenant.Tenant;
import com.yoobu.api.tenant.TenantConfig;
import com.yoobu.api.tenant.TenantConfigKeys;
import com.yoobu.api.tenant.TenantConfigRepository;
import com.yoobu.api.tenant.TenantContext;
import com.yoobu.api.tenant.TenantSettings;
import com.yoobu.api.tenant.TenantSettingsService;
import com.yoobu.api.tenant.TenantTimeService;
import com.yoobu.api.tenant.TenantType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;

import static org.mockito.Mockito.mock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingServiceTest {

    private final RecordingAuditLogService auditLogService = new RecordingAuditLogService();
    private final BookingRepositoryStub bookingRepository = new BookingRepositoryStub();
    private final BookingItemRepositoryStub bookingItemRepository = new BookingItemRepositoryStub();
    private final BookingMapper bookingMapper = mapper();
    private final CatalogServiceRepositoryStub catalogServiceRepository = new CatalogServiceRepositoryStub();
    private final StubTenantSettingsService tenantSettingsService = new StubTenantSettingsService();
    private final StubTenantTimeService tenantTimeService = new StubTenantTimeService();

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                auditLogService,
                bookingRepository.proxy,
                bookingItemRepository.proxy,
                bookingMapper,
                catalogServiceRepository.proxy,
                mock(com.yoobu.api.catalog.ProductVariantRepository.class),
                tenantSettingsService,
                tenantTimeService,
                mock(NotificationOutboxService.class)
        );
        TenantContext.setCurrentTenant(foodTenant(77L));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createFoodOrderCalculatesTotalAndTrimsConfiguredCurrency() {
        LocalDate deliveryDate = LocalDate.of(2026, 3, 21);
        tenantSettingsService.settings = TenantSettings.fromMap(Map.of(TenantConfigKeys.CURRENCY, "  EUR  "));
        tenantTimeService.earliestDate = deliveryDate.minusDays(1);
        catalogServiceRepository.servicesById.put(11L, service(11L, "Pizza", "10.00"));
        catalogServiceRepository.servicesById.put(12L, service(12L, "Soup", "3.50"));

        CreateBookingRequest request = new CreateBookingRequest(
                "Alex",
                "+1000000000",
                "Main st. 1",
                deliveryDate,
                "no onions",
                List.of(new BookingItemRequest(11L, 2), new BookingItemRequest(12L, 3))
        );

        BookingResponse response = bookingService.createFoodOrder(request, 5000L);

        assertEquals(BookingStatus.NEW, response.status());
        assertEquals("EUR", response.currency());
        assertEquals(new BigDecimal("30.50"), response.totalPrice());
        assertEquals(1, auditLogService.logCreateCalls);
        assertEquals("booking", auditLogService.lastEntity);
        assertEquals("5000", auditLogService.lastActorId);
    }

    @Test
    void updateBookingStatusRejectsInvalidTransition() {
        bookingRepository.lookupBooking = booking(42L, BookingStatus.NEW);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.updateBookingStatus(42L, BookingStatus.DONE)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Invalid booking status transition"));
        assertFalse(bookingRepository.saveAndFlushCalled);
    }

    @Test
    void updateBookingStatusToDeliveringStoresTrackingUrl() {
        bookingRepository.lookupBooking = booking(42L, BookingStatus.CONFIRMED);

        BookingResponse response = bookingService.updateBookingStatus(
                42L,
                BookingStatus.DELIVERING,
                " https://grab.example.com/track/abc-1 "
        );

        assertEquals(BookingStatus.DELIVERING, response.status());
        assertEquals("https://grab.example.com/track/abc-1", response.trackingUrl());
        assertTrue(bookingRepository.saveAndFlushCalled);
    }

    @Test
    void updateBookingStatusCanSetTrackingWithoutChangingStatus() {
        bookingRepository.lookupBooking = booking(42L, BookingStatus.DELIVERING);

        BookingResponse response = bookingService.updateBookingStatus(
                42L,
                BookingStatus.DELIVERING,
                "https://grab.example.com/track/abc-2"
        );

        assertEquals(BookingStatus.DELIVERING, response.status());
        assertEquals("https://grab.example.com/track/abc-2", response.trackingUrl());
        assertTrue(bookingRepository.saveAndFlushCalled);
    }

    @Test
    void updateBookingStatusWithNullTrackingDoesNotOverwriteExistingValue() {
        Booking booking = booking(42L, BookingStatus.DELIVERING);
        booking.setTrackingUrl("https://grab.example.com/track/existing");
        bookingRepository.lookupBooking = booking;

        BookingResponse response = bookingService.updateBookingStatus(42L, BookingStatus.DONE, null);

        assertEquals(BookingStatus.DONE, response.status());
        assertEquals("https://grab.example.com/track/existing", response.trackingUrl());
        assertTrue(bookingRepository.saveAndFlushCalled);
    }

    @Test
    void updateBookingStatusWithBlankTrackingClearsValue() {
        Booking booking = booking(42L, BookingStatus.DELIVERING);
        booking.setTrackingUrl("https://grab.example.com/track/existing");
        bookingRepository.lookupBooking = booking;

        BookingResponse response = bookingService.updateBookingStatus(42L, BookingStatus.DONE, "   ");

        assertEquals(BookingStatus.DONE, response.status());
        assertNull(response.trackingUrl());
        assertTrue(bookingRepository.saveAndFlushCalled);
    }

    @Test
    void updateBookingStatusRejectsInvalidTrackingUrl() {
        bookingRepository.lookupBooking = booking(42L, BookingStatus.CONFIRMED);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.updateBookingStatus(42L, BookingStatus.DELIVERING, "grab://tracking")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Tracking URL must use http or https", exception.getReason());
        assertFalse(bookingRepository.saveAndFlushCalled);
    }

    @Test
    void getAdminBookingsPageNormalizesInvalidPageAndTooLargeSize() {
        bookingService.getAdminBookingsPage(null, null, -3, 1000);

        assertEquals("findByTenantIdAndDeletedAtIsNull", bookingRepository.lastPageQueryMethod);
        assertEquals(0, bookingRepository.lastPageable.getPageNumber());
        assertEquals(100, bookingRepository.lastPageable.getPageSize());
        assertEquals("createdAt: DESC", bookingRepository.lastPageable.getSort().toString());
    }

    @Test
    void getAdminBookingsPageUsesCombinedFilterWhenBothProvided() {
        LocalDate deliveryDate = LocalDate.of(2026, 3, 25);

        bookingService.getAdminBookingsPage(BookingStatus.CONFIRMED, deliveryDate, 1, 0);

        assertEquals(
                "findByTenantIdAndDeletedAtIsNullAndStatusAndDeliveryDate",
                bookingRepository.lastPageQueryMethod
        );
        assertEquals(1, bookingRepository.lastPageable.getPageNumber());
        assertEquals(10, bookingRepository.lastPageable.getPageSize());
    }

    @Test
    void getAllowedAdminStatusesForCancelledContainsOnlyCancelled() {
        assertEquals(List.of(BookingStatus.CANCELLED), bookingService.getAllowedAdminStatuses(BookingStatus.CANCELLED));
    }

    @Test
    void getAllowedAdminStatusesForConfirmedRequiresDeliveringBeforeDone() {
        List<BookingStatus> statuses = bookingService.getAllowedAdminStatuses(BookingStatus.CONFIRMED);

        assertTrue(statuses.contains(BookingStatus.DELIVERING));
        assertFalse(statuses.contains(BookingStatus.DONE));
    }

    @Test
    void getAnalyticsAssemblesStatsFromRepositoryCalls() {
        bookingRepository.stubbedCount = 4;
        bookingRepository.stubbedRevenue = new BigDecimal("99.50");
        tenantSettingsService.settings = TenantSettings.fromMap(Map.of(TenantConfigKeys.CURRENCY, "EUR"));

        var analytics = bookingService.getAnalytics();

        assertEquals(4, analytics.today().orderCount());
        assertEquals(new BigDecimal("99.50"), analytics.today().revenue());
        assertEquals("EUR", analytics.today().currency());
        assertEquals(4, analytics.week().orderCount());
        assertEquals(4, analytics.month().orderCount());
        assertTrue(analytics.topBuyers().isEmpty());
    }

    @Test
    void getAnalyticsFallsBackToZeroRevenueWhenRepositoryReturnsNull() {
        bookingRepository.stubbedCount = 0;
        bookingRepository.stubbedRevenue = null;

        var analytics = bookingService.getAnalytics();

        assertEquals(BigDecimal.ZERO, analytics.today().revenue());
        assertEquals(BigDecimal.ZERO, analytics.week().revenue());
        assertEquals(BigDecimal.ZERO, analytics.month().revenue());
    }

    private static BookingMapper mapper() {
        return (BookingMapper) Proxy.newProxyInstance(
                BookingMapper.class.getClassLoader(),
                new Class<?>[]{BookingMapper.class},
                (proxy, method, args) -> {
                    if ("toResponse".equals(method.getName()) && args.length == 2) {
                        Booking booking = (Booking) args[0];
                        @SuppressWarnings("unchecked")
                        List<BookingItem> items = (List<BookingItem>) args[1];
                        return new BookingResponse(
                                booking.getId(),
                                booking.getType(),
                                booking.getStatus(),
                                booking.getTrackingUrl(),
                                booking.getCustomerName(),
                                booking.getCustomerPhone(),
                                booking.getDeliveryAddress(),
                                booking.getTotalPrice(),
                                booking.getCurrency(),
                                booking.getDeliveryDate(),
                                booking.getNote(),
                                items.stream()
                                        .map(item -> new BookingItemResponse(
                                                item.getService().getName(),
                                                item.getQuantity(),
                                                item.getUnitPrice(),
                                                item.getVariantSize(),
                                                item.getVariantColor()
                                        ))
                                        .toList(),
                                booking.getCreatedAt(),
                                booking.getPaymentQrUrl()
                        );
                    }
                    if ("toString".equals(method.getName())) {
                        return "BookingMapperTestDouble";
                    }
                    throw new UnsupportedOperationException("Unexpected call: " + method.getName());
                }
        );
    }

    private static Booking booking(Long id, BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setTenant(foodTenant(77L));
        booking.setStatus(status);
        booking.setType(BookingType.ORDER);
        booking.setCurrency("USD");
        booking.setCreatedAt(OffsetDateTime.now());
        booking.setUpdatedAt(OffsetDateTime.now());
        return booking;
    }

    private static CatalogService service(Long id, String name, String price) {
        CatalogService service = instantiate(CatalogService.class);
        service.setId(id);
        service.setName(name);
        service.setPrice(new BigDecimal(price));
        return service;
    }

    private static Tenant foodTenant(Long id) {
        Tenant tenant = instantiate(Tenant.class);
        tenant.setId(id);
        tenant.setType(TenantType.FOOD_ORDER);
        tenant.setTimezone("UTC");
        return tenant;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot instantiate " + type.getName(), ex);
        }
    }

    private static final class StubTenantTimeService extends TenantTimeService {
        private LocalDate earliestDate = LocalDate.of(2000, 1, 1);

        private StubTenantTimeService() {
            super(Clock.systemUTC());
        }

        @Override
        public LocalDate earliestDeliveryDate(Tenant tenant, TenantSettings settings) {
            return earliestDate;
        }
    }

    private static final class StubTenantSettingsService extends TenantSettingsService {
        private TenantSettings settings = TenantSettings.fromMap(Map.of());

        private StubTenantSettingsService() {
            super(emptyTenantConfigRepository());
        }

        @Override
        public TenantSettings getSettings(Long tenantId) {
            return settings;
        }
    }

    private static TenantConfigRepository emptyTenantConfigRepository() {
        return (TenantConfigRepository) Proxy.newProxyInstance(
                TenantConfigRepository.class.getClassLoader(),
                new Class<?>[]{TenantConfigRepository.class},
                (proxy, method, args) -> {
                    if ("findByTenantId".equals(method.getName())) {
                        return List.<TenantConfig>of();
                    }
                    if ("toString".equals(method.getName())) {
                        return "TenantConfigRepositoryTestDouble";
                    }
                    throw new UnsupportedOperationException("Unexpected call: " + method.getName());
                }
        );
    }

    private static final class RecordingAuditLogService extends AuditLogService {
        private int logCreateCalls;
        private String lastEntity;
        private String lastActorId;

        private RecordingAuditLogService() {
            super(noopAuditRepository(), new ObjectMapper());
        }

        @Override
        public void logCreate(Long tenantId, String entity, Long entityId, String actorId, Object newValue) {
            logCreateCalls++;
            lastEntity = entity;
            lastActorId = actorId;
        }

        @Override
        public void logAction(
                Long tenantId,
                String entity,
                Long entityId,
                String action,
                String actorId,
                Object oldValue,
                Object newValue
        ) {
            // no-op for these tests
        }

        @Override
        public String currentActorId() {
            return "admin-1";
        }
    }

    private static AuditLogRepository noopAuditRepository() {
        return (AuditLogRepository) Proxy.newProxyInstance(
                AuditLogRepository.class.getClassLoader(),
                new Class<?>[]{AuditLogRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        return args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "AuditLogRepositoryTestDouble";
                    }
                    throw new UnsupportedOperationException("Unexpected call: " + method.getName());
                }
        );
    }

    private static final class BookingRepositoryStub {
        private Booking lookupBooking;
        private boolean saveAndFlushCalled;
        private String lastPageQueryMethod;
        private Pageable lastPageable;
        private long stubbedCount = 0;
        private BigDecimal stubbedRevenue = BigDecimal.ZERO;
        private List<Object[]> stubbedTopBuyers = List.of();
        private final BookingRepository proxy = (BookingRepository) Proxy.newProxyInstance(
                BookingRepository.class.getClassLoader(),
                new Class<?>[]{BookingRepository.class},
                (obj, method, args) -> switch (method.getName()) {
                    case "save" -> save((Booking) args[0]);
                    case "saveAndFlush" -> saveAndFlush((Booking) args[0]);
                    case "findByIdAndTenantIdAndDeletedAtIsNull" -> Optional.ofNullable(lookupBooking);
                    case "findByTenantIdAndDeletedAtIsNull" -> capturePage(method.getName(), (Pageable) args[1]);
                    case "findByTenantIdAndDeletedAtIsNullAndStatus" -> capturePage(method.getName(), (Pageable) args[2]);
                    case "findByTenantIdAndDeletedAtIsNullAndDeliveryDate" -> capturePage(method.getName(), (Pageable) args[2]);
                    case "findByTenantIdAndDeletedAtIsNullAndStatusAndDeliveryDate" ->
                            capturePage(method.getName(), (Pageable) args[3]);
                    case "countBookingsInPeriod" -> stubbedCount;
                    case "sumRevenueInPeriod" -> stubbedRevenue;
                    case "findTopBuyersRaw" -> stubbedTopBuyers;
                    case "toString" -> "BookingRepositoryStub";
                    default -> throw new UnsupportedOperationException("Unexpected call: " + method.getName());
                }
        );

        private Booking save(Booking booking) {
            if (booking.getId() == null) {
                booking.setId(900L);
            }
            return booking;
        }

        private Booking saveAndFlush(Booking booking) {
            saveAndFlushCalled = true;
            return booking;
        }

        private Page<Booking> capturePage(String methodName, Pageable pageable) {
            lastPageQueryMethod = methodName;
            lastPageable = pageable;
            return new PageImpl<>(List.of(), pageable, 0);
        }
    }

    private static final class BookingItemRepositoryStub {
        private final Map<Long, List<BookingItem>> itemsByBookingId = new LinkedHashMap<>();
        private final BookingItemRepository proxy = (BookingItemRepository) Proxy.newProxyInstance(
                BookingItemRepository.class.getClassLoader(),
                new Class<?>[]{BookingItemRepository.class},
                (obj, method, args) -> switch (method.getName()) {
                    case "saveAll" -> saveAll(args[0]);
                    case "findByBookingIdOrderByIdAsc" -> itemsByBookingId.getOrDefault((Long) args[0], List.of());
                    case "findByBookingIdInOrderByBookingIdAscIdAsc" -> findByBookingIds(args[0]);
                    case "toString" -> "BookingItemRepositoryStub";
                    default -> throw new UnsupportedOperationException("Unexpected call: " + method.getName());
                }
        );

        @SuppressWarnings("unchecked")
        private List<BookingItem> saveAll(Object arg) {
            List<BookingItem> items = (List<BookingItem>) arg;
            if (!items.isEmpty()) {
                itemsByBookingId.put(items.getFirst().getBooking().getId(), new ArrayList<>(items));
            }
            return items;
        }

        @SuppressWarnings("unchecked")
        private List<BookingItem> findByBookingIds(Object arg) {
            List<Long> ids = (List<Long>) arg;
            List<BookingItem> result = new ArrayList<>();
            for (Long id : ids) {
                result.addAll(itemsByBookingId.getOrDefault(id, List.of()));
            }
            return result;
        }
    }

    private static final class CatalogServiceRepositoryStub {
        private final Map<Long, CatalogService> servicesById = new LinkedHashMap<>();
        private final CatalogServiceRepository proxy = (CatalogServiceRepository) Proxy.newProxyInstance(
                CatalogServiceRepository.class.getClassLoader(),
                new Class<?>[]{CatalogServiceRepository.class},
                (obj, method, args) -> {
                    if ("findByIdAndTenantIdAndStatus".equals(method.getName())) {
                        Long serviceId = (Long) args[0];
                        Long tenantId = (Long) args[1];
                        ServiceStatus status = (ServiceStatus) args[2];
                        if (!Long.valueOf(77L).equals(tenantId) || status != ServiceStatus.ACTIVE) {
                            return Optional.empty();
                        }
                        return Optional.ofNullable(servicesById.get(serviceId));
                    }
                    if ("toString".equals(method.getName())) {
                        return "CatalogServiceRepositoryStub";
                    }
                    throw new UnsupportedOperationException("Unexpected call: " + method.getName());
                }
        );
    }
}
