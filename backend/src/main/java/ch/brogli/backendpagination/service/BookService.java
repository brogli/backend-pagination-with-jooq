package ch.brogli.backendpagination.service;

import ch.brogli.backendpagination.api.model.BookDto;
import ch.brogli.backendpagination.api.model.BookPage;
import ch.brogli.backendpagination.persistence.repository.BookRepository;
import ch.brogli.backendpagination.persistence.repository.BookRepository.PageResult;
import ch.brogli.backendpagination.service.cursor.Cursor;
import ch.brogli.backendpagination.service.cursor.Navigation;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BookService {
    private final BookRepository repo;

    public BookService(BookRepository repo) {
        this.repo = repo;
    }

    public BookPage search(SearchBooksQuery query) {
        Cursor cursor = query.cursor();
        Navigation navigation = cursor == null ? Navigation.NEXT : cursor.navigation();

        PageResult page =
                repo.fetchPage(
                        query.sort(), query.direction(), query.size(), cursor, query.filters());
        List<BookDto> rows = page.rows();
        if (rows.isEmpty()) {
            return new BookPage(rows);
        }

        String filters = query.filters().fingerprint();
        Cursor next =
                Cursor.fromRow(
                        rows.getLast(), query.sort(), query.direction(), filters, Navigation.NEXT);
        Cursor prev =
                Cursor.fromRow(
                        rows.getFirst(), query.sort(), query.direction(), filters, Navigation.PREV);
        return switch (navigation) {
            case NEXT ->
                    new BookPage(rows)
                            .nextCursor(page.hasMore() ? next.encode() : null)
                            .prevCursor(cursor != null ? prev.encode() : null);
            case PREV ->
                    new BookPage(rows)
                            .nextCursor(next.encode())
                            .prevCursor(page.hasMore() ? prev.encode() : null);
        };
    }
}
