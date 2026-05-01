# CLAUDE.md

Concise notes for Claude working in this repo. Human-facing docs live in `README.md`.

## Layout

- Spring Boot app lives in `backend/`. Always invoke Gradle as `./gradlew` from
  `backend/`, never globally-installed `gradle`.
- `docs/` is the canonical, evolving design (grows as features land).
  `_claude-plans/backend-pagination/` is the original proposal — historical
  context, not source of truth.

## Build stack pinned versions

- Spring Boot **4.0.6** (Groovy DSL, `build.gradle` not `.kts`)
- Gradle wrapper **9.4.1**
- Java toolchain **25** (Temurin via sdkman)
- Liquibase **5.0.2** (transitive from Boot starter; pinned on `buildscript`
  classpath to match)
- jOOQ **3.19.32** (BOM-managed; pinned on `buildscript` classpath for
  codegen). **Codegen and runtime jOOQ versions MUST match** — generated
  code can reference symbols added in newer versions (`resetTouchedOnNotNull`
  is 3.20+) and break `compileJava` against an older runtime. Verify with
  `./gradlew dependencyInsight --dependency org.jooq:jooq`.
- PostgreSQL JDBC driver **42.7.10** (BOM-managed; pinned on `buildscript`)
- Testcontainers **1.21.3** (only on `buildscript` for codegen; not in
  runtime/test deps yet)

## Spring Boot 4 gotchas learned

- **Liquibase needs `spring-boot-starter-liquibase`** (the proper starter).
  `liquibase-core` alone or `spring-boot-liquibase` (no `-starter`) don't get
  picked up by autoconfig — `LiquibaseAutoConfiguration` won't even appear in
  the conditions report and Liquibase silently doesn't run. Symptom: no
  Liquibase log lines at startup, no `databasechangelog` table created.
- The starter brings `spring-boot-jdbc` transitively; you don't need
  `spring-boot-starter-jdbc` separately.
- Spring Boot 4 modularized auto-configs into per-feature jars — many things
  that "just worked" via `spring-boot-autoconfigure` in 3.x now need an
  explicit module-or-starter dependency. If something autoconfigures in 3.x
  but not 4.x, suspect a missing per-feature module.

## Local environment

- `podman` is the supported container runtime (kube-play for Postgres, run
  for the OCI image). Don't add docker fallbacks to docs/README/build.
- Use `sdk use java 25.0.3-tem` before running `./gradlew` (it's already in
  sdkman).
- `bootBuildImage` requires the rootless podman socket and `DOCKER_HOST`:
  - User socket via `systemctl --user enable --now podman.socket` (Ubuntu
    masks `podman.socket` and `podman.service` at `/etc/xdg/systemd/user/`
    and `/etc/systemd/user/`; both must be `sudo rm`'d before unmasking
    works — `systemctl --user unmask` only touches `~/.config/systemd/user`).
  - `DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock` (already in
    `~/.bashrc`; not picked up by non-interactive shells, so inline-export
    it for one-off Bash invocations).
  - `build.gradle` sets `bootBuildImage { docker { bindHostToBuilder = true } }`
    — required so the buildpacks lifecycle creator container sees the
    podman socket at `/var/run/docker.sock` (the path it hardcodes). Without
    this flag the build fails with `permission denied while trying to
    connect to the docker API at unix:///var/run/docker.sock`. No-op for
    real dockerd setups.

## Liquibase + jOOQ codegen (Phase 2)

- **Schema/seed/index split**: `db/changelog/master.xml` includes
  `001-schema.sql` (table only), `002-seed-large.sql` (gated on
  `context="seed-large"`), `003-indexes.sql` (table only after the seed).
  Order matters: putting indexes *after* the seed means a fresh
  `local`-profile bootRun inserts 1M rows into an unindexed table
  (~3s instead of ~20s) and then builds the indexes once with
  `max_parallel_maintenance_workers=4` (~5s). Total Liquibase work
  ~8s vs ~22s with indexes-during-insert. Caveat: this fast path
  requires a fresh DB. If indexes already exist when the seed runs
  (e.g. you started without `local` first, then enabled it), the seed
  pays per-row index maintenance and you're back to the slow path.
  Tear down and restart with `local` for the fast path.
- **`seed-large` is opt-in** at *both* runtime and codegen:
  - App runtime: `spring.liquibase.contexts: '!seed-large'` in
    `application.yml`. Without it, every `bootRun` blocks ~22s loading 1M
    rows and overwrites real data on top of any existing DB.
  - Codegen: `new Contexts('!seed-large')` in `build.gradle`'s task body.
    The codegen testcontainer would also pay 22s for nothing — codegen
    only reads metadata, never row data.
- **Don't use `new Contexts()` (empty)** thinking it excludes things.
  Empty Contexts matches *every* changeset including tagged ones. Negation
  expressions are the right form: `'!seed-large'`, `'a and !b'`, etc.
  Spring Boot's `spring.liquibase.contexts` is a thin pass-through and
  honors them.
- **Seed the dev DB**: run the app with the `local` Spring profile —
  `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun` (or
  `./gradlew bootRun --args='--spring.profiles.active=local'`).
  `application-local.yml` overrides `spring.liquibase.contexts` from
  `'!seed-large'` to `'seed-large'`, so the 1M-row seed runs on startup
  (~22s, then app comes up). Subsequent startups don't re-seed because
  the changeset is recorded in `databasechangelog`. To re-seed, tear
  down the dev pod (`podman kube play --down deploy/postgres.yaml`) or
  truncate the table manually.
- **Codegen pattern: programmatic, not `nu.studer.jooq` plugin.** A single
  Groovy `generateJooq` task in `build.gradle` starts a `PostgreSQLContainer`,
  applies Liquibase against it, calls
  `org.jooq.codegen.GenerationTool.generate(new Configuration().withJdbc(…))`
  with the live container's JDBC URL/credentials, then stops the container.
  We tried `nu.studer.gradle-jooq-plugin:10.2` first; it produced four
  paper cuts in Groovy DSL: (a) `generationTool { … }` is the Groovy entry
  point, not `jooqConfiguration { … }` (which takes a plain Action with no
  DSL sugar); (b) jOOQ's `urlProperty` element is 3.20+ only, plugin
  default is 3.19.x; (c) `System.setProperty` from `doFirst` doesn't reach
  the plugin's forked codegen JVM (use `javaExecSpec`); (d) `BuildService`
  + shared-service plumbing is overkill for a single-task pipeline. The
  programmatic version is ~30 lines and avoids all four. The plugin
  alternatives that *do* bundle testcontainers (e.g.
  `dev.monosoul.jooq-docker`) are Flyway-only.
- **JDBC driver classloader trap**: `DriverManager.getConnection` rejects
  drivers registered from the buildscript classloader when called from a
  Gradle task action. Bypass by instantiating the `Driver` and calling
  `.connect(url, props)` directly.
- **XML-comment trap in changesets**: Liquibase parses changesets via SAX
  which forbids `--` *inside* an XML comment (`<!-- … -->`). Don't quote
  CLI flags like `--contexts=foo` in comment bodies — rephrase.
- **DOCKER_HOST for codegen**: testcontainers needs the env var set for
  every non-interactive shell, same as `bootBuildImage`:
  `DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock ./gradlew …`.

## Running things

```bash
cd backend
./gradlew generateJooq                              # codegen against ephemeral testcontainer
./gradlew bootRun                                   # dev mode, no test data
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun      # dev mode, seed 1M rows on first startup
./gradlew bootBuildImage                            # OCI image: backend-pagination-with-jooq:local
```

DataSource defaults: `jdbc:postgresql://localhost:5432/books`,
`postgres`/`postgres`. Override via `SPRING_DATASOURCE_*` env vars.
