package ch.brogli.backendpagination.persistence.repository;

import static ch.brogli.backendpagination.jooq.Tables.BOOK;

import ch.brogli.backendpagination.api.model.BookDto;
import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.Genre;
import ch.brogli.backendpagination.api.model.Language;
import ch.brogli.backendpagination.api.model.SortField;
import ch.brogli.backendpagination.jooq.tables.records.BookRecord;
import ch.brogli.backendpagination.service.SearchBooksQuery.Filters;
import ch.brogli.backendpagination.service.cursor.Cursor;
import ch.brogli.backendpagination.service.cursor.Navigation;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectSeekStepN;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class BookRepository {
    private final DSLContext dsl;

    public BookRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public record PageResult(List<BookDto> rows, boolean hasMore) {}

    /**
     * Single seek in the direction implied by {@code cursor}'s navigation ({@link Navigation#NEXT}
     * when {@code cursor} is null). {@link Navigation#PREV} reverses ORDER BY internally and
     * re-reverses the trimmed result to forward order before returning. Fetches {@code size + 1}
     * rows so {@code hasMore} needs no COUNT.
     */
    public PageResult fetchPage(
            SortField sort,
            Direction direction,
            int size,
            @Nullable Cursor cursor,
            Filters filters) {
        Navigation navigation = cursor == null ? Navigation.NEXT : cursor.navigation();
        TableField<BookRecord, ?> sortField = fieldFor(sort);
        boolean sortAsc = direction == Direction.ASC;
        boolean orderAsc = sortAsc == (navigation == Navigation.NEXT);

        SelectSeekStepN<BookRecord> step =
                dsl.selectFrom(BOOK)
                        .where(conditionsFor(filters))
                        .orderBy(
                                orderAsc ? sortField.asc() : sortField.desc(),
                                orderAsc ? BOOK.ID.asc() : BOOK.ID.desc());

        List<BookDto> fetched;
        if (cursor == null) {
            fetched = step.limit(size + 1).fetch().map(BookRepository::toDto);
        } else {
            fetched =
                    step.seek(cursor.value(), cursor.id())
                            .limit(size + 1)
                            .fetch()
                            .map(BookRepository::toDto);
        }

        boolean hasMore = fetched.size() > size;
        List<BookDto> trimmed = hasMore ? fetched.subList(0, size) : fetched;
        List<BookDto> ordered = navigation == Navigation.PREV ? trimmed.reversed() : trimmed;
        return new PageResult(ordered, hasMore);
    }

    private static Condition conditionsFor(Filters filters) {
        Condition where = DSL.noCondition();
        if (filters.genre() != null && !filters.genre().isEmpty()) {
            where =
                    where.and(
                            BOOK.GENRE.in(filters.genre().stream().map(Genre::getValue).toList()));
        }
        if (filters.language() != null) {
            where = where.and(BOOK.LANGUAGE.eq(filters.language().getValue()));
        }
        if (filters.inStock() != null) {
            where = where.and(BOOK.IN_STOCK.eq(filters.inStock()));
        }
        if (filters.minRating() != null) {
            where = where.and(BOOK.RATING.ge(filters.minRating()));
        }
        if (filters.priceMin() != null) {
            where = where.and(BOOK.PRICE.ge(filters.priceMin()));
        }
        if (filters.priceMax() != null) {
            where = where.and(BOOK.PRICE.le(filters.priceMax()));
        }
        if (filters.publishedAfter() != null) {
            where = where.and(BOOK.PUBLISHED_AT.ge(filters.publishedAfter()));
        }
        return where;
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
