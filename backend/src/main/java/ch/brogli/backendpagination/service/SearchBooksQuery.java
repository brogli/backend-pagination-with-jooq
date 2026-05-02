package ch.brogli.backendpagination.service;

import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.Genre;
import ch.brogli.backendpagination.api.model.Language;
import ch.brogli.backendpagination.api.model.SortField;
import ch.brogli.backendpagination.service.cursor.Cursor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record SearchBooksQuery(Paging paging, @Nullable Cursor cursor, Filters filters) {

    public record Paging(SortField sort, Direction direction, int size) {}

    public record Filters(
            @Nullable List<Genre> genre,
            @Nullable Language language,
            @Nullable Boolean inStock,
            @Nullable BigDecimal minRating,
            @Nullable BigDecimal priceMin,
            @Nullable BigDecimal priceMax,
            @Nullable LocalDate publishedAfter) {}
}
