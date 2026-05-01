package ch.brogli.backendpagination.service;

import ch.brogli.backendpagination.api.model.BookFilters;
import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.SortField;
import ch.brogli.backendpagination.exception.BadRequestException;
import org.jspecify.annotations.Nullable;

public record SearchBooksQuery(
        Paging paging, @Nullable Cursor cursor, @Nullable BookFilters filters) {

    public record Paging(SortField sort, Direction dir, int size) {}

    /**
     * Cursor anchor row. Both fields are mandatory by construction; the "both-or-neither"
     * wire-level pair is normalized through {@link #fromOptional}, so downstream code never sees a
     * half-set cursor.
     */
    public record Cursor(String value, long id) {
        public static @Nullable Cursor fromOptional(@Nullable String value, @Nullable Long id) {
            boolean hasValue = value != null && !value.isBlank();
            boolean hasId = id != null;
            if (hasValue != hasId) {
                throw new BadRequestException("cursorValue and cursorId must be provided together");
            }
            return hasValue ? new Cursor(value, id) : null;
        }
    }
}
