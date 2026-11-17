# Registration e2e run — 2026-11-17

Manual UI end-to-end run of the registration feature against the local stack (`./launch.sh start`),
driven in a browser at `http://localhost:4200`.

Test account created by this run (local dev Keycloak only):

| Field    | Value                     |
|----------|---------------------------|
| Username | `e2euser1117`             |
| Email    | `e2euser1117@example.com` |
| Password | `E2eTest2026`             |

## Steps and results

| # | Step | Result | Screenshot |
|---|------|--------|------------|
| 1 | Open `/register` as a guest | Form renders, "Create account" disabled until valid | [01](01-register-empty-form.jpg) |
| 2 | Fill username, email, names, password + confirmation | Form valid, button enabled | [02](02-register-filled-form.jpg) |
| 3 | Submit | `POST /api/user/register` → 201, "Account created. You can now log in." | [03](03-register-success.jpg) |
| 4 | Click "Log in" | Redirect to Keycloak sign-in (PKCE, `angular-client`) | [04](04-keycloak-login.jpg) |
| 5 | Sign in as the new user | Back on `/`, header "Hi e2euser1117", product list loads (12) | [05](05-logged-in-product-list.jpg) |
| 6 | Create Product → fill SKU `e2e_pixel_9`, "Google Pixel 9 (E2E)", price 799 | Form filled | [06](06-add-product-filled.jpg) |
| 7 | Submit | "Product Created Successfully" | [07](07-add-product-success.jpg) |
| 8 | Back to the product list | Count 13, "Google Pixel 9 (E2E) — 799" listed | [08](08-new-product-in-list.jpg) |

Backend check: `t_user_profile` in `user_service` (port 5433) holds the `e2euser1117` row with the
Keycloak id; no password is stored there.
