package ch.brogli.backendpagination.persistence.repository;

import static ch.brogli.backendpagination.jooq.Tables.BOOK;

import ch.brogli.backendpagination.api.model.BookDto;
import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.Genre;
import ch.brogli.backendpagination.api.model.Language;
import ch.brogli.backendpagination.api.model.SortField;
import ch.brogli.backendpagination.jooq.tables.records.BookRecord;
import ch.brogli.backendpagination.service.cursor.Navigation;
import ch.brogli.backendpagination.service.cursor.SortValue;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectSeekStepN;
import org.jooq.TableField;
import org.springframework.stereotype.Repository;

@Repository
public class BookRepository {
    private final DSLContext dsl;

    public BookRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public record Anchor(SortValue value, long id) {}

    public record PageResult(List<BookDto> rows, boolean hasMore) {}

    /**
     * Single seek in the direction implied by {@code navigation}. {@link Navigation#PREV} reverses
     * ORDER BY internally and re-reverses the trimmed result to forward order before returning.
     */
    public PageResult fetchPage(
            SortField sort,
            Direction direction,
            Navigation navigation,
            int size,
            Optional<Anchor> anchor,
            Condition where) {
        TableField<BookRecord, ?> sortField = fieldFor(sort);
        boolean sortAsc = direction == Direction.ASC;
        boolean orderAsc = sortAsc == (navigation == Navigation.NEXT);

        SelectSeekStepN<BookRecord> step =
                dsl.selectFrom(BOOK)
                        .where(where)
                        .orderBy(
                                orderAsc ? sortField.asc() : sortField.desc(),
                                orderAsc ? BOOK.ID.asc() : BOOK.ID.desc());

        var limited =
                anchor.map(a -> step.seek(a.value().unwrap(), a.id()).limit(size + 1))
                        .orElseGet(() -> step.limit(size + 1));
        List<BookDto> fetched = limited.fetch().map(BookRepository::toDto);

        boolean hasMore = fetched.size() > size;
        List<BookDto> trimmed = hasMore ? fetched.subList(0, size) : fetched;
        List<BookDto> ordered = navigation == Navigation.PREV ? trimmed.reversed() : trimmed;
        return new PageResult(ordered, hasMore);
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
