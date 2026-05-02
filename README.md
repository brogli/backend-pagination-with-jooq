# backend-pagination-with-jooq

Keyset-paginated book browser. Spring Boot 4 + jOOQ + PostgreSQL backend with
a Vue 3 frontend. Documentation lives in [`docs/`](./docs/).

## Prerequisites

- Java 25 (`sdk install java 25.0.3-tem`)
- podman
- Node.js — version pinned via `engines` in `frontend/package.json`. Use `nvm install && nvm use` (or `fnm use`).
- pnpm — version pinned via `packageManager` in `frontend/package.json`. Easiest path is [Corepack](https://nodejs.org/api/corepack.html): `corepack enable`.

## Run the database

```bash
podman kube play deploy/postgres.yaml          # up
podman kube play --down deploy/postgres.yaml   # down
```

Postgres 16 listens on `localhost:5432`, db `books`, user/pass `postgres`/`postgres`.

## Run the backend

```bash
./gradlew :backend:bootRun                                  # no test data
SPRING_PROFILES_ACTIVE=local ./gradlew :backend:bootRun     # seed 1M rows on first startup
```

Then `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.

Liquibase runs at startup against `db/changelog/master.xml`.

## Run the frontend

```bash
./gradlew :frontend:pnpmDev    # Vite dev server on http://localhost:5173
```

Equivalent to `cd frontend && pnpm dev`. Vite proxies `/api` to the backend on
`:8080`, so run `:backend:bootRun` in another terminal. The Hey API client is
regenerated as a `predev` step from the backend's `openapi.yaml`.

## Build the frontend

```bash
./gradlew :frontend:assemble                # type-check + vite build → frontend/dist/
./gradlew :frontend:pnpmGenApi              # regenerate Hey API client only
```

Phase 5 will wire the backend jar to embed `frontend/dist/`; until then `dist/`
is just a build artifact.

## Format and lint

Backend (Java + Gradle/YAML/XML formatting via Spotless):

```bash
./gradlew :backend:spotlessApply    # format
./gradlew :backend:spotlessCheck    # verify (also runs as part of `check`)
```

Java is formatted with google-java-format (AOSP, 4-space indent). YAML, XML,
and Gradle files get trailing-whitespace and final-newline normalization only.

Frontend (oxc toolchain — oxlint + oxfmt; ESLint owns Vue/TS rules only):

```bash
cd frontend
pnpm lint           # oxlint then ESLint, both with --fix
pnpm format         # oxfmt src/
pnpm type-check     # vue-tsc --build
pnpm test:unit      # Vitest (jsdom)
```

## Build and run as a container

`bootBuildImage` talks to the container runtime via a docker-compatible
socket. One-time setup for podman:

```bash
systemctl --user enable --now podman.socket
echo 'export DOCKER_HOST="unix:///run/user/$(id -u)/podman/podman.sock"' >> ~/.bashrc
```

Then in a fresh shell:

```bash
./gradlew :backend:bootBuildImage
```

This produces `backend-pagination-with-jooq:local` via Paketo buildpacks
(no Dockerfile, CDS-enabled JVM image).

Run it against the running Postgres pod:

```bash
podman run --rm -p 8080:8080 --network=host \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/books \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  backend-pagination-with-jooq:local
```
