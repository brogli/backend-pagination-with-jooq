# Pagination design

Keyset pagination on `/api/books`. The API contract lives in
`backend/src/main/resources/openapi/openapi.yaml`. This page is about the
design.

## Request and response

A request carries a sort field, a direction, a size, an optional cursor, and
zero or more filters. The response is `{ items, prevCursor, nextCursor }`.
Each cursor is either a string the client passes back as-is on the next
request, or null when there's nothing more in that direction. One page costs
one query. No count, no extra round trip.

## Why keyset, not offset

Offset pagination (`LIMIT n OFFSET k`) gets expensive when `k` is large. The
database still has to fetch and order the skipped rows before it can return
the rest. Markus Winand's
[use-the-index-luke.com/no-offset](https://use-the-index-luke.com/no-offset)
covers it in detail.

Keyset pagination tracks *where you are* instead of *how far you scrolled*.
Each request supplies the sort value and id of the row at the edge of the
previous page, and the database seeks straight there with
`WHERE (sort_col, id) > (anchor_col, anchor_id)`. With a composite index on
`(sort_col, id)` this stays cheap no matter how deep the page is.
`IndexUsageIT` pins the EXPLAIN plans for every sort/filter combination the
API supports.

The `id` is in the anchor as a tiebreaker. Sort columns aren't unique. Two
books can share a title, a price, or a publication date, so the anchor needs
both pieces to point at one specific row.

## The seek in jOOQ

The same query shape runs for every page. jOOQ's `.seek()` produces a row
tuple comparison against the `ORDER BY` columns:

```java
var step = dsl.selectFrom(BOOK)
        .where(where)
        .orderBy(sortField.asc(), BOOK.ID.asc());

// First page (no anchor).
step.limit(size + 1);

// Subsequent page (seek past the anchor).
step.seek(anchor.value().unwrap(), anchor.id()).limit(size + 1);
```

`.seek(v, id)` becomes `WHERE (sort_col, id) > (?, ?)` in SQL. That row tuple
form is what lets Postgres use the composite `(sort_col, id)` index as an
Index Only Scan. The strict `>` (not `>=`) excludes the anchor row from the
next page, since the client already has it.

The first page has no anchor and so skips `.seek()` entirely. Every page
after that gets its anchor from the previous response, carried in a cursor.

## The cursor

A cursor is a base64url-encoded (no padding) JSON envelope:

```json
{ "v": 1, "sort": "title", "direction": "asc", "navigation": "NEXT", "value": "Dune", "id": 42 }
```

- `value` and `id` are the anchor that feeds the next seek.
- `sort` and `direction` are bound to the query the cursor was issued under.
  Reusing a cursor with a different sort or direction returns 400.
- `navigation` says whether the next seek runs forward (`NEXT`) or backward
  (`PREV`). See [Bidirectional navigation](#bidirectional-navigation).
- `v` is a format version, so the encoding can change later without breaking
  cursors that are still in flight.

The cursor is opaque. Clients pass it back as-is and don't parse or modify it.

The decoder is typed by sort field. A `value` of the wrong shape is rejected
at decode rather than ever reaching the seek.

## Edge detection

Each query fetches `size + 1` rows. If the extra row shows up, there's more
data in that direction and the matching cursor is filled in. If not, the
cursor is null. No COUNT, no empty trailing page.

On a NEXT page, `prevCursor` is null only on the very first page (no incoming
cursor was sent), and `nextCursor` is null once the extra row stops arriving.

On a PREV page it's the mirror image. `nextCursor` is always set (the user
came from somewhere) and `prevCursor` is null once they reach the start.

## Bidirectional navigation

Going backward is the same operation as going forward, with the order flipped.
The cursor's `navigation` field marks which end of the current page the next
anchor is and decides whether the ordering flips.

- **NEXT cursor**: the *last* row of the current page is the anchor, and the
  seek runs forward in the user-requested sort direction.
- **PREV cursor**: the *first* row of the current page is the anchor. The
  ordering is reversed so the seek is still a strict-greater-than under the
  new direction, and the page is reversed back before returning so the result
  still reads forward.

## Filters and cursor validity

The cursor binds sort and direction (a mismatch returns 400), but it doesn't
bind the filter parameters (`genre`, `language`, `inStock`, `minRating`,
`priceMin`, `priceMax`, `publishedAfter`). Filters are kept off the cursor
for simplicity. The client is expected to drop the cursor whenever filters
change, and the frontend does this.

## See also

- OpenAPI spec: `backend/src/main/resources/openapi/openapi.yaml`
- Sample requests: `tools/books.http`
