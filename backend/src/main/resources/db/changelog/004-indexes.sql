--liquibase formatted sql

-- Runs after both seed variants (002 seed-large, 003 seed-medium). Bulk insert
-- into an unindexed table is dramatically faster — Postgres uses parallel
-- maintenance workers to build each index once against the populated table.
-- The two seed contexts are mutually exclusive, so at most one of them
-- contributes rows on any given Liquibase run.

--changeset phase2:004-indexes runInTransaction:false
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
