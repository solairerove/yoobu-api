# Ecommerce Frontend Implementation — Tech Doc for Agent

## Context

The backend already fully supports an `ECOMMERCE` tenant type alongside the existing `FOOD_ORDER` type. The Angular frontend (`yoobu-web`) currently only implements the `FOOD_ORDER` flow. `ECOMMERCE` tenants are routed to an "unsupported" placeholder. This document describes exactly what to build.

The app is a Telegram Mini App — it runs inside Telegram's WebView. Authentication is via Telegram `initData` sent as a header on every request. All UI runs at `/t/:slug`.

---

## Repository Layout

```
yoobu-web/src/app/
├── app.routes.ts
├── core/
│   ├── models/
│   │   ├── service.model.ts          ← extend with variants
│   │   ├── booking.model.ts          ← extend with ecommerce types
│   │   └── tenant-config.model.ts   ← add ECOMMERCE to TenantType
│   └── services/
│       └── tenant-api.service.ts    ← add createOrder() method
├── features/
│   ├── food-order/                  ← DO NOT TOUCH, reference only
│   │   ├── food-order-home.component.ts
│   │   ├── food-order-menu.component.ts
│   │   ├── food-order-cart.component.ts
│   │   ├── food-order-cart-bar.component.ts
│   │   ├── food-order-checkout.component.ts
│   │   ├── food-order-bookings.component.ts
│   │   ├── food-order-detail-sheet.component.ts
│   │   ├── food-order-confirmation.component.ts
│   │   ├── food-order-success-card.component.ts
│   │   ├── food-order.store.ts
│   │   └── food-order-flow.facade.ts
│   └── ecommerce/                   ← CREATE THIS DIRECTORY
└── tenant/
    └── tenant-shell.component.ts    ← add ECOMMERCE routing branch
```

---

## Step 1 — Update Existing Models

### `core/models/tenant-config.model.ts`

Add `'ECOMMERCE'` to the `TenantType` union:

```typescript
export type TenantType = 'FOOD_ORDER' | 'APPOINTMENT' | 'CATALOG_REQUEST' | 'ECOMMERCE';
```

### `core/models/service.model.ts`

Add `ProductVariant` interface and extend `ServiceItem` with `variants`:

```typescript
export interface ProductVariant {
  id: number;
  size: string | null;
  color: string | null;
  price: number;
  stock: number;
  sortOrder: number;
  imageUrl: string | null;
}

export interface ServiceItem {
  id: number;
  name: string;
  description: string | null;
  imageUrl: string | null;
  price: number | null;          // nullable — ECOMMERCE services may have no price
  unit: string | null;
  durationMinutes: number | null;
  sortOrder: number;
  status: 'ACTIVE' | 'INACTIVE' | 'DELETED';
  variants: ProductVariant[];    // empty array for non-ECOMMERCE tenants
}
```

### `core/models/booking.model.ts`

Add ecommerce-specific request types and extend existing response types:

```typescript
// --- Ecommerce order creation ---
export interface EcommerceBookingItemRequest {
  variantId: number;
  quantity: number;
}

export interface CreateEcommerceOrderRequest {
  customerName: string;
  customerPhone: string;
  deliveryAddress: string;
  deliveryDate: string | null;   // ISO date string, optional for ecommerce
  note: string | null;
  items: EcommerceBookingItemRequest[];
}

// --- Extend existing BookingItem to include variant info ---
export interface BookingItem {
  serviceName: string;
  quantity: number;
  unitPrice: number;
  variantSize: string | null;    // ADD THESE TWO
  variantColor: string | null;   // ADD THESE TWO
}
```

---

## Step 2 — Update API Service

### `core/services/tenant-api.service.ts`

Add `createOrder` for ecommerce (separate endpoint from food order):

```typescript
createOrder(slug: string, request: CreateEcommerceOrderRequest): Observable<BookingResponse> {
  return this.http.post<BookingResponse>(`/api/t/${slug}/orders`, request).pipe(
    timeout(10_000),
    retry({ count: 2, delay: retryStrategy })
  );
}
```

Also add methods for reading a single order and cancelling — reuse booking endpoints since the response type is the same:

```typescript
// These already exist for food orders — same endpoints work for ecommerce too:
// getMyBookings(slug) -> GET /api/t/{slug}/bookings/my
// getBooking(slug, bookingId) -> GET /api/t/{slug}/bookings/{bookingId}
// cancelBooking(slug, bookingId) -> POST /api/t/{slug}/bookings/{bookingId}/cancel
// confirmBookingPayment(slug, bookingId) -> POST /api/t/{slug}/bookings/{bookingId}/confirm-payment
```

No new read/cancel endpoints are needed — they share `/bookings/*` with food orders.

---

## Step 3 — Update Tenant Shell

### `tenant/tenant-shell.component.ts`

The shell already handles `FOOD_ORDER`. Add a branch for `ECOMMERCE`:

```typescript
// In the switch/if that maps TenantType to a component:
case 'ECOMMERCE':
  // lazy-load EcommerceHomeComponent
  break;
```

Follow the exact same lazy-loading pattern already used for `FoodOrderHomeComponent`. The shell passes `config` and `slug` as inputs to the loaded component.

---

## Step 4 — Build the Ecommerce Feature

Create all new files under `features/ecommerce/`. Mirror the structure and patterns of `food-order/` — signals-based state, facade + store separation, standalone components with inline templates or separate `.html` files matching the project convention.

### 4a. State Store — `ecommerce.store.ts`

Manages the cart: which variant IDs are selected and their quantities.

```typescript
// Signals to expose:
servicesSignal: WritableSignal<ServiceItem[]>
quantitiesSignal: WritableSignal<Record<number, number>>  // key = variantId

// Computed signals:
selectedItems: Signal<{ service: ServiceItem; variant: ProductVariant; quantity: number }[]>
selectedCount: Signal<number>   // total item count across all variants
selectedTotal: Signal<number>   // sum of (variant.price * quantity)

// Methods:
setTenant(slug: string): void          // reset store for a new tenant
setServices(services: ServiceItem[]): void
increase(variantId: number, max?: number): void   // increment qty (cap at 9)
decrease(variantId: number): void                  // decrement qty (remove at 0)
clearCart(): void
```

Key difference from food order store: the cart key is `variantId` (not `serviceId`), because the same product can have multiple variants (Black M, Black XL, etc.) that can each be in the cart simultaneously.

Availability guard: `increase()` must check `variant.stock > 0` and not allow adding a variant where `stock === 0`. Respect the variant's own `stock` as the per-variant cap if stock < 9.

### 4b. Flow Facade — `ecommerce-flow.facade.ts`

Orchestrates views, API calls, form state, and Telegram button integration. Pattern is identical to `food-order-flow.facade.ts`.

**View enum:**
```typescript
type EcommerceView = 'catalog' | 'cart' | 'checkout' | 'confirmation' | 'orders';
```

**Signals to maintain:**
- `activeView: WritableSignal<EcommerceView>`
- `submitting: WritableSignal<boolean>`
- `submitError: WritableSignal<string | null>`
- `submittedOrder: WritableSignal<BookingResponse | null>` — set after successful submission
- `selectedBookingId: WritableSignal<number | null>` — for detail sheet

**Key methods:**
- `openCart()` / `closeCart()` — navigate to/from cart view
- `openCheckout()` / `closeCheckout()` — navigate to/from checkout
- `startNewOrder()` — clear cart, return to catalog
- `submitOrder(form: CheckoutFormValue)` — call `tenantApiService.createOrder()`, on success set `submittedOrder` and navigate to confirmation view
- `refreshOrders()` — reload `getMyBookings()`
- `cancelOrder(bookingId)` — call `cancelBooking()`, refresh list
- `confirmPayment(bookingId)` — call `confirmBookingPayment()`, refresh list

**Telegram main button integration** (same pattern as food order facade):
- Catalog view, cart empty → hide main button
- Catalog view, cart non-empty → show "View cart (N)" 
- Cart view → show "Checkout"
- Checkout view → show "Place order"
- Confirmation / orders view → hide or show "New order"

### 4c. Home Component — `ecommerce-home.component.ts`

Top-level shell for the feature. Receives `config: TenantConfig` and `slug: string` as inputs (same pattern as `FoodOrderHomeComponent`). Renders the active sub-view based on `facade.activeView()`.

Layout:
- Header with logo, tenant name, banner (same as food order header)
- Tab bar or nav to switch between catalog and orders views
- Renders `EcommerceCatalogComponent`, `EcommerceCartComponent`, `EcommerceCheckoutComponent`, `EcommerceConfirmationComponent`, or `EcommerceOrdersComponent` based on active view

### 4d. Catalog Component — `ecommerce-catalog.component.ts`

Displays the product list. Each `ServiceItem` is a product.

- For each service: show `imageUrl`, `name`, `description`
- Under each service: show its `variants` as selectable chips or cards
- Each variant shows: size label (e.g. "XL"), color label, price, stock badge
- If `variant.stock === 0` → show "Out of stock" badge, disable add button
- If variant is already in cart → show quantity pill with +/– controls (same pattern as food order menu)
- If service has only one variant (common for simple products) → show add-to-cart button directly on the product card

### 4e. Cart Component — `ecommerce-cart.component.ts`

Shows selected items grouped by variant. Each row:
- Variant image (fall back to service image if variant has no `imageUrl`)
- Product name + variant labels (e.g. "Basic Tee · Black · XL")
- Unit price
- Quantity pill with +/– controls
- Line total

Footer: subtotal, total, "Proceed to checkout" button (or Telegram main button).

### 4f. Checkout Component — `ecommerce-checkout.component.ts`

Reactive form collecting:

| Field | Validation | Hint source |
|---|---|---|
| Customer name | required | `config.checkoutNameHint` |
| Customer phone | required, phone pattern | `config.checkoutPhoneHint` |
| Delivery address | required | `config.checkoutDeliveryHint` |
| Delivery date | optional (date picker or day chips) | none |
| Note | optional | `config.checkoutNoteHint` |

Order review card at bottom showing selected items + total (same as food order checkout).

On submit: call `facade.submitOrder(formValue)`.

### 4g. Confirmation Component — `ecommerce-confirmation.component.ts`

Shown after successful order submission. Reuse or mirror `food-order-confirmation.component.ts` / `food-order-success-card.component.ts`.

Shows:
- Order ID and status
- Item list with variant labels (size + color)
- Total
- Payment QR if `config.paymentQrUrl` is set
- "View my orders" and "New order" CTAs

### 4h. Orders Component — `ecommerce-orders.component.ts`

User's order history. Same structure as `food-order-bookings.component.ts`:
- Active orders with stage bar (Placed → Paid → Confirmed → Delivering → Delivered)
- Collapsed history list
- Tapping an order opens `EcommerceDetailSheetComponent`

### 4i. Detail Sheet Component — `ecommerce-detail-sheet.component.ts`

Full order detail in a bottom sheet modal. Show:
- Status badge
- Items: `serviceName` + `variantSize` + `variantColor` + qty + unit price
- Delivery address, date, note
- Total
- Actions: "Confirm payment" (if `PAYMENT_PENDING` and `paymentQrUrl` exists), "Cancel" (if `NEW` or `PAYMENT_PENDING`)

---

## API Contract Reference

### GET `/api/t/{slug}/services`

Returns `ServiceItem[]`. For `ECOMMERCE` tenants each item includes `variants: ProductVariant[]`. For other tenant types `variants` is `[]`.

```json
[
  {
    "id": 1,
    "name": "Basic Tee",
    "description": "100% cotton",
    "imageUrl": null,
    "price": null,
    "unit": "шт",
    "durationMinutes": null,
    "sortOrder": 0,
    "status": "ACTIVE",
    "variants": [
      { "id": 10, "size": "M",  "color": "Black", "price": 25.00, "stock": 3, "sortOrder": 0, "imageUrl": "https://..." },
      { "id": 11, "size": "XL", "color": "Black", "price": 25.00, "stock": 0, "sortOrder": 1, "imageUrl": "https://..." },
      { "id": 12, "size": "M",  "color": "Blue",  "price": 27.00, "stock": 5, "sortOrder": 2, "imageUrl": null }
    ]
  }
]
```

### POST `/api/t/{slug}/orders`

```json
// Request
{
  "customerName": "John",
  "customerPhone": "+1234567890",
  "deliveryAddress": "123 Main St",
  "deliveryDate": null,
  "note": null,
  "items": [
    { "variantId": 10, "quantity": 2 },
    { "variantId": 12, "quantity": 1 }
  ]
}

// Response — same BookingResponse shape as food orders
{
  "id": 42,
  "type": "ORDER",
  "status": "NEW",
  "customerName": "John",
  "totalPrice": 77.00,
  "currency": "USD",
  "items": [
    { "serviceName": "Basic Tee", "quantity": 2, "unitPrice": 25.00, "variantSize": "M",  "variantColor": "Black" },
    { "serviceName": "Basic Tee", "quantity": 1, "unitPrice": 27.00, "variantSize": "M",  "variantColor": "Blue"  }
  ],
  ...
}
```

**Error cases to handle:**
- `HTTP 409 CONFLICT` — optimistic lock conflict (another user grabbed the last unit simultaneously). Show "Some items went out of stock while you were checking out. Please review your cart."
- `HTTP 400 BAD_REQUEST` — validation failure (e.g. stock went to zero between catalog load and submit). Show error message from response body.

### Shared booking read/cancel endpoints (same as food order):

```
GET  /api/t/{slug}/bookings/my
GET  /api/t/{slug}/bookings/{bookingId}
POST /api/t/{slug}/bookings/{bookingId}/cancel
POST /api/t/{slug}/bookings/{bookingId}/confirm-payment
```

---

## Key Differences vs Food Order Flow

| Concern | Food Order | Ecommerce |
|---|---|---|
| Cart key | `serviceId` | `variantId` |
| Order endpoint | `POST /bookings` | `POST /orders` |
| Delivery date | Required | Optional |
| Stock display | None | Per-variant badge |
| Product images | Per service | Per variant (fall back to service) |
| Item label in cart/orders | Service name only | Name + size + color |
| Out-of-stock | N/A | Disable variant, show badge |
| Repeat order | Match by service name | Not required in first version |

---

## What NOT to Change

- `features/food-order/` — leave entirely untouched
- `BookingController` and food order API paths — unchanged on backend
- `food-order-flow.facade.ts` — do not refactor into a shared base; duplicate what you need

---

## Conventions

- Angular standalone components with `imports: [...]` — no NgModules
- State via Angular signals (`signal()`, `computed()`) — no RxJS Subject-based state; use `toSignal()` at the boundary for HTTP responses
- Inject services with `inject()` in component bodies — no constructor injection
- Inline templates are acceptable for smaller components; larger ones can use `templateUrl`
- No new third-party dependencies — match whatever the food-order feature uses
- CSS: add a new `ecommerce.css` or scope styles within component `styles`; do not modify existing stylesheets
