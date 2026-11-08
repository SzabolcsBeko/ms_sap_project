# API Test Plan: Postman / Insomnia / Bruno

> Companion to [ARCHITECTURE.md](ARCHITECTURE.md) and [RUNNING.md](RUNNING.md). A hands-on collection for learning the system's flows and checking how each microservice behaves, including failures.
> **Date:** 2026‑11‑08

---

## 1. What's in the box

| File | Purpose | Use it with |
|---|---|---|
| [`api-tests/microservices-shop.postman_collection.json`](../api-tests/microservices-shop.postman_collection.json) | 31 requests in 7 folders, each with a description and test assertions | Postman, Insomnia (Bruno: via Import) |
| [`api-tests/local.postman_environment.json`](../api-tests/local.postman_environment.json) | Connection settings for the local stack (URLs, realm, client, test user) | Postman, Insomnia (Bruno: via Import) |
| [`api-tests/bruno/`](../api-tests/bruno/) | The same collection as a **native Bruno collection** (`.bru` files), environment included | Bruno: **Open Collection** |

**Format:** Postman Collection **v2.1** is the source. The `bruno/` folder is generated from it with Bruno's own converter, exactly what Bruno would write when importing.

| Tool | How to load it | Test scripts | Verified |
|---|---|---|---|
| **Postman** | Import the two JSON files | native | 44/44 assertions with a Postman-semantics runner (Postman app not installed here) |
| **Bruno** | **Open Collection** → `api-tests/bruno` (or Import the JSON) | translated to Bruno's `bru`/`res` API | **Bruno 3.4.2's own converter and parser**; the `.bru` files pass 44/44 against the live stack |
| **Insomnia** (v9+) | Import the two JSON files | mapped to Insomnia's after-response scripts | **not verified** (Insomnia not installed here) |

The scripts deliberately use a small, common subset of the Postman API that all three can handle: `pm.test`, `pm.expect`, `pm.response.code/json()/text()/headers.get()/responseTime`, and **`pm.environment.get/set`**. Two Bruno details shaped this:
- Bruno deliberately does **not** translate `pm.collectionVariables.set`. Values the scripts store (`accessToken`, `createdProductId`) therefore live in the **environment**, via `pm.environment.set`, which becomes `bru.setEnvVar` in Bruno.
- Bruno turns `pm.response.text()` into `JSON.stringify(res.getBody())`, which wraps plain-text bodies in quotes. So plain-text checks use `to.include(...)` instead of an exact match.

> **How it was verified:** every request and assertion was run against the live stack: folders 0–5 pass **44/44** in both the Postman JSON and the native Bruno files, and folder 6 was checked with the services stopped. If a script doesn't convert in your tool version, the requests still work; you just check the result by eye.

---

## 2. Before you start

1. Start the stack: `./launch.sh` (see [RUNNING.md](RUNNING.md)).
2. Load the collection (below) and **select the environment "Microservices Shop: local"**. It's required, because the test scripts save the token into it.
3. Run **folder 0 → Get access token** first. It stores the JWT in the `accessToken` environment variable, which every request sends as `Authorization: Bearer …` (set once at collection level). Tokens last **5 minutes**. When requests start returning 401, run it again.

### Variables

| Variable | Default | Notes |
|---|---|---|
| `gatewayUrl` | `http://localhost:9000` | single entry point for the API |
| `keycloakUrl` | `http://localhost:8181` | |
| `realm` / `clientId` | `spring-microservices-security-realm` / `angular-client` | |
| `username` / `password` | `testuser` / (from the realm file) | the dev test user from `realm-export.json` |
| `productServiceUrl`, `orderServiceUrl`, `inventoryServiceUrl` | `:8080`, `:8081`, `:8082` | used only for direct calls (actuator, gateway-bypass checks) |
| `inStockSku` | `iphone_15` | any seeded SKU: iphone_15, pixel_8, galaxy_24, oneplus_12 |
| `accessToken`, `createdProductId` | (empty; set by scripts) | environment variables, written by `pm.environment.set` (Bruno: `bru.setEnvVar`) |

All variables are plain (none typed "secret"), because Bruno never writes secret values into its environment files and the test password would go missing. The password is the dev-only test user's.

---

## 3. Importing

### Postman
1. **Import** → drop both JSON files.
2. Top-right environment selector → **Microservices Shop: local**.
3. Run: right-click the collection → **Run collection** → untick **folder 6** → **Run**.

### Bruno
> **Why "Open Collection" can't open the JSON files:** in Bruno, *Open Collection* opens a **folder** that already contains a Bruno collection (`bruno.json`). JSON files go through **Import**. An environment file is never opened on its own; it's imported inside a collection's settings.

**Option A (recommended): open the native collection**
1. **Open Collection** → select the folder **`api-tests/bruno`** (the folder itself, not a file inside it).
2. Top-right environment selector → **Microservices Shop: local** (the environment is already inside the folder).
3. Run: right-click the collection → **Run**, or run folders one at a time (skip folder 6).

Bruno edits these `.bru` files in place, so changes you make in Bruno show up in git.

**Option B: import the Postman JSON**
1. **Import Collection** → choose `microservices-shop.postman_collection.json` (Bruno detects the Postman format) → pick a location where Bruno creates a new collection folder.
2. Collection **Settings → Environments → Import** → pick `local.postman_environment.json` → select it.

### Insomnia (v9 or newer)
1. **Import** → **File** → pick the collection, then import the environment file the same way.
2. Select the imported environment.
3. Run requests individually or with the **Collection Runner** (skip folder 6).

### Command line (optional)
- **Postman/newman:** `npx newman run api-tests/microservices-shop.postman_collection.json -e api-tests/local.postman_environment.json --folder "0. Setup & smoke" --folder "1. Security (gateway edge)" ...` (one `--folder` per folder 0–5).
- **Bruno CLI:** `cd api-tests/bruno && bru run "0. Setup & smoke" --env "Microservices Shop: local"` (repeat per folder 0–5; requires `npm i -g @usebruno/cli`).

> **Keeping the formats in sync:** the Postman JSON is the source and `api-tests/bruno/` is generated from it. If you change the JSON, re-create the Bruno folder (Import it into Bruno at that location, replacing the old folder), or make the same change in both.

---

## 4. The test matrix

Folders **0–5 are automated** (run in order, top to bottom). Folder **6 is manual**.

### 0. Setup & smoke
| # | Request | Expected |
|---|---|---|
| 0.1 | Get access token (Keycloak password grant) | 200, `token_type` Bearer, token saved to `accessToken` |
| 0.2 | `GET /actuator/health` on the gateway | 200, `UP` (needs a token) |
| 0.3 | `GET /aggregate/product-service/v3/api-docs` | 200 OpenAPI document, **no token** (public route) |

### 1. Security (gateway edge)
| # | Request | Expected |
|---|---|---|
| 1.1 | `GET /api/product` without token | 401, `WWW-Authenticate: Bearer` |
| 1.2 | `GET /api/product` with a fake token | 401 |
| 1.3 | Keycloak login with wrong password | 401, `invalid_grant` |

### 2. Product catalog: gateway → product-service → MongoDB
| # | Request | Expected |
|---|---|---|
| 2.1 | Create product (SKU `{{inStockSku}}`) | 201, Mongo `id`, saved to `createdProductId` |
| 2.2 | List products | 200, array that contains the new product |
| 2.3 | Malformed JSON | 400 |

### 3. Inventory: gateway → inventory-service → MySQL
| # | Request | Expected |
|---|---|---|
| 3.1 | `iphone_15`, quantity 1 | `true` |
| 3.2 | quantity 100 (= seeded stock) | `true` (query is `>=`) |
| 3.3 | quantity 101 | `false` |
| 3.4 | unknown SKU `nokia_3310` | `false` |
| 3.5 | missing `quantity` parameter | 400 |

### 4. Order flow: gateway → order-service → inventory-service → MySQL
| # | Request | Expected |
|---|---|---|
| 4.1 | Place order, in stock | 201 `Order Placed Successfully` |
| 4.2 | Place order, quantity 1000 | **500** (generic error, nothing saved) |
| 4.3 | Place order, unknown SKU | **500** |
| 4.4 | Place order with `userDetails` (as the web app sends) | 201, but user details are **not stored** |
| 4.5 | `GET :8081/actuator/circuitbreakers` | `inventory` breaker `CLOSED`, threshold 50% |

### 5. Known gaps: tests that assert today's (weak) behaviour
| # | Request | Expected today | What it shows |
|---|---|---|---|
| 5.1 | Create product with `{}` | 201, all fields `null` | no input validation |
| 5.2–5.4 | Order 100 `pixel_8`, again 100, then check stock | 201, 201, still `true` for 100 | **overselling**: orders never decrement stock |
| 5.5 | `GET :8080/api/product` with no token | 200 | services are reachable **around** the gateway, without auth |
| 5.6 | `GET :8082/api/inventory` with no token | 200 | same, inventory-service |
| 5.7 | `GET /api/product/{id}` | 404 | endpoint not implemented |

When one of these gaps is fixed, its test fails, and that's the signal to update it to the new expected behaviour.

### 6. Resilience (manual)
| # | Precondition | Request | Expected |
|---|---|---|---|
| 6.1 | inventory-service stopped | Place order | **500 in milliseconds** (fallback, no hang) |
| 6.2 | after 6.1 five or more times | `GET :8081/actuator/circuitbreakers` | `inventory` breaker **OPEN** |
| 6.3 | product-service stopped | `GET /api/product` (with token) | **503** `Service Unavailable, please try again later` |
| 6.4 | product-service stopped | public API docs, no token | **401** instead of 503 (gap, see §6) |
| 6.5 | after `./launch.sh` + 3 successful orders | `GET :8081/actuator/circuitbreakers` | breaker **CLOSED** again |

---

## 5. Running the resilience scenario step by step

```bash
# 1. Stop inventory-service (kills the JVM on its port)
lsof -ti tcp:8082 -sTCP:LISTEN | xargs kill
```
Run **6.1** five times, then **6.2**: the breaker is `OPEN`. In `.run/logs/order-service.log` you'll see `Cannot get inventory for skuCode …`, logged by the fallback.

```bash
# 2. Stop product-service
lsof -ti tcp:8080 -sTCP:LISTEN | xargs kill
```
Run **6.3** (503 from the gateway's fallback route) and **6.4** (401 on the public route).

```bash
# 3. Bring them back (launch.sh only starts what is missing)
./launch.sh
```
Wait 5 seconds, run **folder 4 → Place order (in stock)** 3 times, then **6.5**: the breaker goes `OPEN → HALF_OPEN → CLOSED`.

What to learn from it:

| Setting (order-service, `inventory` breaker) | Value | Effect you can observe |
|---|---|---|
| sliding window | 5 calls, count-based | the breaker decides after 5 calls |
| failure-rate threshold | 50% | 3 failures in 5 calls open it |
| wait in OPEN | 5 s | after 5 s the next call is a trial call |
| permitted calls in HALF_OPEN | 3 | 3 successes close it |
| retry | 3 attempts, 2 s apart | doesn't fire here: the circuit breaker's fallback handles the error first, so the order fails fast |

---

## 6. Findings this test plan surfaced

| Finding | Where | Suggested fix |
|---|---|---|
| Out of stock / unknown SKU returns **500** | order-service | throw a domain exception mapped to 409/422 with `@ControllerAdvice` |
| **Overselling**: stock never decremented | order + inventory | reserve or decrement stock in the order flow (transactional or Saga) |
| **No input validation**: empty product is saved | product-service | `@Valid` + `@NotBlank`/`@Positive` on `ProductRequest` |
| Services reachable **without auth** on their own ports | all services | keep them on a private network, or make each a resource server |
| `userDetails` sent by the web app is **dropped** | order-service | add it to `OrderRequest`/`Order` (also needed for the planned Kafka notifications) |
| Gateway fallback on a **public** route returns **401** instead of 503 | api-gateway `SecurityConfig` | add `/fallbackRoute` to the permitted URLs |
| No `GET /api/product/{id}` | product-service | add it if clients need single-product lookup |

---

## 7. Notes & troubleshooting

- **Data:** runs add products and orders to your local databases. There are no delete endpoints, and folder 5.1 adds a blank product that also shows up in the web app. To start fresh: `./launch.sh stop --all`, delete the data folders listed in [RUNNING.md](RUNNING.md) §0, then `./launch.sh`.
- **401 everywhere:** the token expired (5 min). Run 0.1 again.
- **Connection refused:** the stack isn't running. Check with `./launch.sh status`.
- **4.5 fails with `OPEN`/`HALF_OPEN`:** you ran folder 6 recently. Place 3 successful orders, or wait, and run it again.
- **Every request after 0.1 returns 401:** no environment is selected, so the token had nowhere to be saved. Select **Microservices Shop: local** and run 0.1 again.
- **Test scripts didn't import (Insomnia):** the requests still work. Copy the `access_token` from the 0.1 response into the `accessToken` environment variable by hand and compare results with the matrix above.
