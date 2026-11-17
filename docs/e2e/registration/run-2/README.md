# Registration e2e run 2 — behaviour walkthrough (2026-11-17)

Second UI run against the local stack (`./launch.sh start`, browser at `http://localhost:4200`).
Unlike [run 1](../README.md) (happy path only), this run also exercises the negative paths:
client-side validation, the server-side duplicate check and a failed login.

Test account created by this run (local dev Keycloak only):

| Field    | Value                      |
|----------|----------------------------|
| Username | `e2eshopper01`             |
| Email    | `e2eshopper01@example.com` |
| Password | `E2eTest2026`              |

## Steps and results

| # | Step | Expected | Actual | Screenshot |
|---|------|----------|--------|------------|
| 1 | Open `/` as a guest (after logging out) | No products, Login + Register in header | "Please log in to see the products." | [01](01-guest-home.jpg) |
| 2 | `/register`: username `a!`, email `not-an-email`, names empty, password `weak` | Inline errors, submit disabled | Errors for min length + allowed characters, invalid email, required names, password length + complexity; button disabled | [02](02-register-validation-errors.jpg) |
| 3 | Valid fields, confirmation differs from password | Mismatch error, submit disabled | "Passwords do not match."; button disabled | [03](03-register-password-mismatch.jpg) |
| 4 | Matching passwords, username `e2euser1117` (already registered) → submit | 409, error on Username | Banner + field error "This username is already taken."; no profile row written | [04](04-register-duplicate-username.jpg) |
| 5 | Change username to `e2eshopper01` | Field error clears, submit enabled | Field error cleared, button enabled — **but the top banner stays** (see findings) | [05](05-register-corrected-form.jpg) |
| 6 | Submit | 201, success message | "Account created. You can now log in." | [06](06-register-success.jpg) |
| 7 | Log in → Keycloak, wrong password | Rejected | "Invalid username or password." | [07](07-login-wrong-password.jpg) |
| 8 | Correct password | Back on `/`, logged in | Header "Hi e2eshopper01", product list (13) | [08](08-logged-in-product-list.jpg) |
| 9 | Create Product: SKU `e2e_galaxy_s25`, "Samsung Galaxy S25 (E2E)", price 899.5 | Form filled | Filled (see note on price) | [09](09-add-product-filled.jpg) |
| 10 | Submit | Product created | "Product Created Successfully" | [10](10-add-product-success.jpg) |
| 11 | Back to the list | Count 14, new product listed | "Samsung Galaxy S25 (E2E) — Price: 899.5" | [11](11-new-product-in-list.jpg) |

Backend checks:
- `t_user_profile` (user_service, port 5433) has the `e2eshopper01` row; the rejected duplicate attempt
  (`e2e.dup@example.com`) left no row.
- MongoDB `product-service.product` has `e2e_galaxy_s25` with price `899.5`.

## Findings

1. **Stale error banner on the register page — fixed.** After a 409, changing the username cleared
   the field error and re-enabled the button, but the top banner "This username is already taken"
   stayed until the next submit. `RegisterComponent` now clears `errorMessage` on any form value
   change. Re-checked after the fix: banner shown after the 409 ([12](12-fix-banner-after-409.jpg)),
   gone as soon as the username is edited ([13](13-fix-banner-cleared-on-edit.jpg)).
2. **Low-contrast labels and headings (cosmetic).** On `/register` and `/add-product`, field labels
   and the page heading are dark grey/black on the dark background.
3. **Screenshot 09 shows Price 0.** The price was set by the test tool and the screenshot caught a
   stale render. The saved product has price 899.5 (DB and list both confirm), so this is a
   capture artefact, not an app defect.
