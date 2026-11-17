# Registration e2e run 3 — full behaviour walkthrough (2026-11-17)

Third UI run against the local stack (`./launch.sh start`, browser at `http://localhost:4200`), on
commit `e51611e` (includes the fix for the stale error banner found in [run 2](../run-2/README.md)).
It covers the whole journey: guest → register (with every rejection path) → log in (with a failed
attempt) → add a product → log out.

Test account created by this run (local dev Keycloak only):

| Field    | Value                      |
|----------|----------------------------|
| Username | `e2eshopper03`             |
| Email    | `e2eshopper03@example.com` |
| Password | `E2eTest2026`              |

## Steps and results

| # | Step | Expected | Actual | Result | Screenshot |
|---|------|----------|--------|--------|------------|
| 1 | Open `/` as a guest | No products; Login + Register in header | "Please log in to see the products." | ✅ | [01](01-guest-home.jpg) |
| 2 | Click Register | Empty form, submit disabled | As expected | ✅ | [02](02-register-empty-form.jpg) |
| 3 | Username `a!`, email `not-an-email`, names left empty, password `weak` | Inline error per field, submit disabled | Min length + allowed characters, invalid email, required names, password length + complexity | ✅ | [03](03-register-validation-errors.jpg) |
| 4 | Valid fields, confirmation differs | "Passwords do not match.", submit disabled | As expected | ✅ | [04](04-register-password-mismatch.jpg) |
| 5 | Matching passwords, taken username `e2eshopper01` → submit | 409: banner + field error | "This username is already taken" (banner and under the field) | ✅ | [05](05-register-duplicate-username.jpg) |
| 6 | Change username to `e2eshopper03` | Banner **and** field error clear, submit enabled | Both cleared, button enabled (run-2 bug is fixed) | ✅ | [06](06-register-error-cleared-on-edit.jpg) |
| 7 | Submit | 201, success message with Log in button | "Account created. You can now log in." | ✅ | [07](07-register-success.jpg) |
| 8 | Click Log in | Keycloak sign-in page (PKCE, `angular-client`) | As expected | ✅ | [08](08-keycloak-login.jpg) |
| 9 | Sign in with a wrong password | Rejected | "Invalid username or password." | ✅ | [09](09-login-wrong-password.jpg) |
| 10 | Sign in with the correct password | Back on `/`, logged in | Header "Hi e2eshopper03", product list (14) | ✅ | [10](10-logged-in-product-list.jpg) |
| 11 | Create Product: SKU `e2e_xperia_1_vi`, "Sony Xperia 1 VI (E2E)", price 1099 | Form valid | Filled, Add Product enabled | ✅ | [11](11-add-product-filled.jpg) |
| 12 | Submit | Product created | "Product Created Successfully" | ✅ | [12](12-add-product-success.jpg) |
| 13 | Back to the list | Count 15, new product listed | "Sony Xperia 1 VI (E2E) — Price: 1099" | ✅ | [13](13-new-product-in-list.jpg) |
| 14 | Logout | Back to guest view | "Please log in to see the products." | ✅ | [14](14-logged-out.jpg) |

**14 / 14 steps passed.**

## Backend checks

- `t_user_profile` (user_service, port 5433) gained exactly one row, `e2eshopper03`. The rejected
  duplicate attempt in step 5 wrote nothing.
- MongoDB `product-service.product` has `e2e_xperia_1_vi` with price `1099`.

## Findings

- **Low-contrast labels and headings — fixed after this run.** Steps 1–14 above were captured
  before the fix, so their labels/headings still look dim. Cause: the app set no page background or
  text colour, so in a browser using dark mode the page showed the browser's dark canvas behind black
  headings and `text-gray-700` labels. Fix: `styles.css` gives `body` explicit colours for both
  schemes (`bg-white text-gray-900` / `dark:bg-gray-900 dark:text-gray-100`), and form labels got
  `dark:text-gray-300`. Re-checked in both schemes:

  | Page | Dark mode | Light mode |
  |------|-----------|------------|
  | `/register` | [15](15-contrast-fix-register-dark.jpg) | [18](18-contrast-fix-register-light.jpg) |
  | `/add-product` | [16](16-contrast-fix-add-product-dark.jpg) | [19](19-contrast-fix-add-product-light.jpg) |
  | `/` (heading) | [17](17-contrast-fix-home-dark.jpg) | — |

- The stale-banner bug from run 2 is fixed (step 6).
- Unlike run 2's screenshot 09, the price in screenshot 11 is correct: this run typed it with real
  keystrokes instead of setting the value by script.
