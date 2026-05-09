## Spring Boot gRPC Commerce Demo

This repository contains a multi-service Java Spring Boot system where the end user talks to exactly one service over REST and every important internal call happens over gRPC.

### Services

- `api-gateway`
  - Public REST edge on port `8080`
  - Converts HTTP/JSON into gRPC calls
- `order-service`
  - Internal orchestrator on gRPC port `9091`
  - Fan-outs to pricing, inventory, and analytics
- `pricing-service`
  - Internal unary gRPC service on port `9092`
- `inventory-service`
  - Internal unary + server-streaming gRPC service on port `9093`
- `analytics-service`
  - Internal client-streaming + bidirectional-streaming gRPC service on port `9094`
- `proto-contracts`
  - Shared `.proto` contracts and generated Java stubs

### gRPC Concepts Demonstrated

- Unary RPC
  - `PricingService.PriceCart`
  - `InventoryService.ReserveInventory`
  - `OrderCommandService.CreateQuote`
  - `OrderCommandService.PlaceOrder`
- Server streaming
  - `InventoryService.StreamWarehouseAvailability`
  - `OrderCommandService.WatchOrderJourney`
- Client streaming
  - `AnalyticsService.IngestLifecycle`
- Bidirectional streaming
  - `AnalyticsService.LiveRecommendations`
- Metadata propagation
  - `x-correlation-id`
  - `x-caller-service`
- Deadlines
  - Gateway and order-service set per-call deadlines on downstream gRPC requests
- Error handling
  - Inventory rejects impossible reservations with gRPC status errors
- Protobuf evolution
  - Reserved field slot in pricing response to show safe schema evolution

### Request Flow

`REST client -> api-gateway -> order-service -> pricing-service / inventory-service / analytics-service`

Quote flow:

1. REST call reaches `api-gateway`
2. `api-gateway` calls `order-service` over gRPC
3. `order-service` calls:
   - `pricing-service` via unary RPC
   - `inventory-service` via server-streaming RPC
   - `analytics-service` via bidi stream for recommendations

Place-order flow:

1. REST call reaches `api-gateway`
2. `api-gateway` calls `order-service`
3. `order-service` calls:
   - `pricing-service` via unary RPC
   - `inventory-service` via unary reservation RPC
   - `analytics-service` via client-streaming lifecycle ingestion
   - `analytics-service` via bidi recommendation stream

### Build

```powershell
.\mvnw.cmd -DskipTests package
```

### Run

Start each service in a separate terminal:

```powershell
.\mvnw.cmd -pl pricing-service spring-boot:run
.\mvnw.cmd -pl inventory-service spring-boot:run
.\mvnw.cmd -pl analytics-service spring-boot:run
.\mvnw.cmd -pl order-service spring-boot:run
.\mvnw.cmd -pl api-gateway spring-boot:run
```

### Convenience scripts (PowerShell)

Use the repo scripts to start and stop all services:

```powershell
.\start-all.ps1
.\stop-all.ps1
```

`start-all.ps1` builds and starts each service in its own PowerShell window. `stop-all.ps1` stops Java processes that include this repo path and the service names in their command line.

### Example REST Calls

Quote:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/checkout/quote' `
  -Headers @{ 'X-Correlation-Id' = 'demo-corr-001' } `
  -ContentType 'application/json' `
  -Body '{
    "customer": { "customerId": "cust-100", "email": "gold@example.com", "tier": "GOLD" },
    "items": [
      { "sku": "LAPTOP-15", "title": "15 inch laptop", "quantity": 1 },
      { "sku": "MOUSE-ERGONOMIC", "title": "Ergo mouse", "quantity": 2 }
    ],
    "currency": "USD"
  }'
```

Place order:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/checkout/orders' `
  -Headers @{ 'X-Correlation-Id' = 'demo-corr-002' } `
  -ContentType 'application/json' `
  -Body '{
    "customer": { "customerId": "cust-100", "email": "gold@example.com", "tier": "GOLD" },
    "items": [
      { "sku": "LAPTOP-15", "title": "15 inch laptop", "quantity": 1 },
      { "sku": "MOUSE-ERGONOMIC", "title": "Ergo mouse", "quantity": 2 }
    ],
    "shippingAddress": {
      "line1": "42 Residency Road",
      "line2": "Suite 8",
      "city": "Bengaluru",
      "state": "KA",
      "postalCode": "560025",
      "countryCode": "IN"
    },
    "currency": "USD"
  }'
```

Watch the order journey:

```powershell
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/api/checkout/orders/<ORDER_ID>/journey'
```

### Verified

The repository was verified with:

- `.\mvnw.cmd -q -DskipTests package`
- End-to-end smoke calls through the REST gateway for:
  - quote creation
  - order placement
  - order journey retrieval
