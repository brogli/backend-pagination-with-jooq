package ch.brogli.backendpagination.repository;

import static ch.brogli.backendpagination.jooq.Tables.BOOK;

import ch.brogli.backendpagination.api.model.BookDto;
import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.Genre;
import ch.brogli.backendpagination.api.model.Language;
import ch.brogli.backendpagination.api.model.SeekKey;
import ch.brogli.backendpagination.api.model.SortField;
import ch.brogli.backendpagination.jooq.tables.records.BookRecord;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectSeekStepN;
import org.jooq.TableField;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class BookRepository {
    private final DSLContext dsl;

    public BookRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Forward-direction page fetch. Tiebreaker direction mirrors primary so a (sort_col, id)
     * composite index can serve via plain forward index scan. Pass {@code seekKey == null} for the
     * first page.
     */
    public List<BookDto> fetchPage(
            SortField sort, Direction dir, int size, @Nullable SeekKey seekKey, Condition where) {
        TableField<BookRecord, ?> sortField = fieldFor(sort);
        boolean asc = dir == Direction.ASC;

        SelectSeekStepN<BookRecord> step =
                dsl.selectFrom(BOOK)
                        .where(where)
                        .orderBy(
                                asc ? sortField.asc() : sortField.desc(),
                                asc ? BOOK.ID.asc() : BOOK.ID.desc());

        var limited =
                (seekKey == null)
                        ? step.limit(size)
                        : step.seek(seekKey.getValue(), seekKey.getId()).limit(size);
        return limited.fetch().map(BookRepository::toDto);
    }

    /**
     * Reverse-direction lookup that finds the cursor seed for the page <em>preceding</em> the
     * current page. Encoded forward-cursor semantics require the prevCursor to point at the last
     * row of the page-before-prev (so a later forward seek lands on the prev page's first row).
     * That row sits {@code size + 1} rows behind the current page's first row, so we fetch {@code
     * size + 1} reversed rows and take the last. Returns null if the prev page would be the first
     * page (cursor is unconditionally null there).
     */
    public @Nullable SeekKey findPrevSeed(
            SortField sort, Direction dir, int size, SeekKey currentFirstKey, Condition where) {
        TableField<BookRecord, ?> sortField = fieldFor(sort);
        boolean asc = dir == Direction.ASC;

        var rs =
                dsl.select(sortField, BOOK.ID)
                        .from(BOOK)
                        .where(where)
                        .orderBy(
                                asc ? sortField.desc() : sortField.asc(),
                                asc ? BOOK.ID.desc() : BOOK.ID.asc())
                        .seek(currentFirstKey.getValue(), currentFirstKey.getId())
                        .limit(size + 1)
                        .fetch();

        if (rs.size() < size + 1) {
            return null;
        }
        var last = rs.getLast();
        return new SeekKey(last.value1(), last.value2());
    }

    private static TableField<BookRecord, ?> fieldFor(SortField sort) {
        return switch (sort) {
            case TITLE -> BOOK.TITLE;
            case AUTHOR -> BOOK.AUTHOR;
            case PRICE -> BOOK.PRICE;
            case RATING -> BOOK.RATING;
            case PUBLISHED_AT -> BOOK.PUBLISHED_AT;
        };
    }

    private static BookDto toDto(Record r) {
        return new BookDto(
                r.get(BOOK.ID),
                r.get(BOOK.TITLE),
                r.get(BOOK.AUTHOR),
                Genre.fromValue(r.get(BOOK.GENRE)),
                Language.fromValue(r.get(BOOK.LANGUAGE)),
                r.get(BOOK.IN_STOCK),
                r.get(BOOK.RATING),
                r.get(BOOK.PRICE),
                r.get(BOOK.PUBLISHED_AT));
    }
}
