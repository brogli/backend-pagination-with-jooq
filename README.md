# Backend Pagination With Jooq

## About

Spring JPA can do
[offset and keyset based pagination](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html#repositories.scrolling.guidance).
With JOOQ you have to hand-roll it. Since offset based pagination doesn't scale well, I went for a
keyset pagination POC.

A Vue 3 + PrimeVue 4 SPA browses a 1M-row Postgres table through a Spring Boot 4 backend that uses
jOOQ's `seek()` for keyset pagination. Page cost stays constant regardless of depth, unlike
`LIMIT/OFFSET`. The seek key travels as an opaque base64-encoded cursor in a query param, so
refresh, bookmarks, and browser back/forward all round-trip. Each page response carries both
`nextCursor` and `prevCursor`, enabling bidirectional walking without a client-side stack. Detailed
design lives in [`docs/`](./docs/).

## Findings

Measured against a containerized backend + Postgres pair (`deploy/`), 1M-row dataset, 100 virtual
users walking a realistic browse pattern for 60 s — 42 945 requests, 0 errors, ~630 req/s. Reproduce
with [`tools/load/run.sh`](./tools/load/).

- **Sub-millisecond median page time, end-to-end.** `/api/books` clocks p50 0.69 ms, p95 1.03 ms,
  p99 1.62 ms from inside the load container.
- **Postgres is essentially free.** With aligned composite indexes, mean per-query exec time is ~66
  µs and 100% of reads are buffer-cache hits. PostgreSQL accounts for ~10% of the end-to-end budget;
  the rest is JDBC + jOOQ, Spring routing, Jackson, and the cursor codec.
- **Backend memory holds steady.** JVM sits at ~370 MiB inside its 400 MiB limit with no growth
  across the run; CPU peaks at ~52% under sustained 630 req/s.

## Running locally

Prereqs: Java 25 (`sdk install java 25.0.3-tem`), podman, Node.js + pnpm (versions pinned in
`frontend/package.json`; `corepack enable` handles pnpm).

### Database

```bash
podman kube play deploy/postgres.yaml          # up
podman kube play --down deploy/postgres.yaml   # down
```

Postgres 16 on `localhost:5432`, db `books`, user/pass `postgres`/`postgres`.

### Backend

```bash
./gradlew :backend:bootRun                                  # no test data
SPRING_PROFILES_ACTIVE=local ./gradlew :backend:bootRun     # seed 1M rows on first startup
```

`curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.

### Frontend

```bash
./gradlew :frontend:pnpmDev    # Vite dev server on http://localhost:5173
```

## Backend container

Build the image (Jib needs a docker-compatible socket — for podman:
`export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock`):

```bash
./gradlew :backend:jibDockerBuild              # → backend-pagination-with-jooq:local-jib
```

Run via the kube-style manifest:

```bash
podman kube play deploy/backend.yaml           # up
podman kube play --down deploy/backend.yaml    # down
```
