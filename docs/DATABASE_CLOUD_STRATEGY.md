# Database & Cloud Scalability Strategy

> Companion to [ARCHITECTURE.md](ARCHITECTURE.md). Captures the decision discussion around moving the relational services (order, inventory) toward a cloud‑scalable setup, and whether to adopt **PostgreSQL** in place of **MySQL**.
> **Date:** 2026‑11‑08 • **Status:** Recommendation / proposal (no code changed yet).

---

## 1. The question

> "Two services use MySQL. For a highly scalable, cloud-hosted app, is **PostgreSQL** the better choice?"

**Short answer:** PostgreSQL is a reasonable, defensible, future-proof choice — but the engine swap is a **modernization / ecosystem decision, not a scalability fix**. It is a common myth that PostgreSQL is inherently "more optimized for the cloud." Both MySQL and PostgreSQL are deeply cloud-optimized and run at massive scale on every major provider.

---

## 2. Scalability levers that actually matter

Engine choice is **not** the primary driver of scalability for this system. The real levers are:

| Lever | Status in this project | Notes |
|---|---|---|
| **Database-per-service** | ✅ Already in place | order and inventory have independent schemas → scale independently |
| **Stateless services** behind gateway | ✅ Available today | scale horizontally with more replicas, any engine |
| **Connection pooling** | HikariCP in-app; add PgBouncer/ProxySQL at edge | critical in serverless/high-fan-out environments |
| **Read replicas** | Not yet | offload read traffic (e.g., inventory checks) |
| **Caching** | Not yet | Redis for hot reads |
| **Partitioning / sharding** | Not yet | needed only at large data volumes |
| **Async / event-driven** | Planned (Kafka) | decouple `order → inventory`; the biggest coupling win |

> **Takeaway:** Swapping MySQL → PostgreSQL by itself does **not** add scalability. The scalability wins come from replicas + pooling + horizontal service scaling + going event-driven.

---

## 3. MySQL vs PostgreSQL — honest comparison

| Dimension | MySQL 8 | PostgreSQL 16 |
|---|---|---|
| Cloud optimization | Excellent (Aurora was *first* released as Aurora MySQL; RDS/Cloud SQL/Azure all first-class) | Excellent |
| Simple high-throughput reads | Often slightly faster | Very good |
| Complex queries / write concurrency | Good | Often stronger (MVCC, richer planner) |
| JSON | `JSON` type | `JSONB` (indexable) |
| Advanced indexing | B-tree, limited | B-tree, **GIN/GiST/BRIN** |
| SQL features | CTEs, window functions | CTEs, window functions, stricter SQL compliance |
| Extensibility | Limited | **PostGIS, pgvector, pg_partman, …** |
| Distributed / NewSQL on-ramp | Fewer options | **Citus, AlloyDB, CockroachDB, YugabyteDB** are Postgres-wire-compatible |
| Kubernetes operator | Available | **CloudNativePG** (community standout) |

**Accurate framing:** PostgreSQL has a **stronger cloud-native ecosystem and more horizontal-scaling / distributed-SQL paths** — *not* "better cloud performance." For a managed single-instance database at this app's current scale, MySQL 8 and PostgreSQL 16 would perform comparably and you likely couldn't tell them apart.

---

## 4. The strongest concrete reason to migrate *in this project*

The services currently run **MySQL 5.7.44, which is past end-of-life** (no security patches). You must move off it regardless. That reframes the real decision as:

> **MySQL 8  vs  PostgreSQL 16** — on a clean slate, targeting the cloud.

Given a cloud target, a trivial schema, and a desire to future-proof, **PostgreSQL 16 is the recommended choice** — slightly richer feature set and ecosystem, no meaningful downside for these schemas.

---

## 5. Why PostgreSQL for *this* app (summary of the recommendation)

1. **Forced move anyway** — MySQL 5.7 is EOL (security + support risk).
2. **Future-proofing** — if sharding/distributed SQL is ever needed, Postgres is the on-ramp (Citus, AlloyDB, Cockroach, Yugabyte).
3. **Richer engine** (JSONB, advanced indexing, stricter SQL) with **no downside** for `t_orders` / `t_inventory`.
4. **Low migration cost** — see below.

> **Scope:** This affects **order-service** and **inventory-service** only. **product-service stays on MongoDB** regardless.

---

## 6. Migration cost — low

- Entities use **JPA / Hibernate**, so the SQL dialect is abstracted → mostly a **driver + dialect + connection-string** change.
- **Flyway** is already declared → author Postgres-flavored migration scripts.
- Schemas are trivial (a few columns each) → minimal risk.
- `@GeneratedValue(strategy = IDENTITY)` maps cleanly to Postgres `GENERATED ... AS IDENTITY` (watch the MySQL `AUTO_INCREMENT` → Postgres identity/`BIGSERIAL` difference in migration SQL).

### Outline of changes per service (order, inventory)

| Area | MySQL (today) | PostgreSQL (proposed) |
|---|---|---|
| `pom.xml` | `mysql-connector-j`, `flyway-mysql` | `org.postgresql:postgresql`, `flyway-database-postgresql` |
| `application.properties` | `jdbc:mysql://…:3306/…`, MySQL driver | `jdbc:postgresql://…:5432/…`, `org.postgresql.Driver` |
| Dialect | MySQL (auto) | PostgreSQL (auto-detected by Hibernate) |
| `docker-compose.yml` | `mysql:5.7.44` | `postgres:16` (ports 5432 / mapped) |
| Flyway migrations | MySQL DDL | Postgres DDL (identity columns, types) |
| Credentials | plaintext in properties | externalized env / secrets (recommended) |

---

## 7. Recommended phased plan

- **Phase 0 — Hygiene:** Move off EOL MySQL 5.7 (this migration). Externalize DB credentials.
- **Phase 1 — Scale reads:** Add read replicas + connection pooling (PgBouncer); introduce Redis caching for inventory/product reads.
- **Phase 2 — Decouple:** Introduce Kafka; make `order → inventory` event-driven (reservation/decrement, order-confirmation events).
- **Phase 3 — Elastic data tier:** If volume demands, adopt Aurora/AlloyDB or Citus for distributed Postgres.
- **Phase 4 — Platform:** Containerize all apps; Kubernetes + CloudNativePG; managed DB via RDS/Cloud SQL; observability (tracing, metrics, central logs).

---

## 8. Decision record (summary)

| | |
|---|---|
| **Decision** | Adopt **PostgreSQL 16** for order-service and inventory-service; keep MongoDB for product-service. |
| **Primary driver** | Escape EOL MySQL 5.7 + future-proof with the cloud-native/distributed-SQL ecosystem. |
| **Not the driver** | Raw cloud performance — MySQL 8 and Postgres 16 are comparable at this scale. |
| **Scalability source** | Replicas, pooling, stateless horizontal scaling, event-driven decoupling — independent of engine choice. |
| **Effort** | Low (JPA + Flyway abstract most of it). |
| **Blast radius** | order-service, inventory-service only. |
| **Status** | ✅ **Implemented** (branch `feature/postgresql-migration`): both services run on PostgreSQL 16 (host ports 5434 / 5435), Flyway migrations rewritten, Testcontainers on `postgres:16`, `launch.sh` checks readiness with `pg_isready`. Keycloak's own MySQL 8 is unchanged. |
