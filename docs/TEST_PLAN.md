# Test Plan: API (Postman / Insomnia / Bruno) and UI (browser)

> Companion to [ARCHITECTURE.md](ARCHITECTURE.md) and [RUNNING.md](RUNNING.md). A hands-on collection for learning the system's flows and checking how each microservice behaves, including failures, plus a manual walkthrough of the web app ([§6](#6-ui-testing-the-web-app-in-the-browser)).
> **Date:** 2026‑11‑08

---

## 1. What's in the box

| File | Purpose | Use it with |
|---|---|---|
| [`api-tests/microservices-shop.postman_collection.json`](../api-tests/microservices-shop.postman_collection.json) | 32 requests in 7 folders, each with a description and test assertions | Postman, Insomnia (Bruno: via Import) |
| [`api-tests/local.postman_environment.json`](../api-tests/local.postman_environment.json) | Connection settings for the local stack (URLs, realm, client, test user) | Postman, Insomnia (Bruno: via Import) |
| [`api-tests/bruno/`](../api-tests/bruno/) | The same collection as a **native Bruno collection** (`.bru` files), environment included | Bruno: **Open Collection** |

**Format:** Postman Collection **v2.1** is the source. The `bruno/` folder is generated from it with Bruno's own converter, exactly what Bruno would write when importing.

| Tool | How to load it | Test scripts | Verified |
|---|---|---|---|
| **Postman** | Import the two JSON files | native | 46/46 assertions with newman (Postman app not installed here) |
| **Bruno** | **Open Collection** → `api-tests/bruno` (or Import the JSON) | translated to Bruno's `bru`/`res` API | **Bruno 3.4.2's own converter and parser**; the `.bru` files pass 46/46 against the live stack |
| **Insomnia** (v9+) | Import the two JSON files | mapped to Insomnia's after-response scripts | **not verified** (Insomnia not installed here) |

The scripts deliberately use a small, common subset of the Postman API that all three can handle: `pm.test`, `pm.expect`, `pm.response.code/json()/text()/headers.get()/responseTime`, and **`pm.environment.get/set`**. Two Bruno details shaped this:
- Bruno deliberately does **not** translate `pm.collectionVariables.set`. Values the scripts store (`accessToken`, `createdProductId`) therefore live in the **environment**, via `pm.environment.set`, which becomes `bru.setEnvVar` in Bruno.
- Bruno turns `pm.response.text()` into `JSON.stringify(res.getBody())`, which wraps plain-text bodies in quotes. So plain-text checks use `to.include(...)` instead of an exact match.

> **How it was verified:** every request and assertion was run against the live stack: folders 0–5 pass **44/44** in both the Postman JSON and the native Bruno files, and folder 6 was checked with the services stopped. After the move of order-service and inventory-service to PostgreSQL 16, everything was re-run: folders 0–5 pass **44/44** with newman and **26/26 requests** with Bruno CLI 2, and folder 6 passes following §5 (breaker `OPEN → HALF_OPEN → CLOSED`). After product-service got input validation (2026‑11‑11), folders 0–5 pass **46/46** with newman and **27 requests / 46 checks** with Bruno CLI 2 (the two new `.bru` files, 2.4 and 2.5, follow the converter's format by hand). If a script doesn't convert in your tool version, the requests still work; you just check the result by eye.

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
| 2.4 | Create product with `{}` | 400 problem response, `errors` names `name`, `description`, `skuCode`, `price`; nothing saved |
| 2.5 | Create product with price `0` (all else valid) | 400, `errors` = `{price: "price must be greater than 0"}` |

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
| ~~5.1~~ | ~~Create product with `{}`~~ | **fixed**: now 400, see 2.4 | input validation added to product-service |
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

Everything below was observed in the running app (Angular 18 dev server, Keycloak 24). First run 2026‑11‑08 found the bugs in §6.5; they were fixed on branch `bugfix/frontend-ui-bugs` and re-checked on 2026‑11‑11.

### 6.1 Before you start

1. Start everything: `./launch.sh` (the frontend is the last step, at http://localhost:4200).
2. Any window width works. Below 768 px the header shows a ☰ button that opens the menu with **Login/Logout**.
3. Open the developer tools: **Network** tab (filter `9000` for API calls, `8181` for Keycloak) and **Console** tab. The console should stay free of red errors in every scenario.
4. Test user: `testuser`, with the password from `api-gateway/docker/keycloak/realms/realm-export.json`.

### 6.2 What happens behind the scenes

| You do | Browser → | Network tab shows |
|---|---|---|
| Open the app logged out | (nothing) | no call to `:9000`: the product list needs a token, so the app doesn't ask for it |
| Click **Login** | app → Keycloak | redirect to `:8181/…/auth?response_type=code&code_challenge_method=S256` (Authorization Code + PKCE) |
| Sign in | Keycloak → app | redirect back to `:4200`, then `POST :8181/…/token` (code exchanged for tokens) |
| Product list loads | app → gateway → product-service | `OPTIONS :9000/api/product → 200` (CORS preflight), `GET :9000/api/product → 200` with `Authorization: Bearer …` |
| **Order Now** | app → gateway → order-service → inventory-service | one `POST :9000/api/order → 201` per click (or 500 when out of stock) |
| **Add Product** | app → gateway → product-service | `POST :9000/api/product → 201` |
| About 4.5 min after login | app → Keycloak | `POST :8181/…/token → 200` (refresh token). The app accepts the new tokens and you stay logged in |

### 6.3 Test cases

Run them in order.

| # | Scenario | Steps | Expected |
|---|---|---|---|
| UI‑01 | Anonymous home page | Open http://localhost:4200 | Header: **Login**. Page: **"Products (0)"** and **"Please log in to see the products."** No **Create Product** button, no call to `:9000`, no console errors. |
| UI‑02 | Login | Click **Login** → sign in as `testuser` | Keycloak page *"Sign in to your account"*, then back to the app: header **"Hi testuser"** + **Logout** (readable in light and dark mode), **"Products (N)"**, **Create Product** button. |
| UI‑03 | Order without quantity | Leave **Quantity** empty → **Order Now** | Red **"Quantity cannot be empty"** under that product. No request is sent. |
| UI‑04 | Successful order | Quantity `1` on an **iPhone 15** row → **Order Now** | Green **"Order Placed Successfully"** under that product; Network: `POST /api/order → 201`. |
| UI‑05 | Out of stock | Quantity `1000` → **Order Now** | Red **"Order Failed, please try again later"** under that product; Network: `POST /api/order → 500`. |
| UI‑06 | Out of stock after a success | Do UI‑04, then (no reload) quantity `1000` on the same row → **Order Now** | The green message is **replaced** by the red one. Network: exactly one new `POST` (500). |
| UI‑07 | Add product: required fields | **Create Product**, click into **SKU Code**, then click another field without typing | **"SKU Code is required."**; **Add Product** is disabled (faded). |
| UI‑08 | Add product: validation | Enter SKU `ab`, Name `x`, Description `short`, keep Price `0`, click between the fields | **"SKU Code must be at least 3 characters long."**, **"Name must be at least 3 characters long."**, **"Description must be at least 10 characters long."**, **"Price must be greater than 0."**; **Add Product** stays disabled. |
| UI‑09 | Add product: success | SKU `oneplus_12`, Name `OnePlus 12`, a description of 10+ characters, Price `799` → **Add Product** | Green **"Product Created Successfully"**, form cleared; Network: `POST /api/product → 201`. **← Back to products** returns to the list without a full reload. |
| UI‑10 | Order the new product | On the list, **OnePlus 12** (last row), quantity `2` → **Order Now** | `POST /api/order → 201`; the message appears under the OnePlus row, no scrolling needed. Use a seeded SKU (iphone_15, pixel_8, galaxy_24, oneplus_12); any other SKU has no stock, so ordering it fails. |
| UI‑11 | Long session | Stay logged in for 5+ minutes (token lifetime is 5 min) | Header still shows **"Hi testuser"**. Network: `POST :8181/…/token → 200` about every 4.5 min. Console: no *"silent renew failed"*. |
| UI‑12 | Order after the refresh | After UI‑11, quantity `1` → **Order Now** | `POST /api/order → 201` with the refreshed token. |
| UI‑13 | Logout | **Logout** | Back to **"Products (0)"**, **"Please log in to see the products."**, header **Login**. Clicking **Login** again shows the Keycloak sign-in form, so the Keycloak session really ended. (Without logging out, **Login** goes straight back in without a password: single sign-on.) |
| UI‑14 | Add product while logged out | Logged out, open http://localhost:4200/add-product directly | You're sent to the Keycloak sign-in page; after signing in you land back on **Add Product**. If a save fails, the page shows **"Product could not be created, please try again later"** and keeps the values. |
| UI‑15 | Narrow window | Make the window narrower than 768 px | **Login/Logout** move behind the ☰ button; clicking it opens and closes the menu. |

### 6.4 Clean-up

UI‑09 creates a product, and UI‑04/05/06/10/12 create orders. There are no delete endpoints. To reset, see §8 (Data).

### 6.5 UI bugs found (all fixed)

Found on 2026‑11‑08, fixed on branch `bugfix/frontend-ui-bugs`. Each fix has a unit test (`ng test`) and was re-checked in the browser.

| ID | Bug | Where | Fix |
|---|---|---|---|
| UI‑1 | Header menu button had no handler; below 768 px the Login/Logout links couldn't be reached | `header.component.*` | the ☰ button toggles the menu (`menuOpen`, `aria-expanded`) |
| UI‑2 | `orderSuccess` / `orderFailed` were never reset, so a success message stuck over later failures | `home-page.component.ts` | one `orderResult` that every click replaces |
| UI‑3 | **Silent token renewal failed** ("token(s) validation failed"), so the user was logged out about 4.5 min after login | `auth.config.ts` | `ignoreNonceAfterRefresh: true` (Keycloak keeps the original nonce in refreshed ID tokens) |
| UI‑4 | Every **Order Now** click subscribed to `userData$` and never unsubscribed; after a silent logout clicks failed with `null … email` and the stale list stayed on screen | `home-page.component.ts` | `userData$.pipe(take(1))`; the list is cleared on logout; a missing user shows "please log in again" |
| UI‑5 | Min-length and price > 0 messages never triggered; only `required` validators existed (price 0 accepted) | `add-product.component.ts` | `Validators.minLength(3/3/10)` and `Validators.min(0.01)`; the disabled button now looks disabled |
| UI‑6 | Home page asked for products before login and showed the 401 as "No products found!" | `home-page.component.ts` | products load only when logged in; logged-out users see "Please log in…", load errors show "Could not load the products…" |
| UI‑7 | Greeting "Hi {username}" was `text-white` on a white header in light mode | `header.component.html` | `text-gray-900 dark:text-white` |
| UI‑8 | `/add-product` had no route guard and failed saves showed no error | `app.routes.ts`, `add-product.component.*` | `autoLoginPartialRoutesGuard` (login, then back to the page); error message on a failed save |
| UI‑9 | Success/failure messages rendered at the top of the list, out of view for lower rows; no "back" link from Add Product | `home-page` / `add-product` templates | the message shows under the ordered product; **← Back to products** link |
| UI‑10 | Header crashed while logged out: `Cannot read properties of null (reading 'preferred_username')` | `header.component.ts` | `userData?.preferred_username ?? ""` |
| UI‑11 | Orders sent `firstName`/`lastName` from fields Keycloak doesn't have, so both were `undefined` | `home-page.component.ts` | use Keycloak's `given_name` / `family_name` (order-service still drops `userDetails`, see §7) |

---

## 7. Findings this test plan surfaced

API and service findings (UI bugs are in §6.5):

| Finding | Where | Suggested fix |
|---|---|---|
| Out of stock / unknown SKU returns **500** | order-service | throw a domain exception mapped to 409/422 with `@ControllerAdvice` |
| **Overselling**: stock never decremented | order + inventory | reserve or decrement stock in the order flow (transactional or Saga) |
| ~~**No input validation**: empty product is saved~~ **Fixed** (2.4, 2.5) | product-service | `@Valid` + `@NotBlank`/`@Positive` on `ProductRequest`; 400 problem response listing each invalid field |
| Services reachable **without auth** on their own ports | all services | keep them on a private network, or make each a resource server |
| `userDetails` sent by the web app is **dropped** | order-service | add it to `OrderRequest`/`Order` (also needed for the planned Kafka notifications) |
| Gateway fallback on a **public** route returns **401** instead of 503 | api-gateway `SecurityConfig` | add `/fallbackRoute` to the permitted URLs |
| No `GET /api/product/{id}` | product-service | add it if clients need single-product lookup |

---

## 8. Notes & troubleshooting

- **Data:** runs add products and orders to your local databases. There are no delete endpoints. Runs from before the product validation fix added blank products (no name or price) that show up in the web app as "- Price:"; remove them with `docker exec mongodb mongosh -u root -p password --authenticationDatabase admin product-service --eval 'db.product.deleteMany({skuCode: null, name: null})'`. To start fresh: `./launch.sh stop --all`, delete the data folders listed in [RUNNING.md](RUNNING.md) §0, then `./launch.sh`.
- **401 everywhere:** the token expired (5 min). Run 0.1 again.
- **Connection refused:** the stack isn't running. Check with `./launch.sh status`.
- **4.5 fails with `OPEN`/`HALF_OPEN`:** you ran folder 6 recently. Place 3 successful orders, or wait, and run it again.
- **Every request after 0.1 returns 401:** no environment is selected, so the token had nowhere to be saved. Select **Microservices Shop: local** and run 0.1 again.
- **Test scripts didn't import (Insomnia):** the requests still work. Copy the `access_token` from the 0.1 response into the `accessToken` environment variable by hand and compare results with the matrix above.
