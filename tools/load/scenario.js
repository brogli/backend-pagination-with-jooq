import http from 'k6/http'
import { check, sleep, fail } from 'k6'
import { Trend, Counter } from 'k6/metrics'

// 100 VUs, 60s. Each VU loops the realistic walk pattern below.
export const options = {
  scenarios: {
    walk: {
      executor: 'constant-vus',
      vus: 100,
      duration: '60s',
      gracefulStop: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<100', 'p(99)<200'],
  },
  summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
}

const BASE = __ENV.BASE_URL || 'http://localhost:8080'
const SIZE = 25
const THINK_MIN_MS = 80
const THINK_MAX_MS = 220

// Per-route latency trends so we can see if first-page is faster/slower than cursor-paged.
const tFirst = new Trend('rt_first_page', true)
const tNext = new Trend('rt_next_page', true)
const tPrev = new Trend('rt_prev_page', true)
const cFilterSwitch = new Counter('filter_switches')
const cWalkLoops = new Counter('walk_loops')

// Filter profiles scattered across the index matrix in CLAUDE.md.
// Each entry is a query-string fragment minus cursor/size.
const PROFILES = [
  'sort=title&dir=asc',                                            // book_title_id_idx
  'sort=author&dir=asc',                                           // book_author_id_idx
  'sort=price&dir=desc',                                           // book_price_id_idx
  'sort=rating&dir=desc',                                          // book_rating_id_idx
  'sort=publishedAt&dir=desc',                                     // book_published_at_id_idx
  'sort=price&dir=asc&genre=Fantasy',                              // book_genre_price_id_idx (single genre)
  'sort=price&dir=asc&genre=Fantasy&genre=SciFi',                  // multi-genre IN — falls back to price index
  'sort=rating&dir=desc&inStock=true',                             // book_in_stock_rating_id_idx
  'sort=publishedAt&dir=desc&language=English',                    // book_language_published_at_id_idx
  'sort=price&dir=asc&genre=Mystery&inStock=true',                 // book_genre_price_id_idx + filter
  'sort=price&dir=asc&minRating=4.0',                              // book_price_id_idx + post-filter
  'sort=publishedAt&dir=desc&priceMin=15',                         // book_published_at_id_idx + post-filter
  'sort=title&dir=asc&publishedAfter=2020-01-01',                  // book_title_id_idx + post-filter
  'sort=title&dir=asc&language=German',                            // book_title_id_idx (no composite for language+title)
]

function think() {
  sleep((THINK_MIN_MS + Math.random() * (THINK_MAX_MS - THINK_MIN_MS)) / 1000)
}

function pickProfile(prev) {
  // Pick a different profile than the previous one to actually exercise filter switching.
  let next
  do {
    next = PROFILES[Math.floor(Math.random() * PROFILES.length)]
  } while (PROFILES.length > 1 && next === prev)
  return next
}

function get(url, trend, label) {
  const res = http.get(url, { tags: { name: label } })
  trend.add(res.timings.duration)
  const ok = check(res, {
    [`${label} status 200`]: (r) => r.status === 200,
    [`${label} body parses`]: (r) => {
      try {
        const j = r.json()
        return j && Array.isArray(j.content)
      } catch (_) {
        return false
      }
    },
  })
  if (!ok) {
    fail(`${label} failed: status=${res.status} body=${res.body && res.body.slice(0, 200)}`)
  }
  return res.json()
}

export default function () {
  let profile = pickProfile(null)

  // Realistic walk: forward 10, change filter, forward 10, back 5, change filter, forward 10, ...
  // Loops until the 60s scenario duration kills the VU.
  while (true) {
    // Fresh entry into a filter — fetch first page.
    let body = get(`${BASE}/api/books?${profile}&size=${SIZE}`, tFirst, 'first')
    think()

    // Forward 10.
    let history = []
    for (let i = 0; i < 10 && body.nextCursor; i++) {
      history.push(body.prevCursor)
      body = get(`${BASE}/api/books?${profile}&size=${SIZE}&cursor=${encodeURIComponent(body.nextCursor)}`, tNext, 'next')
      think()
    }

    // Change filter, fresh first page.
    profile = pickProfile(profile)
    cFilterSwitch.add(1)
    body = get(`${BASE}/api/books?${profile}&size=${SIZE}`, tFirst, 'first')
    think()

    // Forward 10 in the new filter.
    history = []
    for (let i = 0; i < 10 && body.nextCursor; i++) {
      history.push(body.prevCursor)
      body = get(`${BASE}/api/books?${profile}&size=${SIZE}&cursor=${encodeURIComponent(body.nextCursor)}`, tNext, 'next')
      think()
    }

    // Back 5 — use prevCursor chain.
    for (let i = 0; i < 5 && body.prevCursor; i++) {
      body = get(`${BASE}/api/books?${profile}&size=${SIZE}&cursor=${encodeURIComponent(body.prevCursor)}`, tPrev, 'prev')
      think()
    }

    cWalkLoops.add(1)
  }
}
