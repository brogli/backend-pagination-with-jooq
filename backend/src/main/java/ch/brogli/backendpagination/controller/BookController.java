package ch.brogli.backendpagination.controller;

import ch.brogli.backendpagination.api.BooksApi;
import ch.brogli.backendpagination.api.model.BookFilters;
import ch.brogli.backendpagination.api.model.BookPage;
import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.SortField;
import ch.brogli.backendpagination.service.BookService;
import ch.brogli.backendpagination.service.SearchBooksQuery;
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
            Direction dir,
            Integer size,
            @Nullable String cursorValue,
            @Nullable Long cursorId,
            @Nullable BookFilters filters) {

        SearchBooksQuery query =
                new SearchBooksQuery(
                        new SearchBooksQuery.Paging(sort, dir, size),
                        SearchBooksQuery.Cursor.fromOptional(cursorValue, cursorId),
                        filters);

        return ResponseEntity.ok(service.search(query));
    }
}
