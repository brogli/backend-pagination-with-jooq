package ch.brogli.backendpagination.service.cursor;

import ch.brogli.backendpagination.api.model.BookDto;
import ch.brogli.backendpagination.api.model.SortField;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Typed sort-column anchor value for keyset seek. {@link #unwrap()} exists solely for the jOOQ
 * boundary — {@code DSL.seek(Object...)} takes raw varargs, so it's the one place the sealed-type
 * guarantee gives way.
 */
public sealed interface SortValue {

    Object unwrap();

    record StringValue(String value) implements SortValue {
        @Override
        public Object unwrap() {
            return value;
        }
    }

    record DecimalValue(BigDecimal value) implements SortValue {
        @Override
        public Object unwrap() {
            return value;
        }
    }

    record DateValue(LocalDate value) implements SortValue {
        @Override
        public Object unwrap() {
            return value;
        }
    }

    /** Picks the {@link SortValue} variant for the given sort field, populated from {@code row}. */
    static SortValue fromRow(BookDto row, SortField sort) {
        return switch (sort) {
            case TITLE -> new StringValue(row.getTitle());
            case AUTHOR -> new StringValue(row.getAuthor());
            case PRICE -> new DecimalValue(row.getPrice());
            case RATING -> new DecimalValue(row.getRating());
            case PUBLISHED_AT -> new DateValue(row.getPublishedAt());
        };
    }
}
