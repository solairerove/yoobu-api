---
apply: always
---

# Project Rules — yoobu-api

## Identity

- Java 21, Spring Boot 4.x, Spring WebMVC, PostgreSQL, Flyway, Thymeleaf admin panels.
- Multi-tenant SaaS. Every business row is scoped by `tenant_id`.
- Current production flow: `FOOD_ORDER` only. `APPOINTMENT` and `CATALOG_REQUEST` are designed but not implemented.
- Security: custom `OncePerRequestFilter` classes, not Spring `AuthenticationProvider`.
- Telegram Mini App auth via HMAC-SHA256 (`X-Telegram-Init-Data` header).
- Dev profile accepts `X-Telegram-User-Id` header as fallback.
- Mapping stack: MapStruct is used for entity→DTO mapping.
- Lombok is used across entities/services/config classes.
- CORS is implemented and configured via `CorsConfig` + `app.cors.*` properties.

## Non-Negotiable

### Fix root causes. Never suppress.

- Do not add `@SuppressWarnings`, `noinspection`, or `// TODO: fix later` to make a problem disappear.
- Do not catch and swallow exceptions. If you catch, either handle meaningfully or rethrow.
- Do not weaken validation to make a test pass. Fix the test or the code, not the constraint.
- If a compiler warning appears, resolve the underlying issue. If resolution is genuinely impossible, explain why in a comment next to the suppression.
- If a test is flaky, find the race condition or state leak. Do not add `@RepeatedTest` or `Thread.sleep` as a band-aid.

### Always write tests.

- Every new public endpoint gets an integration test.
- Every new service method with branching logic gets a unit test or integration test covering each branch.
- Every bug fix includes a regression test that fails before the fix and passes after.
- Tests use Testcontainers with PostgreSQL. No H2, no mocks for repository layer.
- Test class naming: `*IT` for integration tests, `*Test` for unit tests.
- Existing test classes and their scope (reference, do not duplicate coverage):
    - `SuperAdminTenantControllerIT` — superadmin auth, tenant CRUD, slug availability
    - `SuperAdminAuditControllerIT` — audit query/export endpoint behavior
    - `AuditLogIndexPlanIT` — audit index usage expectations
    - `TenantAdminAccessIT` — tenant admin auth, superadmin cross-access
    - `TenantIsolationIT` — cross-tenant data isolation
    - `TenantAdminCatalogAndBookingIT` — happy path service→catalog→booking
    - `TenantFoodOrderConstraintsIT` — FOOD_ORDER-only restrictions
    - `BookingLifecycleIT` — full booking lifecycle, audit log
    - `BookingOptimisticLockingIT` — optimistic lock conflict behavior
    - `ServiceManagementAndValidationIT` — service CRUD, validation, delivery-date cutoff
    - `CorsConfigurationIT` — configured CORS headers and preflight behavior
    - `AdminPanelIT` — Thymeleaf admin routes
    - `SuperAdminPanelIT` — Thymeleaf superadmin routes
    - `TenantContextTest` — tenant context lifecycle
    - `TenantSettingsTest` / `TenantSettingsServiceTest` — tenant settings and defaults
    - `TenantTimeServiceTest` — tenant-local date/cutoff calculations

## Architecture Rules

### Tenant isolation is the most critical correctness requirement.

- Tenant-scoped business queries MUST filter by `tenant_id`.
- Read tenant from `TenantContext.getCurrentTenant()`, never from request parameters.
- If you add a new repository method, it must accept `Tenant` or `Long tenantId` and filter by it.
- Superadmin/global operations (for example tenant management and cross-tenant audit search) are explicit exceptions and must stay limited to those use cases.
- Tenant-scoped entities typically use `@ManyToOne Tenant tenant`; infrastructure tables may store `tenant_id` as scalar when relationship loading is unnecessary.
- Cross-tenant data access is a security vulnerability, not a bug.

### Database and schema

- Schema changes go through Flyway migrations only. Never `ddl-auto: update`.
- Migration naming: `V{next}__description.sql`. Check existing versions before creating.
- Current migrations: V1 (baseline), V2 (audit actor_id VARCHAR), V3 (service status model), V4 (audit filter indexes), V5 (booking optimistic lock version column).
- SQL is the source of truth for constraints, defaults, and indexes. Entity annotations may lag behind — this is acceptable but do not introduce new drift.
- Soft delete via `status = DELETED` + `deleted_at` for services. No hard deletes in production.
- All timestamps stored in UTC. Convert to tenant timezone via `TenantTimeService` in application layer.
- `booking_item.unit_price` is copied from `service.price` at order time. Never reference live service price for existing bookings.
- Booking uses optimistic locking via `booking.version` (`@Version` + DB column).

### Security model

- Four filter chains in `SecurityConfig`, ordered by specificity:
    1. `/superadmin/**` — `SuperAdminBasicAuthenticationFilter`
    2. `/admin/*/**` — `TenantContextFilter` → `TenantBasicAuthenticationFilter`
    3. `/t/*/**` — `TenantContextFilter`, Telegram auth on controller params only
    4. `/**` — permit all
- CSRF is disabled on all chains.
- CORS is enabled on all chains via `.cors(Customizer.withDefaults())`.
- Superadmin credentials are accepted on tenant admin endpoints (operational necessity).
- Do not introduce Spring `AuthenticationProvider`. The custom filter approach is a deliberate decision.

### Booking creation

- `booking.type` is assigned server-side (`ORDER`) for the current FOOD_ORDER flow. Client never sends type.
- Non-FOOD_ORDER tenants are rejected with 400 in service layer.
- `booking.total_price` is computed server-side from `sum(booking_item.unit_price * quantity)`.
- `deliveryDate` is validated against tenant-local earliest allowed date via `TenantTimeService`.
- `booking.slot_id` and `booking.service_id` exist in schema/entity but are unused by current FOOD_ORDER flow.

### Service status model

- `ServiceStatus` enum: `ACTIVE`, `INACTIVE`, `DELETED`.
- `DELETED` is rejected with 400 on create/update endpoints. Delete must use the delete endpoint.
- Public `GET /t/{slug}/services` returns only `status = ACTIVE`.
- Admin `GET /admin/{slug}/services` returns all non-deleted.

### Audit logging

- `AuditLogService` logs `CREATE`, `UPDATE`, `UPDATE_STATUS`, `DELETE`, `CANCEL` (and additional explicit actions when needed).
- `actor_id` is `VARCHAR(255)` — can be telegram user id or admin username.
- `old_value` and `new_value` are JSON strings.
- If you add a new state-changing operation, add an audit log entry.
- Superadmin audit APIs support filtered search and CSV export (`/superadmin/audit`, `/superadmin/audit/export`).

## Code Style

- DTOs are records or plain classes in `dto/` subpackages. Do not use entity classes in controller signatures.
- Controllers delegate to service classes. No business logic in controllers.
- Service classes read tenant from `TenantContext`. Controllers do not pass tenant as a parameter.
- Use `@Valid` on request body parameters. Bean validation is the first line of defense.
- Return proper HTTP status codes: 201 for create, 204 for delete, 400 for validation, 404 for not found, 409 for conflict.
- Prefer existing stack consistency: use Lombok and MapStruct where already established in the package.
- Do not introduce a third mapping/codegen framework.

## What NOT to implement

- `APPOINTMENT` and `CATALOG_REQUEST` flows — schema designed, deferred.
- `slot` and `staff` tables — planned for APPOINTMENT phase.
- Telegram notifications (`TelegramNotifier`) — not started yet.
- Daily summary scheduler — blocked by notifications.
- A new auth model with `AuthenticationProvider`/JWT replacing current filter model (unless explicitly requested).
- Online payments — out of scope for MVP.
- PostgreSQL Row Level Security — out of scope for MVP.

## When in doubt

- If a field exists in schema/entity but is unused by controllers/services, it is intentionally reserved for a future flow. Do not remove it or build on it without context.
