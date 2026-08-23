# Pagination design

Keyset pagination on `/api/books`. The API contract lives in
`backend/src/main/resources/openapi/openapi.yaml`. This page is about the design.

## Request and response

A request carries a sort field, a direction, a size, an optional cursor, and zero or more filters.
The response is `{ content, prevCursor, nextCursor }`. Each cursor is either a string the client
passes back as-is on the next request, or null when there's nothing more in that direction. One page
costs one query. No count, no extra round trip.

## Why keyset, not offset

Offset pagination (`LIMIT n OFFSET k`) gets expensive when `k` is large. The database still has to
fetch and order the skipped rows before it can return the rest. Markus Winand's
[use-the-index-luke.com/no-offset](https://use-the-index-luke.com/no-offset) covers it in detail.

Keyset pagination tracks _where you are_ instead of _how far you scrolled_. Each request supplies
the sort value and id of the row at the edge of the previous page, and the database seeks straight
there with `WHERE (sort_col, id) > (anchor_col, anchor_id)`. With a composite index on
`(sort_col, id)` this stays cheap no matter how deep the page is. `IndexUsageIT` pins the EXPLAIN
plans for every sort/filter combination the API supports.

The `id` is in the anchor as a tiebreaker. Sort columns aren't unique. Two books can share a title,
a price, or a publication date, so the anchor needs both pieces to point at one specific row.

## The seek

The same query shape runs for every page:

```sql
SELECT ... FROM book
WHERE <filters>
  AND (sort_col, id) > (:anchor_value, :anchor_id)   -- omitted on the first page
ORDER BY sort_col, id
LIMIT :size + 1
```

The row tuple comparison is what lets Postgres use the composite `(sort_col, id)` index as an Index
Only Scan. The strict `>` (not `>=`) excludes the anchor row from the next page, since the client
already has it. For descending sorts and for backward navigation the comparison and the ordering
flip together.

The first page has no anchor and so skips the comparison entirely. Every page after that gets its
anchor from the previous response, carried in a cursor.

## The cursor

A cursor is a base64url-encoded (no padding) JSON envelope:

```json
{
  "v": 2,
  "sort": "title",
  "direction": "asc",
  "navigation": "NEXT",
  "filters": "Qm9va3MhISE",
  "value": "Dune",
  "id": 42
}
```

- `value` and `id` are the anchor that feeds the next seek.
- `sort` and `direction` are bound to the query the cursor was issued under. Reusing a cursor with a
  different sort or direction returns 400.
- `filters` is a short fingerprint of the filter parameters the cursor was issued under. Reusing a
  cursor with a different filter set returns 400. See
  [Filters and cursor validity](#filters-and-cursor-validity).
- `navigation` says whether the next seek runs forward (`NEXT`) or backward (`PREV`). See
  [Bidirectional navigation](#bidirectional-navigation).
- `v` is a format version. It is checked before anything else in the envelope, so a cursor from a
  newer or older format is reported as unsupported rather than malformed.

The cursor is opaque. Clients pass it back as-is and don't parse or modify it.

The type of `value` follows the sort field: a string for title and author, a decimal for price and
rating, an ISO date for the publication date. A value of the wrong shape is rejected when the cursor
is decoded, before it can reach the database.

## Edge detection

Each query fetches `size + 1` rows. If the extra row shows up, there's more data in that direction
and the matching cursor is filled in. If not, the cursor is null. No COUNT, no empty trailing page.

On a NEXT page, `prevCursor` is null only on the very first page (no incoming cursor was sent), and
`nextCursor` is null once the extra row stops arriving.

On a PREV page it's the mirror image. `nextCursor` is always set (the user came from somewhere) and
`prevCursor` is null once they reach the start.

## Bidirectional navigation

Going backward is the same operation as going forward, with the order flipped. The cursor's
`navigation` field marks which end of the current page the next anchor is and decides whether the
ordering flips.

- **NEXT cursor**: the _last_ row of the current page is the anchor, and the seek runs forward in
  the user-requested sort direction.
- **PREV cursor**: the _first_ row of the current page is the anchor. The ordering is reversed so
  the seek is still a strict-greater-than under the new direction, and the page is reversed back
  before returning so the result still reads forward.

## Filters and cursor validity

An anchor only makes sense inside the result set it was taken from. A cursor issued under
`genre=Fantasy` points at a row that may not exist in the `genre=SciFi` result set, and seeking from
it would start the page at an arbitrary position.

The cursor therefore carries a fingerprint of the filter parameters: a short hash of their canonical
values (genres de-duplicated and sorted, numbers normalised). The server recomputes the fingerprint
from the incoming request and rejects the cursor with 400 if it differs. The filter values
themselves stay out of the cursor, which keeps it short and avoids duplicating state that already
lives in the query string.

The frontend drops the cursor whenever a filter, sort or page size changes, so the 400 only fires for
hand-edited or stale URLs.

## Stale cursors

A bookmarked cursor can go stale: the rows around the anchor were deleted, or the cursor format was
versioned. The server answers with 400 (rejected cursor) or an empty page (anchor past the end of
the data in that direction). In both cases the client drops the cursor and reloads the first page
with the same sort and filters. The retry carries no cursor, so it cannot loop.

## See also

- OpenAPI spec: `backend/src/main/resources/openapi/openapi.yaml`
- Sample requests: `tools/books.http`
