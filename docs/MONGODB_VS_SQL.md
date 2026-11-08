# MongoDB vs SQL — Datastore Rationale (Polyglot Persistence)

> Companion to [ARCHITECTURE.md](ARCHITECTURE.md) and [DATABASE_CLOUD_STRATEGY.md](DATABASE_CLOUD_STRATEGY.md).
> Explains **why this system uses MongoDB for the product catalog and relational PostgreSQL for orders and inventory** — and when a document database is the better choice vs a relational one.
> **Date:** 2026‑11‑08 • **Status:** Rationale / reference.

---

## 1. Key message

MongoDB is **not universally "better" than SQL** — it is a better fit for **specific workloads**. This project already demonstrates the correct instinct: it uses **different datastores for different data shapes** — a pattern called **polyglot persistence**.

| Service | Datastore | Why |
|---|---|---|
| **product-service** | MongoDB (document) | Catalog = self-contained, schema-variable, read-mostly aggregates |
| **order-service** | PostgreSQL (relational) | Orders = relational, transactional, consistency-critical |
| **inventory-service** | PostgreSQL (relational) | Stock = constraints + ACID (prevent overselling) |

> **The real lesson:** match the datastore to the data's **shape** and **consistency needs** — not "one database type wins."

---

## 2. The core difference

### Relational (SQL)
- Data split across **normalized tables** connected by foreign keys.
- One logical object may span many tables; reassembled at read time with **JOINs**.
- Schema is **fixed and enforced by the database**.

### MongoDB (document)
- Data stored as **self-contained JSON-like documents** (BSON).
- One logical object = **one document**, stored and read as a whole.
- Schema is **flexible** — enforced by the application, not the engine.

### The project's `Product` document
```java
@Document(value = "product")
public class Product {
    @Id private String id;
    private String name;
    private String description;
    private String skuCode;
    private BigDecimal price;
}
```
A product is a **single self-contained aggregate** — no joins to other products, no parent/child relationships. `getAllProducts()` is a single collection read. This is the textbook sweet spot for a document store.

---

## 3. Why MongoDB fits `product-service`

| Catalog characteristic | Why MongoDB wins here |
|---|---|
| **Products are self-contained** | Whole product lives in one document — no joins to assemble the object. |
| **Read-heavy, simple access** | Catalog browsing is mostly reads by id/list; MongoDB serves whole documents fast. |
| **Variable / evolving attributes** | A book has `author/pages`; a shirt has `size/color`; electronics have `voltage`. In SQL this forces sparse columns, EAV tables, or many sub-tables. In MongoDB each document carries its own fields — no migration to add a field. |
| **Schema flexibility during dev** | No `ALTER TABLE` / migration to reshape a document — faster iteration for a catalog whose shape keeps changing. |
| **Horizontal scale / sharding** | MongoDB shards natively (e.g. by `skuCode`), scaling reads/writes horizontally — a natural fit for a cloud catalog. |

In short: the catalog is a collection of **independent, shape-varying, read-mostly aggregates** — exactly what document databases are designed for.

---

## 4. General advantages of MongoDB over relational databases

1. **Object–data impedance match.** Domain objects are already JSON-shaped (the SPA sends/receives JSON); MongoDB stores that JSON almost verbatim — less ORM translation friction.
2. **No joins for aggregates.** Embedding related data in one document returns the whole object in a single read.
3. **Flexible / evolving schema.** Add or drop fields per document without `ALTER TABLE` or downtime.
4. **Horizontal scalability (sharding) is first-class.** Built-in sharding + replica sets spread data and reads across nodes.
5. **High write throughput for independent documents.** No multi-table coordination for a single-document write.
6. **Natural fit for semi-structured / unstructured data** — product specs, catalogs, content, logs, variable-attribute profiles.

---

## 5. The other side — where SQL wins (and why order/inventory use it)

MongoDB is **not** the better choice when data is **highly relational and transactional** — which is exactly `order` and `inventory`.

| Need | Why SQL is right for order / inventory |
|---|---|
| **Strong transactions (ACID)** | An order touching multiple rows, or decrementing stock, needs guaranteed atomicity and isolation — the relational core strength. |
| **Relationships / joins** | Orders → order-lines → products → customers are inherently relational; documents would duplicate data. |
| **Data integrity constraints** | Foreign keys, unique, `NOT NULL`, `CHECK` — the engine enforces correctness (e.g. stock ≥ requested). |
| **Complex ad-hoc queries / reporting** | Financial/order analytics (`GROUP BY`, aggregates, cross-entity reporting) is mature and expressive in SQL. |
| **Consistency over availability** | Money and stock must be correct, not merely fast. |

That is why the design puts **money and stock in PostgreSQL** (consistency-critical, relational, transactional) and **the catalog in MongoDB** (flexible, read-heavy, independent documents).

---

## 6. Important caveats about "MongoDB is better"

- **MongoDB has multi-document ACID transactions** (since 4.0), but they are not its strength and carry a performance cost — if you lean on them heavily, you probably wanted a relational DB.
- **Schema flexibility is double-edged** — "no enforced schema" means *your application* must enforce consistency, or data drifts silently. Discipline moves from the DB into your code.
- **Denormalization / embedding duplicates data** — great for reads, but every update must touch each copy.
- **Not a scalability silver bullet** — like the MySQL vs PostgreSQL discussion, the engine isn't what makes you scalable; access patterns and architecture are.

---

## 7. Side-by-side summary

| Dimension | MongoDB (document) | SQL / Relational |
|---|---|---|
| Data model | Self-contained documents (BSON) | Normalized tables + foreign keys |
| Schema | Flexible, app-enforced | Fixed, engine-enforced |
| Relationships | Embed or reference (no native joins*) | Joins, first-class |
| Transactions | Single-doc atomic; multi-doc possible but costly | Full ACID across tables |
| Integrity constraints | Application-level | Engine-level (FK, unique, check) |
| Scaling model | Native sharding (scale-out) | Replicas; sharding harder |
| Best for | Catalogs, content, variable/semi-structured, read-heavy aggregates | Orders, payments, inventory, anything relational + transactional |
| In this project | product-service | order-service, inventory-service |

\* `$lookup` exists but is not a substitute for relational joins at scale.

---

## 8. Decision record

| | |
|---|---|
| **Decision** | Polyglot persistence — MongoDB for the catalog, PostgreSQL (moved from MySQL per the cloud strategy) for orders and inventory. |
| **Driver** | Match datastore to data shape + consistency needs. |
| **Catalog → MongoDB** | Independent, schema-variable, read-mostly aggregates. |
| **Orders/Inventory → SQL** | Relational, ACID, constraint-enforced, consistency-critical. |
| **Anti-pattern avoided** | Using one engine for everything (migrations-per-attribute on SQL catalog, or fighting MongoDB for transactional stock/money). |
