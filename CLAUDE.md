# CLAUDE.md

## Layout

- Multi-module Gradle build at the repo root. Subprojects: `:backend` (Spring Boot) and `:frontend`
  (Vue 3 + pnpm). Run `./gradlew` from the repo root, never global.
- `docs/` is the canonical evolving design.

## Pinned versions

- Spring Boot **4.0.6** (Groovy DSL, `build.gradle`)
- Gradle wrapper **9.4.1**, Java **25** (Temurin via sdkman; `sdk use java 25.0.3-tem` first)
- Liquibase **5.0.2**, jOOQ **3.19.32**, Postgres JDBC **42.7.10**, Testcontainers **1.21.3**
- `org.openapi.generator` plugin **7.22.0+**
- jOOQ codegen and runtime versions **must match** — newer codegen emits symbols missing in older
  runtime.

## Hard rules

- `podman` only. Don't add docker fallbacks.
- Don't reintroduce the `nu.studer.jooq` plugin — codegen is the programmatic `generateJooq` task.
- No integer-typed enums in `openapi.yaml` (Spring's `WebConversionService` won't chain
  `String → Integer → Enum`). Use bounded integers; string enums are fine.
- No ternary across jOOQ DSL chain steps — branch the call. `step.seek(k)` and `step` are different
  types and the LUB loses `.limit(...)`.
- No `--` inside Liquibase `<!-- ... -->` (SAX rejects).
- Don't use `new Contexts()` (empty) to exclude — it matches everything. Use negation:
  `'!seed-large'`. To exclude multiple contexts use a single AND expression
  (`'!seed-large and !seed-medium'`); comma is OR in Liquibase 5 and `'!a,!b'` accepts changesets
  tagged `a` because they satisfy `!b`.

## Boot 4 surprises

- Liquibase needs `spring-boot-starter-liquibase` (not `liquibase-core` or `spring-boot-liquibase`)
  or it silently doesn't run.
- Boot 4 modularized auto-configs into per-feature jars. If something autoconfigured in 3.x but not
  4.x, suspect a missing module.
- Jackson 3 namespaces: `tools.jackson.databind.*` / `tools.jackson.core.*`, **not**
  `com.fasterxml.jackson.*`. Annotations (`com.fasterxml.jackson.annotation.JsonProperty`) are
  unchanged. `JsonProcessingException` is now unchecked.

## Liquibase + jOOQ codegen

- `master.xml` order is schema → `seed-large` → `seed-medium` → indexes. Both seeds run before
  indexes (bulk insert into an unindexed table is dramatically faster); the two seed contexts are
  mutually exclusive so at most one ever contributes rows in a given Liquibase run.
- Both seed changesets are opt-in via Liquibase contexts. Defaults exclude both (`application.yml`
  and codegen: `'!seed-large and !seed-medium'`). `application-local.yml` enables `seed-large`
  (1M-row dev seed). `application-index-audit.yml` enables `seed-medium` (100k-row test seed) and is
  activated by `IndexUsageIT` via `@ActiveProfiles("index-audit")`.
- Seed dev DB with `SPRING_PROFILES_ACTIVE=local ./gradlew :backend:bootRun`. Re-seed by tearing
  down the pod or truncating.
- In Gradle task actions use `Driver.connect(...)` directly — `DriverManager.getConnection` rejects
  buildscript-classloader drivers.
- `bootBuildImage` and codegen testcontainers need
  `DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock` (set in `~/.bashrc`; inline-export for
  non-interactive shells).

## OpenAPI

- Set `configOptions { configPackage: 'ch.brogli.backendpagination.api.config' }` so generated
  config sits inside the `@SpringBootApplication` scan root.
- 3.1.1 idioms: `nullable: true` is removed — use `oneOf: [{$ref}, {type: 'null'}]` or
  `type: [string, "null"]`. `items: true` for "any"-typed arrays.
- `prev` reverse seek uses `limit = size + 1` — forward `.seek()` is strictly greater-than, so the
  prior seed is `size` rows further back than the current first row.

## Frontend (`:frontend`)

- Vue 3 + PrimeVue 4 (styled mode) + Tailwind v4 + vue-router, ported from
  `opinionated-vuejs-starter`. **No Pinia, no Playwright** (vitest stays).
- `gradle-node-plugin` (`com.github.node-gradle.node`) downloads Node + pnpm into `.gradle/nodejs/`;
  builds are hermetic, no nvm needed for `:frontend:assemble`. Node + pnpm versions are pinned in
  **two** places — `frontend/build.gradle` (`node { version, pnpmVersion }`) and
  `frontend/package.json` (`engines`, `packageManager`). Bump both together; renovate will not link
  them.
- Outside Gradle, work in `frontend/` with system pnpm (use nvm or corepack).
- Hey API codegen: `openapi-ts.config.ts` reads
  `../backend/src/main/resources/openapi/openapi.yaml`. Output `frontend/src/api/generated/` is
  gitignored. Runs as `predev` / `prebuild`.

### Frontend conventions

- **Formatting**: oxc toolchain (oxlint + oxfmt) — no Prettier. oxfmt enforces no semicolons, single
  quotes; `.editorconfig` enforces 2-space indent, 100-char line. `pnpm lint` runs both with
  `--fix`.
- **`if`/`else` always braced**. No single-line braceless form: `if (x) doSomething()` →
  `if (x) { doSomething() }`. Applies to early-return guards too.
- **TypeScript strict**: no `any`, no implicit `any`. No non-null assertions (`!`) or `as` casts
  without a `// why:` comment. Exported APIs (functions, composables) need explicit param + return
  types; locals can infer. Treat external data (`fetch`, `JSON.parse`, `localStorage`) as `unknown`
  at the boundary and narrow.
- **Vue SFCs**: type-based forms only — `defineProps<Props>()`,
  `defineEmits<{ change: [value: string] }>()`, `withDefaults(defineProps<Props>(), { ... })`. No
  runtime object form.
- **PrimeVue**: components imported per-file (no global registration). All design-token overrides go
  in `src/theme/preset.ts` — don't inline theme tweaks in `main.ts` or component styles.
- **Tailwind v4**: CSS-first config (no `tailwind.config.js`). Prefer utilities over scoped
  `<style>`; reserve `<style>` for what Tailwind can't express (keyframes, complex selectors). CSS
  layer order `theme, base, primevue, components, utilities` lives in `src/assets/main.css` and is
  mirrored in `main.ts` via `cssLayer` — keep them in sync.
- **Path alias**: `@` → `src/` (`vite.config.ts` and tsconfigs).
- **Views vs components**: `src/views/` = route targets, `src/components/` = reusable pieces.
  Component unit tests next to the component in `__tests__/*.spec.ts`.
- `pnpm type-check` must pass with zero errors before any commit.

## Pagination index contract

EXPLAIN-driven matrix (100k-row `seed-medium` fixture, `LIMIT 25`). Asserted by `IndexUsageIT` —
when you change a row, change the test.

| Verdict      | Meaning                                                                                              |
| ------------ | ---------------------------------------------------------------------------------------------------- |
| `fast`       | Aligned composite index, Index Only Scan                                                             |
| `acceptable` | Index Scan on the sort index with post-filter; planner avoids Seq Scan thanks to LIMIT + selectivity |
| `cliff`      | No usable index path — Seq Scan; must not occur in this matrix                                       |

| Sort        | Filter                  | Index used                          | Verdict    |
| ----------- | ----------------------- | ----------------------------------- | ---------- |
| title       | none                    | `book_title_id_idx`                 | fast       |
| author      | none                    | `book_author_id_idx`                | fast       |
| price       | none                    | `book_price_id_idx`                 | fast       |
| rating      | none                    | `book_rating_id_idx`                | fast       |
| publishedAt | none                    | `book_published_at_id_idx`          | fast       |
| price       | genre = single          | `book_genre_price_id_idx`           | fast       |
| price       | genre IN (≥2)           | `book_price_id_idx`                 | acceptable |
| rating      | inStock                 | `book_in_stock_rating_id_idx`       | fast       |
| publishedAt | language                | `book_language_published_at_id_idx` | fast       |
| price       | genre + inStock         | `book_genre_price_id_idx`           | fast       |
| price       | minRating               | `book_price_id_idx`                 | acceptable |
| publishedAt | priceMin                | `book_published_at_id_idx`          | acceptable |
| title       | publishedAfter          | `book_title_id_idx`                 | acceptable |
| title       | language (no composite) | `book_title_id_idx`                 | acceptable |

Partial-index re-eval on `book_in_stock_rating_id_idx`: hypothetical
`(rating, id) WHERE in_stock = true` does **not** displace the existing composite — planner stays on
the composite for both `inStock=true` and `inStock=false` reads. Keep the composite, do not ship the
partial. Asserted by `IndexUsageIT#partialInStockRatingIndexDoesNotDisplaceComposite`.

Multi-genre `IN (...)` switches off the genre composite (planner picks the plain price index).
Single-genre stays on the composite. Don't add a `(genre[], price, id)` covering index for this —
`acceptable` is correct.

## Running

```bash
./gradlew :backend:generateJooq                              # codegen via testcontainer
./gradlew :backend:openApiGenerate                           # BooksApi + DTOs from openapi.yaml
./gradlew :backend:bootRun                                   # dev, no seed
SPRING_PROFILES_ACTIVE=local ./gradlew :backend:bootRun      # dev + 1M-row seed
./gradlew :backend:bootBuildImage                            # OCI image

./gradlew :frontend:pnpmDev                                  # vite dev server on :5173
./gradlew :frontend:pnpmGenApi                               # regenerate Hey API client
./gradlew :frontend:assemble                                 # type-check + vite build → dist/
```

DataSource: `jdbc:postgresql://localhost:5432/books`, `postgres`/`postgres`. Override via
`SPRING_DATASOURCE_*`.
