# CLAUDE.md

Concise notes for Claude. Human-facing docs in `README.md`.

## Layout

- Multi-module Gradle build at the repo root. Subprojects: `:backend` (Spring Boot) and `:frontend` (Vue 3 + pnpm). Run `./gradlew` from the repo root, never global.
- `docs/` is the canonical evolving design.

## Pinned versions

- Spring Boot **4.0.6** (Groovy DSL, `build.gradle`)
- Gradle wrapper **9.4.1**, Java **25** (Temurin via sdkman; `sdk use java 25.0.3-tem` first)
- Liquibase **5.0.2**, jOOQ **3.19.32**, Postgres JDBC **42.7.10**, Testcontainers **1.21.3**
- `org.openapi.generator` plugin **7.22.0+**
- jOOQ codegen and runtime versions **must match** — newer codegen emits symbols missing in older runtime.

## Hard rules

- `podman` only. Don't add docker fallbacks.
- Don't reintroduce the `nu.studer.jooq` plugin — codegen is the programmatic `generateJooq` task.
- No integer-typed enums in `openapi.yaml` (Spring's `WebConversionService` won't chain `String → Integer → Enum`). Use bounded integers; string enums are fine.
- No ternary across jOOQ DSL chain steps — branch the call. `step.seek(k)` and `step` are different types and the LUB loses `.limit(...)`.
- No `--` inside Liquibase `<!-- ... -->` (SAX rejects).
- Don't use `new Contexts()` (empty) to exclude — it matches everything. Use negation: `'!seed-large'`.

## Boot 4 surprises

- Liquibase needs `spring-boot-starter-liquibase` (not `liquibase-core` or `spring-boot-liquibase`) or it silently doesn't run.
- Boot 4 modularized auto-configs into per-feature jars. If something autoconfigured in 3.x but not 4.x, suspect a missing module.
- Jackson 3 namespaces: `tools.jackson.databind.*` / `tools.jackson.core.*`, **not** `com.fasterxml.jackson.*`. Annotations (`com.fasterxml.jackson.annotation.JsonProperty`) are unchanged. `JsonProcessingException` is now unchecked.

## Liquibase + jOOQ codegen

- `master.xml` order is schema → seed → indexes (indexes after the seed for fast bulk insert).
- `seed-large` is opt-in: `application.yml` excludes it; `application-local.yml` includes it; codegen excludes via `new Contexts('!seed-large')`.
- Seed dev DB with `SPRING_PROFILES_ACTIVE=local ./gradlew :backend:bootRun`. Re-seed by tearing down the pod or truncating.
- In Gradle task actions use `Driver.connect(...)` directly — `DriverManager.getConnection` rejects buildscript-classloader drivers.
- `bootBuildImage` and codegen testcontainers need `DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock` (set in `~/.bashrc`; inline-export for non-interactive shells).

## OpenAPI

- Set `configOptions { configPackage: 'ch.brogli.backendpagination.api.config' }` so generated config sits inside the `@SpringBootApplication` scan root.
- 3.1.1 idioms: `nullable: true` is removed — use `oneOf: [{$ref}, {type: 'null'}]` or `type: [string, "null"]`. `items: true` for "any"-typed arrays.
- `prev` reverse seek uses `limit = size + 1` — forward `.seek()` is strictly greater-than, so the prior seed is `size` rows further back than the current first row.

## Running

```bash
./gradlew :backend:generateJooq                              # codegen via testcontainer
./gradlew :backend:openApiGenerate                           # BooksApi + DTOs from openapi.yaml
./gradlew :backend:bootRun                                   # dev, no seed
SPRING_PROFILES_ACTIVE=local ./gradlew :backend:bootRun      # dev + 1M-row seed
./gradlew :backend:bootBuildImage                            # OCI image
```

DataSource: `jdbc:postgresql://localhost:5432/books`, `postgres`/`postgres`. Override via `SPRING_DATASOURCE_*`.
