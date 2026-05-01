package ch.brogli.backendpagination.service;

import static ch.brogli.backendpagination.jooq.Tables.BOOK;

import ch.brogli.backendpagination.api.model.BookDto;
import ch.brogli.backendpagination.api.model.BookFilters;
import ch.brogli.backendpagination.api.model.BookPage;
import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.Genre;
import ch.brogli.backendpagination.api.model.SeekKey;
import ch.brogli.backendpagination.api.model.SortField;
import ch.brogli.backendpagination.exception.BadRequestException;
import ch.brogli.backendpagination.repository.BookRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class BookService {
    private final BookRepository repo;

    public BookService(BookRepository repo) {
        this.repo = repo;
    }

    public BookPage search(SearchBooksQuery query) {
        SortField sort = query.paging().sort();
        Direction dir = query.paging().dir();
        int size = query.paging().size();

        SeekKey seekKey = parseSeekKey(sort, query.cursor());
        Condition where = buildConditions(query.filters());

        List<BookDto> rows = repo.fetchPage(sort, dir, size, seekKey, where);

        SeekKey next;
        if (rows.size() < size) {
            next = null;
        } else {
            BookDto last = rows.getLast();
            next = new SeekKey(sortValueOf(last, sort), last.getId());
        }

        SeekKey prev;
        if (seekKey == null || rows.isEmpty()) {
            prev = null;
        } else {
            BookDto first = rows.getFirst();
            SeekKey currentFirstKey = new SeekKey(sortValueOf(first, sort), first.getId());
            prev = repo.findPrevSeed(sort, dir, size, currentFirstKey, where);
        }

        return new BookPage(rows).next(next).prev(prev);
    }

    private static @Nullable SeekKey parseSeekKey(
            SortField sort, SearchBooksQuery.@Nullable Cursor cursor) {
        if (cursor == null) return null;
        Object typed;
        try {
            typed =
                    switch (sort) {
                        case TITLE, AUTHOR -> cursor.value();
                        case PRICE, RATING -> new BigDecimal(cursor.value());
                        case PUBLISHED_AT -> LocalDate.parse(cursor.value());
                    };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new BadRequestException(
                    "cursorValue is not a valid " + sort.getValue() + ": " + cursor.value());
        }
        return new SeekKey(typed, cursor.id());
    }

    private static Condition buildConditions(@Nullable BookFilters filters) {
        Condition where = DSL.noCondition();
        if (filters == null) return where;
        if (filters.getGenre() != null && !filters.getGenre().isEmpty()) {
            where =
                    where.and(
                            BOOK.GENRE.in(
                                    filters.getGenre().stream().map(Genre::getValue).toList()));
        }
        if (filters.getLanguage() != null) {
            where = where.and(BOOK.LANGUAGE.eq(filters.getLanguage().getValue()));
        }
        if (filters.getInStock() != null) {
            where = where.and(BOOK.IN_STOCK.eq(filters.getInStock()));
        }
        if (filters.getMinRating() != null) {
            where = where.and(BOOK.RATING.ge(filters.getMinRating()));
        }
        if (filters.getPriceMin() != null) {
            where = where.and(BOOK.PRICE.ge(filters.getPriceMin()));
        }
        if (filters.getPriceMax() != null) {
            where = where.and(BOOK.PRICE.le(filters.getPriceMax()));
        }
        if (filters.getPublishedAfter() != null) {
            where = where.and(BOOK.PUBLISHED_AT.ge(filters.getPublishedAfter()));
        }
        return where;
    }

    private static Object sortValueOf(BookDto row, SortField sort) {
        return switch (sort) {
            case TITLE -> row.getTitle();
            case AUTHOR -> row.getAuthor();
            case PRICE -> row.getPrice();
            case RATING -> row.getRating();
            case PUBLISHED_AT -> row.getPublishedAt();
        };
    }
}
