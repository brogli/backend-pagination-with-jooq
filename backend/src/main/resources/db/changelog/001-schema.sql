--liquibase formatted sql

--changeset phase2:001-create-book
CREATE TABLE book (
    id           bigserial      PRIMARY KEY,
    title        text           NOT NULL,
    author       text           NOT NULL,
    genre        text           NOT NULL,
    language     text           NOT NULL,
    in_stock     boolean        NOT NULL,
    rating       numeric(2,1)   NOT NULL DEFAULT 0.0,
    price        numeric(8,2)   NOT NULL,
    published_at date           NOT NULL,
    created_at   timestamptz    NOT NULL DEFAULT now()
);
--rollback DROP TABLE book;
