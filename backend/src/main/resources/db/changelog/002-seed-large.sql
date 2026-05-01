--liquibase formatted sql

-- Opt-in via context=seed-large; app runtime and codegen both pass !seed-large.
-- runInTransaction:false keeps WAL pressure down during the bulk insert.

--changeset phase2:002-seed-large context:seed-large runInTransaction:false
SELECT setseed(0.42);

INSERT INTO book (title, author, genre, language, in_stock, rating, price, published_at)
SELECT
    adjectives[1 + (random() * (array_length(adjectives, 1) - 1))::int]
        || ' ' ||
    nouns[1 + (random() * (array_length(nouns, 1) - 1))::int]
        || ' #' || i                                                  AS title,
    authors[1 + (random() * (array_length(authors, 1) - 1))::int]      AS author,
    genres[1 + (random() * (array_length(genres, 1) - 1))::int]        AS genre,
    languages[1 + (random() * (array_length(languages, 1) - 1))::int]  AS language,
    random() < 0.7                                                     AS in_stock,
    ROUND((random() * 5)::numeric, 1)                                  AS rating,
    ROUND(EXP(random() * LN(200))::numeric, 2)                         AS price,
    CURRENT_DATE - (random() * 365 * 30)::int                          AS published_at
FROM generate_series(1, 1000000) i,
     (SELECT
        ARRAY['Alice Adams','Bob Brown','Carol Chen','David Davis','Eve Evans',
              'Frank Fischer','Grace Garcia','Henry Hill','Iris Ito','Jack Jones',
              'Karen King','Liam Lee','Maya Martinez','Noah Nguyen','Olivia Orozco',
              'Peter Park','Quinn Quintero','Ruby Rao','Sam Smith','Tina Tan',
              'Uma Underwood','Victor Vega','Wendy Wong','Xavier Xu','Yara Young',
              'Zoe Zhang','Aaron Allen','Beth Baker','Chris Cooper','Diana Diaz',
              'Ethan Edwards','Fiona Foster','George Green','Hannah Hayes','Ian Ibrahim',
              'Julia James','Kevin Kim','Lila Lopez','Mark Murphy','Nora Nelson',
              'Owen Oliver','Paula Patel','Quentin Quan','Rachel Reed','Steve Sato',
              'Tara Torres','Uri Usman','Vera Volkov','William Wright','Xena Xie']    AS authors,
        ARRAY['Fantasy','SciFi','Mystery','Romance','Thriller','NonFiction']          AS genres,
        ARRAY['English','German','French','Spanish','Japanese']                       AS languages,
        ARRAY['The','A','Lost','Hidden','Silent','Last','First','Forgotten',
              'Eternal','Rising','Falling','Burning','Frozen','Ancient','Modern']     AS adjectives,
        ARRAY['Empire','Garden','Mountain','River','Forest','Desert','Ocean','Castle',
              'Temple','Bridge','Tower','Star','Moon','Sun','Storm','Sky','Heart',
              'Soul','Crown','Sword','Dream','Promise','Echo','Shadow','Whisper']     AS nouns
     ) seeds;

ANALYZE book;
--rollback TRUNCATE TABLE book;
