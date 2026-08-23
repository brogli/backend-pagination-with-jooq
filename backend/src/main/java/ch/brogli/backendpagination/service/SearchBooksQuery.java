package ch.brogli.backendpagination.service;

import static java.util.stream.Collectors.joining;

import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.Genre;
import ch.brogli.backendpagination.api.model.Language;
import ch.brogli.backendpagination.api.model.SortField;
import ch.brogli.backendpagination.service.cursor.Cursor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record SearchBooksQuery(
        SortField sort, Direction direction, int size, @Nullable Cursor cursor, Filters filters) {

    public record Filters(
            @Nullable List<Genre> genre,
            @Nullable Language language,
            @Nullable Boolean inStock,
            @Nullable BigDecimal minRating,
            @Nullable BigDecimal priceMin,
            @Nullable BigDecimal priceMax,
            @Nullable LocalDate publishedAfter) {

        /**
         * Short, stable digest of the filter values. Two requests with the same filters produce the
         * same fingerprint across JVM restarts. Used to bind a cursor to the filter set it was
         * issued under.
         */
        public String fingerprint() {
            String genres =
                    genre == null
                            ? ""
                            : genre.stream()
                                    .map(Genre::getValue)
                                    .distinct()
                                    .sorted()
                                    .collect(joining(","));
            String canonical =
                    String.join(
                            "|",
                            genres,
                            language == null ? "" : language.getValue(),
                            Objects.toString(inStock, ""),
                            plain(minRating),
                            plain(priceMin),
                            plain(priceMax),
                            Objects.toString(publishedAfter, ""));
            byte[] digest = sha256(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, 8));
        }

        private static String plain(@Nullable BigDecimal v) {
            return v == null ? "" : v.stripTrailingZeros().toPlainString();
        }

        private static byte[] sha256(byte[] input) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(input);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is mandatory in every JRE", e);
            }
        }
    }
}
