# Load test (k6)

Realistic browse pattern against the containerized backend: 10 virtual users
walk forward 10 pages, change filter, walk forward 10, walk back 5, change
filter again, repeat. Filter profiles scatter across the index matrix in
`CLAUDE.md`. Runs for 60 s.

## Preconditions

This harness assumes both pods are already up and the database is seeded.
Bring them up like this (the `:local` profile triggers the 1M-row seed on
first boot):

```bash
podman kube play deploy/postgres.yaml
./gradlew :backend:jibDockerBuild
podman kube play deploy/backend.yaml
# wait until /actuator/health is UP (seed takes ~30-60s on first boot)
until curl -fsS http://localhost:8080/actuator/health >/dev/null; do sleep 2; done
```

## Run

```bash
./tools/load/run.sh
```

Override the target with `BASE_URL=http://host:port ./tools/load/run.sh` if
you're pointing at something other than the local pod.

## Outputs

All artifacts land in `tools/load/out/` (gitignored):

| File                       | What                                                                      |
|----------------------------|---------------------------------------------------------------------------|
| `k6-stdout.txt`            | full k6 console output: per-route trends, p50/p90/p95/p99, RPS, error rate |
| `podman-stats.jsonl`       | one timestamped JSON line per second per container                        |
| `podman-stats-before.txt`  | one-shot `podman stats` snapshot before the run                           |
| `podman-stats-after.txt`   | one-shot `podman stats` snapshot after the run                            |
| `pg-stat.txt`              | top 20 queries from `pg_stat_statements` by total exec time               |
| `pg-bgwriter.txt`          | `pg_stat_bgwriter` + `pg_stat_database` (buffer cache hit ratio, IO time) |

`pg_stat_statements` is reset at the start of the run, so the captured stats
cover *only* the load run.

## What it measures

- **Roundtrip time** — k6 measures wall-clock per HTTP call from the
  container. Per-route trends (`rt_first_page`, `rt_next_page`, `rt_prev_page`)
  let us see if first-page and cursor-paged calls behave differently.
- **Postgres time** — `pg_stat_statements.mean_exec_time` is the planner's
  view (parse + plan + execute). `blk_read_time` is the only IO contribution
  when `track_io_timing=on`.
- **Container memory + CPU** — `podman stats` samples both the PG and app
  containers every second.

## Files

- `scenario.js` — the k6 script. Walk pattern + filter profiles live here.
- `run.sh` — orchestration: reset stats, start sampler, run k6, snapshot.
- `out/` — run artifacts, gitignored.
