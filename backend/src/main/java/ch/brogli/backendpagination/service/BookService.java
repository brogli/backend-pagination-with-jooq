package ch.brogli.backendpagination.service;

import static ch.brogli.backendpagination.jooq.Tables.BOOK;

import ch.brogli.backendpagination.api.model.BookDto;
import ch.brogli.backendpagination.api.model.BookPage;
import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.Genre;
import ch.brogli.backendpagination.api.model.SortField;
import ch.brogli.backendpagination.persistence.repository.BookRepository;
import ch.brogli.backendpagination.persistence.repository.BookRepository.Anchor;
import ch.brogli.backendpagination.persistence.repository.BookRepository.PageResult;
import ch.brogli.backendpagination.service.cursor.Cursor;
import ch.brogli.backendpagination.service.cursor.Navigation;
import ch.brogli.backendpagination.service.cursor.SortValue;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

@Service
public class BookService {
    private final BookRepository repo;

    public BookService(BookRepository repo) {
        this.repo = repo;
    }

    public BookPage search(SearchBooksQuery query) {
        SortField sort = query.paging().sort();
        Direction direction = query.paging().direction();
        int size = query.paging().size();
        Optional<Cursor> cursor = Optional.ofNullable(query.cursor());

        Navigation navigation = cursor.map(Cursor::navigation).orElse(Navigation.NEXT);
        Optional<Anchor> requestAnchor = cursor.map(c -> new Anchor(c.value(), c.id()));
        Condition where = buildConditions(query.filters());

        PageResult page = repo.fetchPage(sort, direction, navigation, size, requestAnchor, where);
        List<BookDto> rows = page.rows();
        if (rows.isEmpty()) {
            return new BookPage(rows);
        }
        return buildPage(rows, sort, direction, navigation, page.hasMore(), cursor.isPresent());
    }

    private static BookPage buildPage(
            List<BookDto> rows,
            SortField sort,
            Direction direction,
            Navigation navigation,
            boolean hasMore,
            boolean hasCursor) {
        BookDto first = rows.getFirst();
        BookDto last = rows.getLast();
        Cursor next =
                Cursor.of(
                        sort,
                        direction,
                        Navigation.NEXT,
                        SortValue.fromRow(last, sort),
                        last.getId());
        Cursor prev =
                Cursor.of(
                        sort,
                        direction,
                        Navigation.PREV,
                        SortValue.fromRow(first, sort),
                        first.getId());
        return switch (navigation) {
            case NEXT ->
                    new BookPage(rows)
                            .nextCursor(hasMore ? next.encode() : null)
                            .prevCursor(hasCursor ? prev.encode() : null);
            case PREV ->
                    new BookPage(rows)
                            .nextCursor(next.encode())
                            .prevCursor(hasMore ? prev.encode() : null);
        };
    }

    private static Condition buildConditions(SearchBooksQuery.Filters filters) {
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
}
