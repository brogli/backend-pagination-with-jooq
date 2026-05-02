# Pagination wire protocol

Keyset pagination over `/api/books`. Cursor position is one opaque URL
query param; the response carries adjacent cursors in the body. Cursor
absence at an edge signals end-of-data — no separate boolean flags.

## Request

```
GET /api/books?sort=title&dir=asc&size=25                  # first page
GET /api/books?sort=title&dir=asc&size=25&cursor=...       # subsequent
```

Pass `nextCursor` / `prevCursor` from the prior response back verbatim —
clients must not parse, decode, or modify the string. The cursor binds
to the `sort` and `dir` it was issued under: a mismatch, malformed
payload, or unknown version returns 400.

## Response

```json
{
  "content": [ /* page rows */ ],
  "nextCursor": "...",
  "prevCursor": "..."
}
```

Either cursor is omitted (or null) at the corresponding edge. Detection
is via a `limit + 1` peek, so a page that exactly fills the tail
correctly omits `nextCursor` — no trailing-empty-page round trip.

## Cursor internals

The cursor identifies one row by `(sort_column_value, id)`; the `id`
tiebreaker is required because the sort column alone isn't unique. The
wire payload also carries a version, the sort field, direction, and a
navigation hint. JSON-encoded then base64url without padding:

```
{ "v": 1, "sort": "title", "direction": "asc", "navigation": "NEXT", "value": "...", "id": 42 }
```

`Cursor` is a sealed interface with one variant per column type family
(`StringCursor`, `DecimalCursor`, `DateCursor`). Each carries a
statically-typed `value`; Jackson coercion is configured to fail rather
than silently convert (a numeric value for a string-sort cursor is
rejected at decode, not coerced).

Both directions share `BookRepository.fetchPage` — `Navigation.PREV`
reverses `ORDER BY` internally and re-reverses the trimmed result back
to forward order before returning.

## See also

- OpenAPI spec: `backend/src/main/resources/openapi/openapi.yaml`
- Implementation: `BookController`, `BookService`, `BookRepository`, `Cursor`
- Sample requests: `tools/books.http`
