# Microservices Shop — Architecture & Design Document

> **System:** `ms_sap_project` — an e‑commerce "shop" reference platform built on a Spring Boot microservices backend, an Angular SPA frontend, and Keycloak-based OAuth2/OIDC security.
> **Author:** Software Architecture review • **Date:** 2026‑11‑08
> **Status:** Core request/response flows complete (pre‑Kafka). Event-driven notification layer is planned, not yet implemented.

---

## 1. Executive Summary

This project implements a classic **synchronous microservices** architecture for an online shop. A customer authenticates through **Keycloak**, browses products, and places orders. Every client request enters through a single **API Gateway** that handles routing, JWT validation (OAuth2 Resource Server), CORS, and resilience (circuit breakers). Three domain services sit behind the gateway:

| Service | Responsibility | Datastore |
|---|---|---|
| **product-service** | Product catalog (create, list) | MongoDB |
| **order-service** | Order placement & persistence | MySQL |
| **inventory-service** | Stock availability checks | MySQL |

The only **inter-service** call is `order-service → inventory-service` (synchronous REST via a declarative HTTP interface), protected by **Resilience4j** (circuit breaker + retry + time limiter) with a graceful fallback.

### Key architectural characteristics
- **Style:** Microservices, database-per-service, API-Gateway edge pattern.
- **Communication:** Synchronous HTTP/REST (JSON). No message broker yet (Kafka planned).
- **Security:** Centralized at the gateway — stateless JWT bearer tokens issued by Keycloak; the Angular app uses the OIDC Authorization Code + PKCE flow with silent refresh.
- **Resilience:** Circuit breakers at both the gateway (per-route) and in order-service (per inventory call), plus retry and timeout policies.
- **Observability:** Spring Boot Actuator + aggregated Swagger/OpenAPI docs exposed through the gateway.

---

## 2. Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Frontend | Angular (standalone components), RxJS, Tailwind, `angular-auth-oidc-client` | Angular 18 |
| Backend runtime | Java | 21 |
| Framework | Spring Boot | 3.5.3 |
| Gateway | Spring Cloud Gateway (Server MVC) | Spring Cloud |
| Security | Spring Security OAuth2 Resource Server, Keycloak | Keycloak 24.0.1 |
| Resilience | Resilience4j (circuit breaker, retry, time limiter) | — |
| Persistence | Spring Data MongoDB (product), Spring Data JPA / Hibernate (order, inventory) | — |
| Databases | MongoDB 7.0.5, MySQL 5.7.44 (×2), MySQL 8 (Keycloak) | — |
| DB migrations | Flyway (declared in order & inventory) | — |
| API docs | springdoc-openapi (Swagger UI) | 2.8.9 |
| Testing | JUnit 5, Testcontainers, REST Assured, WireMock | — |
| Build | Maven (per service), Angular CLI | — |

---

## 3. System Context & Component Architecture

### 3.1 Component / Deployment diagram

![Service-to-service communication architecture](service_communication_architecture.png)

*Service-to-service communication: every link is synchronous REST/HTTP. The only direct service-to-service call is `order-service → inventory-service` (`isInStock`).*

```mermaid
flowchart TB
    subgraph Client["Browser"]
        FE["Angular 18 SPA<br/>(standalone components)<br/>:4200"]
    end

    subgraph Identity["Identity Provider"]
        KC["Keycloak 24<br/>Realm: spring-microservices-security-realm<br/>:8181"]
        KCDB[("MySQL 8<br/>keycloak")]
        KC --- KCDB
    end

    subgraph Edge["Edge"]
        GW["API Gateway<br/>Spring Cloud Gateway MVC<br/>OAuth2 Resource Server + CORS<br/>Circuit Breakers<br/>:9000"]
    end

    subgraph Services["Domain Microservices"]
        PS["product-service<br/>:8080"]
        OS["order-service<br/>:8081"]
        IS["inventory-service<br/>:8082"]
    end

    subgraph Data["Databases (DB per service)"]
        MONGO[("MongoDB 7<br/>product-service<br/>:27017")]
        OSQL[("MySQL 5.7<br/>order_service<br/>:3306")]
        ISQL[("MySQL 5.7<br/>inventory_service<br/>:3316")]
    end

    FE -- "1. Login (OIDC Auth Code + PKCE)" --> KC
    KC -- "2. JWT access token" --> FE
    FE -- "3. REST + Bearer JWT" --> GW

    GW -- "validate JWT (JWKS)" --> KC
    GW -- "/api/product" --> PS
    GW -- "/api/order" --> OS
    GW -- "/api/inventory" --> IS

    OS -- "isInStock? (REST, Resilience4j)" --> IS

    PS --- MONGO
    OS --- OSQL
    IS --- ISQL
```

### 3.2 Routing & port map

| Route (gateway `:9000`) | Target service | Port | Notes |
|---|---|---|---|
| `POST/GET /api/product` | product-service | 8080 | Catalog |
| `POST /api/order` | order-service | 8081 | Place order |
| `GET /api/inventory` | inventory-service | 8082 | Stock check (also called internally by order-service) |
| `/aggregate/{svc}/v3/api-docs` | each service | — | Aggregated Swagger docs |
| `/fallbackRoute` | gateway | 9000 | `503` when a circuit opens |

All public routes except Swagger/aggregate resources require a valid JWT (`anyRequest().authenticated()`).

---

## 4. Request Flow — Flowchart

End-to-end flow of the two primary use cases (**browse/order** and **add product**), including the resilience branch.

```mermaid
flowchart TD
    Start([User opens SPA]) --> Auth{Authenticated?}
    Auth -- No --> Login[Redirect to Keycloak login]
    Login --> Token[Receive JWT + user profile]
    Token --> Home
    Auth -- Yes --> Home[Home page: GET /api/product]

    Home --> List[Render product list]
    List --> Choice{User action}

    Choice -- "Add product" --> AddForm[Fill product form]
    AddForm --> PostP["POST /api/product (Bearer JWT)"]
    PostP --> GWv1[Gateway validates JWT]
    GWv1 --> PS[product-service saves to MongoDB]
    PS --> Created[201 Created → refresh list]

    Choice -- "Order product" --> Qty{Quantity entered?}
    Qty -- No --> OrderFail[Show 'quantity required']
    Qty -- Yes --> PostO["POST /api/order (Bearer JWT + userDetails)"]
    PostO --> GWv2[Gateway validates JWT]
    GWv2 --> OS[order-service.placeOrder]
    OS --> Stock["InventoryClient.isInStock(skuCode, qty)"]

    Stock --> CB{Circuit breaker state}
    CB -- "Open / timeout / error" --> FB[fallbackMethod → false]
    CB -- "Closed → call inventory-service" --> ISQ[existsBySkuCode AND qty >= requested]

    ISQ --> InStock{In stock?}
    FB --> InStock
    InStock -- Yes --> Save[Persist Order to MySQL]
    Save --> Ok[201 'Order Placed Successfully']
    InStock -- No --> Ex[RuntimeException → order fails]
    Ex --> OrderFail
```

---

## 5. Data Architecture — ER Diagrams

Each service owns its own schema (**database-per-service**); there are **no cross-database foreign keys** — the link between an order and inventory is the business key **`skuCode`**, resolved at runtime via REST.

### 5.1 order-service — MySQL `order_service`

```mermaid
erDiagram
    T_ORDERS {
        bigint id PK "GeneratedValue IDENTITY"
        varchar orderNumber "UUID string"
        varchar skuCode "business key → product/inventory"
        decimal price
        int quantity
    }
```

### 5.2 inventory-service — MySQL `inventory_service`

```mermaid
erDiagram
    T_INVENTORY {
        bigint id PK "GeneratedValue IDENTITY"
        varchar skuCode "business key"
        int quantity "available stock"
    }
```

### 5.3 product-service — MongoDB `product-service` (collection `product`)

```mermaid
erDiagram
    PRODUCT {
        string id PK "Mongo ObjectId"
        string name
        string description
        string skuCode
        decimal price
    }
```

### 5.4 Logical relationships (cross-service, by `skuCode`)

```mermaid
erDiagram
    PRODUCT ||--o| INVENTORY : "same skuCode (logical)"
    PRODUCT ||--o{ T_ORDERS : "ordered by skuCode (logical)"
    INVENTORY ||--o{ T_ORDERS : "stock checked by skuCode (logical)"
```

> **Design note:** `skuCode` is the integration contract across services but is **not enforced** by any constraint. Product creation does not seed an inventory row, so a product can exist with no stock record (and `isInStock` returns `false`). Keycloak maintains its own user store (MySQL 8) and is not modeled here.

---

## 6. UML — Class Diagram

Backend domain + application layers across the three services and the gateway.

```mermaid
classDiagram
    direction LR

    %% ---------- product-service ----------
    class ProductController {
        +createProduct(ProductRequest) ProductResponse
        +getAllProducts() List~ProductResponse~
    }
    class ProductService {
        -ProductRepository productRepository
        +createProduct(ProductRequest) ProductResponse
        +getAllProducts() List~ProductResponse~
    }
    class ProductRepository {
        <<interface MongoRepository>>
    }
    class Product {
        <<Document "product">>
        +String id
        +String name
        +String description
        +String skuCode
        +BigDecimal price
    }
    class ProductRequest {
        <<record>>
        +String id
        +String name
        +String description
        +String skuCode
        +BigDecimal price
    }
    class ProductResponse {
        <<record>>
        +String id
        +String name
        +String description
        +String skuCode
        +BigDecimal price
    }
    ProductController --> ProductService
    ProductService --> ProductRepository
    ProductRepository ..> Product
    ProductService ..> ProductRequest
    ProductService ..> ProductResponse

    %% ---------- order-service ----------
    class OrderController {
        +placeOrder(OrderRequest) String
    }
    class OrderService {
        -OrderRepository orderRepository
        -InventoryClient inventoryClient
        +placeOrder(OrderRequest) void
    }
    class OrderRepository {
        <<interface JpaRepository>>
    }
    class Order {
        <<Entity t_orders>>
        +Long id
        +String orderNumber
        +String skuCode
        +BigDecimal price
        +Integer quantity
    }
    class OrderRequest {
        <<record>>
        +Long id
        +String orderNumber
        +String skuCode
        +BigDecimal price
        +Integer quantity
    }
    class InventoryClient {
        <<interface @HttpExchange>>
        +isInStock(String skuCode, Integer quantity) boolean
        +fallbackMethod(...) boolean
    }
    OrderController --> OrderService
    OrderService --> OrderRepository
    OrderService --> InventoryClient
    OrderRepository ..> Order
    OrderService ..> OrderRequest

    %% ---------- inventory-service ----------
    class InventoryController {
        +isInStock(String skuCode, Integer quantity) boolean
    }
    class InventoryService {
        -InventoryRepository inventoryRepository
        +isInsStock(String skuCode, Integer quantity) boolean
    }
    class InventoryRepository {
        <<interface JpaRepository>>
        +existsBySkuCodeAndQuantityIsGreaterThanEqual(...) boolean
    }
    class Inventory {
        <<Entity t_inventory>>
        +Long id
        +String skuCode
        +Integer quantity
    }
    InventoryController --> InventoryService
    InventoryService --> InventoryRepository
    InventoryRepository ..> Inventory

    %% ---------- cross-service runtime link ----------
    InventoryClient ..> InventoryController : "REST GET /api/inventory"

    %% ---------- gateway ----------
    class Routes {
        <<Configuration>>
        +productServiceRoute()
        +orderServiceRoute()
        +inventoryServiceRoute()
        +fallbackRoute()
    }
    class SecurityConfig {
        <<Configuration>>
        +securityFilterChain(HttpSecurity)
        +corsConfigurationSource()
    }
```

---

## 7. UML — Sequence Diagrams

### 7.1 Place Order (happy path + fallback)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Angular SPA
    participant KC as Keycloak
    participant GW as API Gateway :9000
    participant OS as order-service :8081
    participant IC as InventoryClient (Resilience4j)
    participant IS as inventory-service :8082
    participant DB as MySQL order_service

    U->>FE: Click "Order" (qty)
    FE->>KC: getAccessToken() / userData$
    KC-->>FE: JWT + profile (email, name)
    FE->>GW: POST /api/order (Bearer JWT, OrderRequest)
    GW->>GW: Validate JWT (JWKS), CORS, circuit breaker
    GW->>OS: forward POST /api/order
    OS->>IC: isInStock(skuCode, quantity)

    alt Circuit CLOSED and service healthy
        IC->>IS: GET /api/inventory?skuCode&quantity
        IS->>IS: existsBySkuCode AND quantity >= requested
        IS-->>IC: true / false
    else Circuit OPEN / timeout / error (after retries)
        IC-->>IC: fallbackMethod() → false
    end

    alt In stock == true
        OS->>DB: save(Order) [UUID orderNumber]
        DB-->>OS: persisted
        OS-->>GW: 201 "Order Placed Successfully"
        GW-->>FE: 201
        FE-->>U: orderSuccess = true
    else Not in stock
        OS-->>GW: RuntimeException (500)
        GW-->>FE: error
        FE-->>U: orderFailed = true
    end
```

### 7.2 Create Product

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Angular SPA
    participant GW as API Gateway :9000
    participant PS as product-service :8080
    participant M as MongoDB

    U->>FE: Submit add-product form
    FE->>GW: POST /api/product (Bearer JWT, ProductRequest)
    GW->>GW: Validate JWT + circuit breaker
    GW->>PS: forward POST /api/product
    PS->>PS: build Product (Lombok builder)
    PS->>M: save(product)
    M-->>PS: id assigned
    PS-->>GW: 201 ProductResponse
    GW-->>FE: 201
    FE-->>U: productCreated = true, reset form
```

### 7.3 Authentication (OIDC Authorization Code + PKCE)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Angular SPA (angular-auth-oidc-client)
    participant KC as Keycloak :8181
    participant GW as API Gateway

    U->>FE: Click Login
    FE->>KC: /authorize (response_type=code, PKCE, scope=openid profile offline_access)
    KC-->>U: Login form
    U->>KC: Credentials
    KC-->>FE: redirect with authorization code
    FE->>KC: /token (code + PKCE verifier)
    KC-->>FE: access_token (JWT) + refresh_token
    Note over FE: silentRenew via refresh token, 30s before expiry
    FE->>GW: API calls with Authorization: Bearer <JWT>
    GW->>KC: fetch JWKS (issuer-uri) to validate signature
    GW-->>FE: authorized response
```

---

## 8. UML — Activity Diagram (Place Order)

```mermaid
flowchart TD
    A([Start: user submits order]) --> B[Attach JWT via auth interceptor]
    B --> C{Quantity provided?}
    C -- No --> Z1[Set orderFailed + quantityIsNull]:::fail
    C -- Yes --> D[POST /api/order to gateway]
    D --> E{JWT valid?}
    E -- No --> Z2[401 Unauthorized]:::fail
    E -- Yes --> F[Gateway → order-service]
    F --> G[Call inventory via InventoryClient]

    G --> H{Inventory reachable within timeout?}
    H -- "No (retry x3 then open circuit)" --> I[fallbackMethod returns false]
    H -- Yes --> J[Query existsBySkuCode and qty >= requested]

    I --> K{In stock?}
    J --> K
    K -- No --> Z3[Throw RuntimeException → order not saved]:::fail
    K -- Yes --> L[Generate UUID orderNumber]
    L --> M[Persist Order in MySQL]
    M --> N[Return 'Order Placed Successfully']
    N --> O([End: orderSuccess])
    Z1 --> P([End: failure])
    Z2 --> P
    Z3 --> P

    classDef fail fill:#fdd,stroke:#c00;
```

---

## 9. Resilience Design (Resilience4j)

The `order-service → inventory-service` call and every gateway route are wrapped in a circuit breaker. Order-service also adds **retry** and **time limiter**.

```mermaid
stateDiagram-v2
    [*] --> Closed
    Closed --> Open : failureRate ≥ 50% over last 5 calls
    Open --> HalfOpen : after waitDurationInOpenState (5s)
    HalfOpen --> Closed : permitted calls (3) succeed
    HalfOpen --> Open : a permitted call fails
    note right of Open
        Fast-fail → fallbackMethod() returns false
        (order treated as "not in stock")
    end note
```

**order-service `inventory` instance:** sliding window = 5 (COUNT_BASED), failure threshold = 50%, wait in open = 5s, half-open permitted = 3, min calls = 5; **retry** max 3 attempts / 2s wait; **time limiter** 3s. Gateway uses the equivalent `default` config per route, forwarding to `/fallbackRoute` (HTTP 503) when open.

---

## 10. Cross-Cutting Concerns

| Concern | Implementation |
|---|---|
| **AuthN/AuthZ** | Keycloak realm `spring-microservices-security-realm`; gateway as OAuth2 Resource Server validating JWT via `issuer-uri`; SPA uses client `angular-client`. All API routes authenticated; Swagger paths public. |
| **CORS** | Configured at gateway (`GET, POST, PUT, DELETE, OPTIONS, HEAD`, default permit values). |
| **API docs** | springdoc per service, aggregated at gateway Swagger UI (`/swagger-ui.html`) via `/aggregate/*` routes. |
| **Health/metrics** | Spring Boot Actuator (`/actuator/*`), circuit-breaker health indicators enabled on gateway & order-service. |
| **DB migration** | Flyway on the JDBC-backed services (order, inventory); `ddl-auto=none` so schema is migration-managed (not auto-generated). |
| **Config** | Per-service `application.properties`; service URLs & DB credentials hardcoded to localhost (dev profile). |

---

## 11. Observations & Architectural Recommendations

**Strengths**
- Clean separation of concerns, database-per-service, and a single secured entry point.
- Modern stack (Java 21, Spring Boot 3.5, Angular 18 standalone, declarative HTTP interface client).
- Resilience is applied at two layers with a sensible fallback.

**Gaps / risks found during analysis**
1. **`userDetails` is dropped.** The frontend `Order` sends `email/firstName/lastName`, but `order-service`'s `OrderRequest`/`Order` have no such fields — customer identity is **not persisted**. This is the hook for the planned Kafka → notification-service (see commit "All the functionality before Kafka").
2. **No event/notification layer yet.** Order placement is fire-and-forget synchronous; no order-confirmation email/event. Kafka is the stated next step.
3. **No stock decrement.** `placeOrder` only *reads* inventory; it never decrements quantity, so stock never depletes and overselling is possible (no reservation/transaction).
4. **Hardcoded hosts.** Service URLs and DB credentials are baked into properties (`localhost`, plaintext passwords). Needs externalized config / secrets and (for scaling) service discovery instead of fixed `http://localhost:PORT`.
5. **No distributed tracing / centralized logging** (e.g., Micrometer Tracing + Zipkin/Tempo, ELK/Loki).
6. **Generic error handling.** "Not in stock" surfaces as a 500 `RuntimeException`; a domain-specific 4xx and `@ControllerAdvice` would be clearer.
7. **No containerization of the apps themselves.** Compose files cover only infra (DBs, Keycloak); the services/frontend run locally. A full compose/Helm for the apps + orchestration would complete the deployment story.

**Suggested roadmap**
- **Phase 1 (in progress):** Introduce Kafka; publish `OrderPlacedEvent` (incl. `userDetails`) from order-service → `notification-service` for email confirmation.
- **Phase 2:** Add inventory reservation/decrement with transactional guarantees (or Saga for cross-service consistency).
- **Phase 3:** Externalize config (Spring Cloud Config / env), add service discovery or K8s DNS, secrets management.
- **Phase 4:** Observability — Micrometer Tracing, Prometheus/Grafana, centralized logs.
- **Phase 5:** Containerize all apps; CI/CD; Kubernetes/Helm deployment.

---

## 12. Repository Layout

```
ms_sap_project/
├── api-gateway/        # Spring Cloud Gateway MVC, Security (OAuth2), Keycloak compose
├── product-service/    # MongoDB catalog service (:8080)
├── order-service/      # MySQL order service (:8081), InventoryClient, Resilience4j
├── inventory-service/  # MySQL stock service (:8082)
└── frontend/           # Angular 18 SPA (OIDC, product list, order, add-product)
```
