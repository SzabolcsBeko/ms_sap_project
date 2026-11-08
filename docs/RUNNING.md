# Running the Services — Local Setup Guide

> Companion to [ARCHITECTURE.md](ARCHITECTURE.md). Step-by-step guide to run the full stack locally: infrastructure (MongoDB, MySQL ×2, Keycloak), the four Spring Boot services, and the Angular frontend.
> **Date:** 2026‑11‑08

---

## 1. Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java (JDK) | **21** | `java -version` |
| Maven | 3.9+ (or use `./mvnw`) | `mvn -v` |
| Docker + Docker Compose | recent | `docker --version` |
| Node.js + npm | Node **20** | `node -v` |
| Angular CLI | 18 | `npx ng version` |

> ⚠️ **`mvnw` exists only in `api-gateway` and `product-service`.** For `order-service` and `inventory-service` either install Maven globally and use `mvn`, or add the wrapper (`mvn -N wrapper:wrapper`).

---

## 2. Port & component map

| Component | Port | Credentials / notes |
|---|---|---|
| **API Gateway** | `9000` | entry point for all API calls |
| product-service | `8080` | default port (not overridden) |
| order-service | `8081` | |
| inventory-service | `8082` | |
| Keycloak | `8181` | admin `admin` / `admin` |
| MongoDB (product) | `27017` | `root` / `password`, db `product-service` |
| MySQL (order) | `3306` | `root` / `mysql`, db `order_service` |
| MySQL (inventory) | `3316` → 3306 | `root` / `mysql`, db `inventory_service` |
| Keycloak MySQL | internal | `keycloak` / `password` |
| Angular frontend | `4200` | OIDC client `angular-client` |

**Start order:** infrastructure → backend services → gateway → frontend.

---

## 3. Step 1 — Start infrastructure (Docker Compose)

Each service ships its own `docker-compose.yml` for its datastore. Start them from each folder:

```bash
# MongoDB for product-service
cd product-service && docker compose up -d && cd ..

# MySQL for order-service (port 3306) — creates DB order_service
cd order-service && docker compose up -d && cd ..

# MySQL for inventory-service (port 3316) — creates DB inventory_service
cd inventory-service && docker compose up -d && cd ..

# Keycloak + its MySQL (port 8181)
cd api-gateway && docker compose up -d && cd ..
```

Verify containers are healthy:

```bash
docker ps
```

You should see `mongodb`, `mysql`, `inventory-service-mysql`, `keycloak`, and `keycloak-mysql`.

> **Databases vs tables:** the MySQL `init.sql` scripts create the *databases*. The *tables* are created automatically by **Flyway** when each service boots (`spring.jpa.hibernate.ddl-auto=none`):
> - `order-service` → `V1__init.sql` creates `t_orders`
> - `inventory-service` → `V1__init.sql` creates `t_inventory`, `V2__add_inventory.sql` seeds stock: `iphone_15`, `pixel_8`, `galaxy_24`, `oneplus_12` (100 each).

---

## 4. Step 2 — Configure Keycloak (important ⚠️)

The gateway is an OAuth2 Resource Server expecting realm **`spring-microservices-security-realm`** at `http://localhost:8181`, and the frontend uses client **`angular-client`**.

### Automatic setup (realm import) ✅
`api-gateway/docker-compose.yml` starts Keycloak with `--import-realm` and mounts `./docker/keycloak/realms/`. That folder holds [`realm-export.json`](../api-gateway/docker/keycloak/realms/realm-export.json), so on first start Keycloak creates:

| Item | Value |
|---|---|
| Realm | `spring-microservices-security-realm` (`sslRequired: none`, local dev only) |
| Client | `angular-client`: public, Authorization Code + PKCE (S256), direct access grants on, redirect `http://localhost:4200/*`, web origin `http://localhost:4200` |
| Test user | `testuser` (`testuser@example.com`, Test User). Its password is set in `realm-export.json` |

No manual steps are needed. To confirm, check the logs for `Realm 'spring-microservices-security-realm' imported`:

```bash
docker logs keycloak 2>&1 | grep -i "imported"
```

> **Re-import behaviour:** the import strategy is `IGNORE_EXISTING`. If the realm already exists, a restart does **not** re-apply changes from the file. To pick up edits, recreate the container: `docker compose down && docker compose up -d` (in `api-gateway/`).
>
> **Dev only:** `sslRequired: none` and the test user are for local development. Never use this realm file in staging or production.

### Manual setup (fallback, Keycloak admin console)
1. Open `http://localhost:8181` → log in with `admin` / `admin`.
2. **Create realm** → name: `spring-microservices-security-realm`.
3. **Create client** for the Angular SPA:
   - Client ID: `angular-client`
   - Client type: **OpenID Connect**, **Public** client (PKCE, no secret)
   - Valid redirect URIs: `http://localhost:4200/*`
   - Web origins: `http://localhost:4200`
   - Standard flow: **enabled**; Direct access grants: optional
4. (Optional) a confidential client for Swagger if you use the aggregated docs UI.
5. **Create a user** (Users → Add user → set a password under Credentials, turn *Temporary* off) so you can log in from the SPA.

The gateway validates tokens via `issuer-uri=http://localhost:8181/realms/spring-microservices-security-realm` — the realm name must match exactly.

---

## 5. Step 3 — Run the backend services

Run each in its own terminal, **from the service folder**. Services with `mvnw`:

```bash
# product-service (:8080) — needs MongoDB up
cd product-service && ./mvnw spring-boot:run
```

```bash
# api-gateway (:9000) — needs Keycloak up
cd api-gateway && ./mvnw spring-boot:run
```

Services **without** `mvnw` (use global Maven):

```bash
# inventory-service (:8082) — needs MySQL on 3316
cd inventory-service && mvn spring-boot:run
```

```bash
# order-service (:8081) — needs MySQL on 3306 AND inventory-service running
cd order-service && mvn spring-boot:run
```

> **Dependency note:** `order-service` calls `inventory-service` (`inventory.url=http://localhost:8082`) at request time, so start inventory **before** placing orders. The gateway should start after the downstream services so its routes resolve.

**Recommended startup sequence:** `inventory-service` → `product-service` → `order-service` → `api-gateway`.

---

## 6. Step 4 — Run the frontend

```bash
cd frontend
npm install
npm start           # == ng serve, http://localhost:4200
```

Open `http://localhost:4200`, click **Login** (redirects to Keycloak), sign in with the user you created, then browse products / place orders.

---

## 7. Smoke test (without the UI)

All calls go through the gateway on `:9000` and require a JWT. First get a token from Keycloak (direct access grants must be enabled on a client for this shortcut):

```bash
# 1. Get an access token (replace client/user as configured)
TOKEN=$(curl -s -X POST \
  http://localhost:8181/realms/spring-microservices-security-realm/protocol/openid-connect/token \
  -d grant_type=password -d client_id=angular-client \
  -d username=testuser -d password=<password from realm-export.json> | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
```

```bash
# 2. Create a product
curl -X POST http://localhost:9000/api/product \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"iPhone 15","description":"Apple phone","skuCode":"iphone_15","price":999.00}'
```

```bash
# 3. List products
curl http://localhost:9000/api/product -H "Authorization: Bearer $TOKEN"
```

```bash
# 4. Place an order (skuCode must have stock in inventory)
curl -X POST http://localhost:9000/api/order \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"skuCode":"iphone_15","price":999.00,"quantity":1}'
# -> "Order Placed Successfully" if in stock, else an error
```

Inventory check directly (also behind the gateway):

```bash
curl "http://localhost:9000/api/inventory?skuCode=iphone_15&quantity=1" \
  -H "Authorization: Bearer $TOKEN"   # -> true / false
```

---

## 8. API documentation (Swagger)

Aggregated Swagger UI is served by the gateway (these paths are public):

```
http://localhost:9000/swagger-ui.html
```

Per-service docs: `http://localhost:9000/aggregate/{product|order|inventory}-service/v3/api-docs`.

---

## 9. Health & monitoring

Actuator is exposed on the gateway and order-service:

```bash
curl http://localhost:9000/actuator/health          # includes circuit breakers
curl http://localhost:8081/actuator/health
```

---

## 10. Shutting down

```bash
# Stop each Spring Boot app with Ctrl+C in its terminal, then stop infra:
cd product-service && docker compose down && cd ..
cd order-service && docker compose down && cd ..
cd inventory-service && docker compose down && cd ..
cd api-gateway && docker compose down && cd ..
```

> To wipe data, also remove the mounted volume folders (`*/mysql`, `product-service/data`, `api-gateway/volume-data`). Keep them to preserve state between runs.

---

## 11. Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `401 Unauthorized` on API calls | No/expired JWT, or realm/client not set up in Keycloak (see §4). Confirm `issuer-uri` realm name matches. |
| Login redirect fails / CORS error | `angular-client` redirect URIs / web origins not set to `http://localhost:4200`. |
| order fails: *"not in stock"* | SKU has no inventory row, or quantity exceeds stock. Seed via `V2__add_inventory.sql` SKUs or add rows. |
| Service won't start: table missing | Flyway didn't run — ensure the MySQL container is up first and the DB exists (`order_service` / `inventory_service`). |
| `order-service` 500 on order | `inventory-service` is down → circuit breaker fallback returns `false`. Start inventory first. |
| Port already in use | Another process on 8080/8081/8082/9000/8181/4200 — stop it or change the port. |
| `./mvnw: not found` (order/inventory) | Those two lack the wrapper — use `mvn`, or add it with `mvn -N wrapper:wrapper`. |

---

## 12. Quick reference — one-glance startup

```text
1. docker compose up -d   in: product-service, order-service, inventory-service, api-gateway
2. Keycloak realm + client + testuser are imported automatically from api-gateway/docker/keycloak/realms/  (http://localhost:8181)
3. Run services (mvn/mvnw spring-boot:run):  inventory → product → order → gateway
4. Frontend:  cd frontend && npm install && npm start   (http://localhost:4200)
```
