# Test Plan: API (Postman / Insomnia / Bruno) and UI (browser)

> Companion to [ARCHITECTURE.md](ARCHITECTURE.md) and [RUNNING.md](RUNNING.md). A hands-on collection for learning the system's flows and checking how each microservice behaves, including failures, plus a manual walkthrough of the web app ([§6](#6-ui-testing-the-web-app-in-the-browser)).
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

> **How it was verified:** every request and assertion was run against the live stack: folders 0–5 pass **44/44** in both the Postman JSON and the native Bruno files, and folder 6 was checked with the services stopped. After the move of order-service and inventory-service to PostgreSQL 16, everything was re-run: folders 0–5 pass **44/44** with newman and **26/26 requests** with Bruno CLI 2, and folder 6 passes following §5 (breaker `OPEN → HALF_OPEN → CLOSED`). If a script doesn't convert in your tool version, the requests still work; you just check the result by eye.

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
- **Bruno CLI:** `cd api-tests/bruno && bru run "0. Setup & smoke" "1. Security (gateway edge)" "2. Product catalog (product-service, MongoDB)" "3. Inventory (inventory-service, PostgreSQL)" "4. Order flow (order-service → inventory-service)" "5. Known gaps (documents current behaviour)" --env "Microservices Shop- local"` (requires `npm i -g @usebruno/cli`, or prefix with `npx -y @usebruno/cli@2`). Run all folders in **one** call so the token from folder 0 reaches the others. `--env` takes the environment's **file name** (`environments/Microservices Shop- local.bru`, where Bruno replaced the `:`), not its display name.

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

### 3. Inventory: gateway → inventory-service → PostgreSQL
| # | Request | Expected |
|---|---|---|
| 3.1 | `iphone_15`, quantity 1 | `true` |
| 3.2 | quantity 100 (= seeded stock) | `true` (query is `>=`) |
| 3.3 | quantity 101 | `false` |
| 3.4 | unknown SKU `nokia_3310` | `false` |
| 3.5 | missing `quantity` parameter | 400 |

### 4. Order flow: gateway → order-service → inventory-service → PostgreSQL
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
| 6.4 | product-service stopped | public API docs, no token | **401** instead of 503 (gap, see §7) |
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

## 6. UI testing: the web app in the browser

The API collection checks the services. This section walks through the **Angular web app** the way a user would, and shows where to look in the browser's developer tools to see the same flows (login → gateway → services).

Everything below was observed in the running app (Angular 18 dev server, Keycloak 24) on 2026‑11‑08.

### 6.1 Before you start

1. Start everything: `./launch.sh` (the frontend is the last step, at http://localhost:4200).
2. Use a **browser window at least 768 px wide**. Below that width the header collapses into a ☰ button that does nothing, so the **Login** link can't be reached (see UI‑1 in §6.5).
3. Open the developer tools: **Network** tab (filter `9000` for API calls, `8181` for Keycloak) and **Console** tab.
4. Test user: `testuser`, with the password from `api-gateway/docker/keycloak/realms/realm-export.json`.

> **Reload between scenarios.** The page keeps its last success/failure message until it's reloaded (UI‑2), and a silent logout leaves stale data on screen (UI‑4). A reload gives each scenario a clean start.

### 6.2 What happens behind the scenes

| You do | Browser → | Network tab shows |
|---|---|---|
| Open the app logged out | app → gateway | `GET :9000/api/product → 401` (no token yet) |
| Click **Login** | app → Keycloak | redirect to `:8181/…/auth?response_type=code&code_challenge_method=S256` (Authorization Code + PKCE) |
| Sign in | Keycloak → app | redirect back to `:4200`, then `POST :8181/…/token` (code exchanged for tokens) |
| Product list loads | app → gateway → product-service | `OPTIONS :9000/api/product → 200` (CORS preflight), `GET :9000/api/product → 200` with `Authorization: Bearer …` |
| **Order Now** | app → gateway → order-service → inventory-service | `POST :9000/api/order → 201` (or 500 when out of stock) |
| **Add Product** | app → gateway → product-service | `POST :9000/api/product → 201` |
| About 4.5 min after login | app → Keycloak | `POST :8181/…/token → 200` (refresh). The app then **rejects** the result and logs you out (UI‑3) |

### 6.3 Test cases

Run them in order. "Expected" is what the app does today; ⚠ marks behaviour that is a known bug (§6.5).

| # | Scenario | Steps | Expected |
|---|---|---|---|
| UI‑01 | Anonymous home page | Open http://localhost:4200 | Header: **Login**. Page: **"Products (0)"** and **"No products found!"**, even though products exist ⚠ (the list call gets 401). No **Create Product** button. |
| UI‑02 | Login | Click **Login** → sign in as `testuser` | Keycloak page *"Sign in to your account"*, then back to the app: header **"Hi testuser"** + **Logout**, **"Products (N)"**, **Create Product** button. ⚠ In light mode "Hi testuser" is invisible (white on white). |
| UI‑03 | Order without quantity | Leave **Quantity** empty → **Order Now** | Red **"Order Failed, please try again later"** + **"Quantity cannot be null"**. No request is sent. |
| UI‑04 | Successful order | Quantity `1` on an **iPhone 15** row → **Order Now** | Green **"Order Placed Successfully"**; Network: `POST /api/order → 201`. The quantity field isn't cleared. |
| UI‑05 | Out of stock (fresh page) | **Reload**, quantity `1000` → **Order Now** | Red **"Order Failed, please try again later"**; Network: `POST /api/order → 500`. |
| UI‑06 | Out of stock after a success ⚠ | Do UI‑04, then (no reload) quantity `1000` → **Order Now** | Network shows `500`, but the page **still says "Order Placed Successfully"** (UI‑2). |
| UI‑07 | Add product: required fields | **Create Product**, click into **SKU Code**, then click another field without typing | **"SKU Code is required."**; **Add Product** is disabled. |
| UI‑08 | Add product: weak validation ⚠ | Enter SKU `ab`, Name `x`, Description `short`, keep Price `0` | **Add Product is enabled**, no messages. The template's "at least 3/10 characters" and "greater than 0" messages never appear (UI‑5). Don't submit, or you'll add a junk product. |
| UI‑09 | Add product: success | SKU `oneplus_12`, Name `OnePlus 12`, any description, Price `799` → **Add Product** | Green **"Product Created Successfully"**, form cleared; Network: `POST /api/product → 201`. You stay on `/add-product`; go back by clicking the **"Spring Boot Microservices Shop"** title (full reload). |
| UI‑10 | Order the new product | On the list, **OnePlus 12** (last row), quantity `2` → **Order Now** | `POST /api/order → 201`. The message appears **at the top of the list**, so scroll up to see it. Use a seeded SKU (iphone_15, pixel_8, galaxy_24, oneplus_12); any other SKU has no stock, so ordering it fails. |
| UI‑11 | Session expiry ⚠ | Stay logged in about 5 minutes (token lifetime), then look at the header | Header switches back to **Login** on its own; Console: *"silent renew failed!"* (UI‑3). The old product list stays on screen. |
| UI‑12 | Order after silent logout ⚠ | After UI‑11, quantity `1` → **Order Now** | **Nothing happens**: no request, no message (stale message stays). Console: `TypeError: Cannot read properties of null (reading 'email')`. Reload and log in again. |
| UI‑13 | Logout | Log in, then **Logout** | Back to **"Products (0)"**, header **Login**. Clicking **Login** again shows the Keycloak sign-in form, so the Keycloak session really ended. (Without logging out, **Login** goes straight back in without a password: single sign-on.) |
| UI‑14 | Add product while logged out ⚠ | Logged out, open http://localhost:4200/add-product directly, fill all fields → **Add Product** | The page opens (no route guard). Submit sends `POST /api/product → 401`, and the page shows **no message**; the form stays filled. The gateway still protects the data. |
| UI‑15 | Narrow window ⚠ | Make the window narrower than 768 px | **Login/Logout** disappear and a ☰ button appears that does nothing (UI‑1). |

### 6.4 Clean-up

UI‑09 creates a product, and UI‑04/05/10 create orders. There are no delete endpoints. To reset, see §8 (Data).

### 6.5 UI bugs found

| ID | Bug | Where | Suggested fix |
|---|---|---|---|
| UI‑1 | Header menu button has no handler; below 768 px the Login/Logout links can't be reached | `header.component.html` | toggle the `#mobile-menu` visibility on click (or always show the auth link) |
| UI‑2 | `orderSuccess` / `orderFailed` are never reset, so a success message sticks over later failures | `home-page.component.ts` `orderProduct()` | reset all three flags at the start of every order |
| UI‑3 | **Silent token renewal fails** ("authCallback incorrect nonce … token(s) validation failed"), so the user is logged out about 4.5 min after login | `auth.config.ts` | add `ignoreNonceAfterRefresh: true` (Keycloak keeps the original nonce in refreshed ID tokens) |
| UI‑4 | Every **Order Now** click subscribes to `userData$` and never unsubscribes. Old subscriptions re-run on every user-data change (seen as the `null … email` errors on logout); if user data is refreshed instead of cleared, earlier clicks' orders could be sent again | `home-page.component.ts` `orderProduct()` | use `userData$.pipe(take(1))` |
| UI‑5 | Validation messages for min length and price > 0 never trigger; only `required` validators exist (price 0 accepted) | `add-product.component.ts` | add `Validators.minLength(...)` / `Validators.min(0.01)` to match the template |
| UI‑6 | Home page asks for products before login and shows a 401 as "No products found!" | `home-page.component.ts` `ngOnInit()` | load products only when authenticated (or handle the error) |
| UI‑7 | Greeting "Hi {username}" is `text-white` on a white header in light mode | `header.component.html` | use `text-gray-900 dark:text-white` |
| UI‑8 | `/add-product` has no route guard and failed saves show no error | `app.routes.ts`, `add-product.component.ts` | add an auth guard; show an error on failure |
| UI‑9 | Success/failure messages render at the top of the list, out of view when ordering lower rows; no "back" link from Add Product | `home-page` / `add-product` templates | show the message next to the row (or as a toast); add a back link |

---

## 7. Findings this test plan surfaced

API and service findings (UI bugs are in §6.5):

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

## 8. Notes & troubleshooting

- **Data:** runs add products and orders to your local databases. There are no delete endpoints, and folder 5.1 adds a blank product that also shows up in the web app. To start fresh: `./launch.sh stop --all`, delete the data folders listed in [RUNNING.md](RUNNING.md) §0, then `./launch.sh`.
- **401 everywhere:** the token expired (5 min). Run 0.1 again.
- **Connection refused:** the stack isn't running. Check with `./launch.sh status`.
- **4.5 fails with `OPEN`/`HALF_OPEN`:** you ran folder 6 recently. Place 3 successful orders, or wait, and run it again.
- **Every request after 0.1 returns 401:** no environment is selected, so the token had nowhere to be saved. Select **Microservices Shop: local** and run 0.1 again.
- **Test scripts didn't import (Insomnia):** the requests still work. Copy the `access_token` from the 0.1 response into the `accessToken` environment variable by hand and compare results with the matrix above.
