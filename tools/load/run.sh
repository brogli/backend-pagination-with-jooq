#!/usr/bin/env bash
# Run the k6 load scenario against a containerized backend + postgres.
#
# Preconditions (NOT done by this script — see tools/load/README.md):
#   - podman pod `backend-pagination-pg` running with pg_stat_statements loaded
#   - podman pod `backend-pagination-app` running, /actuator/health UP
#   - Postgres seeded with the 1M-row dataset (SPRING_PROFILES_ACTIVE=local on first boot)
#
# Outputs land in tools/load/out/:
#   k6-summary.json         k6 end-of-run summary (p50/p95/p99/RPS/error rate)
#   k6-stdout.txt           full k6 run log
#   podman-stats.jsonl      one JSON line per sample, every 1s, for both containers
#   podman-stats-before.txt one-shot snapshot before the run
#   podman-stats-after.txt  one-shot snapshot after the run
#   pg-stat.txt             pg_stat_statements top queries by total_exec_time
#   pg-bgwriter.txt         buffer cache + bgwriter counters before/after
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${HERE}/out"
mkdir -p "${OUT}"

PG_CTR="backend-pagination-pg-postgres"
APP_CTR="backend-pagination-app-backend"
K6_IMAGE="docker.io/grafana/k6:latest"
BASE_URL="${BASE_URL:-http://localhost:8080}"

psql_pg() {
  podman exec -i "${PG_CTR}" psql -U postgres -d books -At "$@"
}

require_up() {
  podman container exists "$1" || { echo "container $1 not running" >&2; exit 1; }
}
require_up "${PG_CTR}"
require_up "${APP_CTR}"

curl -fsS "${BASE_URL}/actuator/health" >/dev/null || { echo "backend not healthy at ${BASE_URL}" >&2; exit 1; }

echo "[1/6] reset pg_stat_statements + capture baseline counters"
# pg_stat_statements is preloaded as a shared library but the SQL extension
# object is per-database; create it once on a fresh volume.
psql_pg -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;" >/dev/null
psql_pg -c "SELECT pg_stat_statements_reset();" >/dev/null
{
  echo "=== bgwriter / io stats (before) ==="
  psql_pg -c "SELECT * FROM pg_stat_bgwriter;"
  echo "=== database stats (before) ==="
  psql_pg -c "SELECT datname, blks_read, blks_hit, tup_returned, tup_fetched FROM pg_stat_database WHERE datname='books';"
} > "${OUT}/pg-bgwriter.txt"

echo "[2/6] podman stats snapshot (before)"
podman stats --no-stream --format "table {{.Name}} {{.CPUPerc}} {{.MemUsage}} {{.MemPerc}} {{.NetIO}} {{.BlockIO}}" \
  "${PG_CTR}" "${APP_CTR}" > "${OUT}/podman-stats-before.txt"

echo "[3/6] start podman stats sampler in background (1s interval)"
: > "${OUT}/podman-stats.jsonl"
# setsid puts the sampler in its own process group so we can kill the whole tree
# (otherwise `podman stats` inherits and keeps running after we kill the wrapper).
setsid bash -c '
  podman stats --format json --interval 1 "$1" "$2" \
    | while IFS= read -r line; do printf "%s %s\n" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$line"; done \
    >> "$3"
' _ "${PG_CTR}" "${APP_CTR}" "${OUT}/podman-stats.jsonl" &
SAMPLER_PGID=$!
trap 'kill -- -${SAMPLER_PGID} 2>/dev/null || true' EXIT

echo "[4/6] pull k6 image (idempotent) + run scenario"
podman image exists "${K6_IMAGE}" || podman pull "${K6_IMAGE}"
# --network=host so the container can reach localhost:8080 (backend pod uses hostNetwork too).
podman run --rm \
  --network=host \
  -v "${HERE}:/scripts:Z" \
  -e BASE_URL="${BASE_URL}" \
  "${K6_IMAGE}" run /scripts/scenario.js \
  | tee "${OUT}/k6-stdout.txt"

echo "[5/6] stop sampler + podman stats snapshot (after)"
kill -- -${SAMPLER_PGID} 2>/dev/null || true
wait ${SAMPLER_PGID} 2>/dev/null || true
trap - EXIT
podman stats --no-stream --format "table {{.Name}} {{.CPUPerc}} {{.MemUsage}} {{.MemPerc}} {{.NetIO}} {{.BlockIO}}" \
  "${PG_CTR}" "${APP_CTR}" > "${OUT}/podman-stats-after.txt"

echo "[6/6] capture pg_stat_statements top queries"
{
  echo "=== bgwriter / io stats (after) ==="
  psql_pg -c "SELECT * FROM pg_stat_bgwriter;"
  echo "=== database stats (after) ==="
  psql_pg -c "SELECT datname, blks_read, blks_hit, tup_returned, tup_fetched FROM pg_stat_database WHERE datname='books';"
} >> "${OUT}/pg-bgwriter.txt"

psql_pg -c "
  SELECT
    calls,
    round(total_exec_time::numeric, 2)  AS total_ms,
    round(mean_exec_time::numeric, 3)   AS mean_ms,
    round(stddev_exec_time::numeric, 3) AS stddev_ms,
    round(min_exec_time::numeric, 3)    AS min_ms,
    round(max_exec_time::numeric, 3)    AS max_ms,
    rows,
    shared_blks_hit,
    shared_blks_read,
    round(blk_read_time::numeric, 3)    AS blk_read_ms,
    left(query, 160)                    AS query
  FROM pg_stat_statements
  WHERE query ILIKE '%book%' OR query ILIKE '%select%'
  ORDER BY total_exec_time DESC
  LIMIT 20;
" > "${OUT}/pg-stat.txt"

echo
echo "Done. Artifacts in ${OUT}/:"
ls -la "${OUT}/"
