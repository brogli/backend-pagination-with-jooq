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
- Liquibase 5.x is brought in transitively by Spring Boot 4.0.6

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

## Running things

```bash
cd backend
./gradlew bootRun         # dev mode against localhost:5432
./gradlew bootBuildImage  # OCI image: backend-pagination-with-jooq:local
```

DataSource defaults: `jdbc:postgresql://localhost:5432/books`,
`postgres`/`postgres`. Override via `SPRING_DATASOURCE_*` env vars.
