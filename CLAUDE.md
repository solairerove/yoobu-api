# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
mvn clean install

# Run all tests (unit + integration)
mvn clean verify

# Run unit tests only
mvn test

# Run a single unit test class
mvn test -Dtest=BookingServiceTest

# Run a single integration test class
mvn verify -Dit.test=BookingLifecycleIT

# Run application locally (requires PostgreSQL)
mvn spring-boot:run

# Start PostgreSQL via Docker
docker-compose up -d
```

## Architecture

Java 21 / Spring Boot multi-tenant SaaS API for Telegram commerce (food ordering and e-commerce). Uses PostgreSQL with Flyway migrations.

### Multi-Tenancy

Every request is scoped to a tenant via `TenantContextFilter`, which extracts the slug from the URL path (`/t/{slug}/...` for Telegram endpoints, `/admin/{slug}/...` for admin endpoints) and stores it in a `ThreadLocal`-based `TenantContext`. All repositories implicitly filter by tenant ID.

### Package Layout

```
com.yoobu.api/
├── tenant/     # Tenant entity, TenantContext (ThreadLocal), settings, timezone
├── booking/    # Booking lifecycle (NEW → ACCEPTED → COMPLETED/CANCELLED)
├── catalog/    # Product/service catalog per tenant
├── admin/      # Admin panel controllers + Thymeleaf templates
├── audit/      # AuditLogService – logs CREATE/UPDATE/ACTION events with snapshots
├── security/   # BasicAuth filters for superadmin and tenant-admin; TelegramInitData validator
├── telegram/   # TelegramUser principal, @TelegramPrincipal resolver
└── config/     # Spring beans, MapStruct config
```

### Key Patterns

- **Lombok + MapStruct**: All entities use `@RequiredArgsConstructor` for DI; DTO ↔ entity conversions use MapStruct mappers configured via `MapStructConfig`.
- **Test layers**: Unit tests (`*Test.java`, surefire) mock repositories directly. Integration tests (`*IT.java`, failsafe) spin up a real PostgreSQL via Testcontainers; extend `IntegrationTestSupport` which provides `MockMvc`, `ObjectMapper`, `JdbcTemplate`, and Basic-Auth helpers.
- **Audit trail**: Call `AuditLogService` when mutating entities — it records actor, old/new snapshots, and event type.
- **Optimistic locking**: `Booking` has a `@Version` field; handle `OptimisticLockingFailureException` in concurrent update paths.

### Security

Two independent BasicAuth filter chains:
- `SuperAdminBasicAuthenticationFilter` guards `/superadmin/**`
- `TenantBasicAuthenticationFilter` guards `/admin/{slug}/**`

Telegram WebApp requests are authenticated via `TelegramInitDataValidator` (HMAC-SHA256 of `init_data`).

### Database

Schema is managed by Flyway (`src/main/resources/db/migration/V*.sql`). `spring.jpa.hibernate.ddl-auto=validate` — never let Hibernate alter the schema.

### Required Environment Variables

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL for PostgreSQL |
| `DB_USER` / `DB_PASS` | Database credentials |
| `SUPERADMIN_USER` / `SUPERADMIN_PASS` | Super-admin BasicAuth credentials |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | CORS origins (defaults to Railway wildcard) |
