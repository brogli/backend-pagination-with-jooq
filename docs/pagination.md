# Pagination wire protocol

Keyset (a.k.a. cursor / seek) pagination over `/api/books`. Both
request and response carry cursor position; the request as URL query
params, the response as fields in the JSON body.

## Request

The client always sends cursor position as URL query params:

```
GET /api/books?sort=title&dir=asc&size=25
                                            ← first page: no cursor
GET /api/books?sort=title&dir=asc&size=25
              &cursorValue=A%20Bridge%20%23106326&cursorId=106326
                                            ← any subsequent page
```

`cursorValue` and `cursorId` are required-together: either both present
or both absent. A half-set pair returns 400. Validation lives in
`SearchBooksQuery.Cursor.fromOptional`, so the rest of the pipeline
never sees a partial cursor.

`cursorValue` is parsed according to the request's `sort` field —
`String` for title/author, `BigDecimal` for price/rating, `LocalDate`
for publishedAt. A parse failure returns 400.

## Response

```json
{
  "content": [ /* page rows */ ],
  "next": { "value": "A Bridge #113955", "id": 113955 },
  "prev": { "value": "A Bridge #100307", "id": 100307 }
}
```

Either `next` or `prev` is `null` when that direction has no further
page (`next` on the last page, `prev` on the first page).

The client reads `body.next.value` / `body.next.id`, builds the next
request as `?cursorValue=<v>&cursorId=<i>`, and re-fetches.

We don't emit an RFC 8288 `Link` header. The single consumer is the
SPA front-end, which uses `body.next` / `body.prev` directly; a
duplicate Link encoding would just be dead weight (YAGNI). Reconsider
if a third-party consumer or generic pagination middleware ever shows
up.

## Cursor mechanics (briefly)

The cursor identifies one row in the sort order: `(sort_column_value,
id)`. The `id` tiebreaker is required — the sort column alone isn't
unique (multiple rows can share a title, price, etc.).

`prev` is implemented via the same forward-seek primitive as `next`.
The server back-computes the cursor that, when sent forward, lands on
the previous page. See `BookRepository.findPrevSeed` for the
`limit = size + 1` reverse-seek detail.

## See also

- OpenAPI spec: `backend/src/main/resources/openapi/openapi.yaml`
- Implementation: `BookController`, `BookService`, `BookRepository`
- Sample requests: `tools/books.http`
