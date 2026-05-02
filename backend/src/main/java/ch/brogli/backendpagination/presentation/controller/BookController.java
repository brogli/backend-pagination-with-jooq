package ch.brogli.backendpagination.presentation.controller;

import ch.brogli.backendpagination.api.BooksApi;
import ch.brogli.backendpagination.api.model.BookPage;
import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.Genre;
import ch.brogli.backendpagination.api.model.Language;
import ch.brogli.backendpagination.api.model.SortField;
import ch.brogli.backendpagination.presentation.exception.BadRequestException;
import ch.brogli.backendpagination.service.BookService;
import ch.brogli.backendpagination.service.SearchBooksQuery;
import ch.brogli.backendpagination.service.cursor.Cursor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookController implements BooksApi {

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<BookPage> searchBooks(
            SortField sort,
            Direction direction,
            Integer size,
            @Nullable String cursor,
            @Nullable List<Genre> genre,
            @Nullable Language language,
            @Nullable Boolean inStock,
            @Nullable BigDecimal minRating,
            @Nullable BigDecimal priceMin,
            @Nullable BigDecimal priceMax,
            @Nullable LocalDate publishedAfter) {

        validatePriceRange(priceMin, priceMax);

        SearchBooksQuery.Filters filters =
                new SearchBooksQuery.Filters(
                        genre, language, inStock, minRating, priceMin, priceMax, publishedAfter);

        SearchBooksQuery query =
                new SearchBooksQuery(
                        new SearchBooksQuery.Paging(sort, direction, size),
                        Cursor.decode(cursor, sort, direction).orElse(null),
                        filters);

        return ResponseEntity.ok(service.search(query));
    }

    private static void validatePriceRange(
            @Nullable BigDecimal priceMin, @Nullable BigDecimal priceMax) {
        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0) {
            throw new BadRequestException(
                    "priceMin (" + priceMin + ") must be <= priceMax (" + priceMax + ")");
        }
    }
}
