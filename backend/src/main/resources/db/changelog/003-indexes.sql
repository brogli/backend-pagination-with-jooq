--liquibase formatted sql

-- Created after the seed (002) so that on a fresh dev DB started with the
-- local profile, the bulk insert hits an unindexed table (no per-row B-tree
-- maintenance) and indexes are built once against the populated table —
-- Postgres uses parallel maintenance workers for the per-index sort.
-- Without the local profile, this changeset still runs (untagged) but
-- against an empty table, so it's instant.

--changeset phase2:003-indexes runInTransaction:false
SET maintenance_work_mem = '256MB';
SET max_parallel_maintenance_workers = 4;

CREATE INDEX book_title_id_idx                 ON book (title, id);
CREATE INDEX book_author_id_idx                ON book (author, id);
CREATE INDEX book_price_id_idx                 ON book (price, id);
CREATE INDEX book_rating_id_idx                ON book (rating, id);
CREATE INDEX book_published_at_id_idx          ON book (published_at, id);
CREATE INDEX book_genre_price_id_idx           ON book (genre, price, id);
CREATE INDEX book_in_stock_rating_id_idx       ON book (in_stock, rating, id);
CREATE INDEX book_language_published_at_id_idx ON book (language, published_at, id);
--rollback DROP INDEX book_title_id_idx, book_author_id_idx, book_price_id_idx, book_rating_id_idx, book_published_at_id_idx, book_genre_price_id_idx, book_in_stock_rating_id_idx, book_language_published_at_id_idx;
