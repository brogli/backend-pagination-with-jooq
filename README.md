# backend-pagination-with-jooq

## About

A keyset-pagination POC. A Vue 3 + PrimeVue 4 SPA browses a 1M-row
Postgres table through a Spring Boot 4 backend that uses jOOQ's `seek()`
for keyset pagination — page cost stays constant regardless of depth,
unlike `LIMIT/OFFSET`. The seek key travels as an opaque base64-encoded
cursor in a query param, so refresh, bookmarks, and browser back/forward
all round-trip. Each page response carries both `nextCursor` and
`prevCursor`, enabling bidirectional walking without a client-side
stack. Detailed design lives in [`docs/`](./docs/).

## Findings

- **Postgres is essentially free.** With 1M rows and aligned composite
  indexes, mean per-query exec time is ~90 µs and `track_io_timing` shows
  effectively zero disk I/O (100% buffer-cache hits). End-to-end, a
  `/api/books` request takes ~9 ms: ~1% inside Postgres, ~14% JDBC + jOOQ,
  ~85% Spring routing + Jackson + cursor codec.

## Running locally

Prereqs: Java 25 (`sdk install java 25.0.3-tem`), podman, Node.js + pnpm
(versions pinned in `frontend/package.json`; `corepack enable` handles pnpm).

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
