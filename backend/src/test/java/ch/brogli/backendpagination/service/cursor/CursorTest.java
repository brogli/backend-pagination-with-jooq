package ch.brogli.backendpagination.service.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.SortField;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CursorTest {

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }

    @Nested
    class Construction {

        @Test
        void rejectsValueTypeThatDoesNotMatchSort() {
            assertThatThrownBy(
                            () ->
                                    new Cursor(
                                            SortField.TITLE,
                                            Direction.ASC,
                                            Navigation.NEXT,
                                            new BigDecimal("1"),
                                            1L))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    new Cursor(
                                            SortField.PRICE,
                                            Direction.ASC,
                                            Navigation.NEXT,
                                            "9.99",
                                            1L))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    new Cursor(
                                            SortField.PUBLISHED_AT,
                                            Direction.ASC,
                                            Navigation.NEXT,
                                            "2020-01-01",
                                            1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class RoundTrip {

        @Test
        void preservesStringValueForTitle() {
            Cursor in =
                    new Cursor(
                            SortField.TITLE,
                            Direction.ASC,
                            Navigation.NEXT,
                            "Hitchhiker's Guide",
                            42L);

            Optional<Cursor> out = Cursor.decode(in.encode(), SortField.TITLE, Direction.ASC);

            assertThat(out).contains(in);
        }

        @Test
        void preservesBigDecimalScaleForPrice() {
            BigDecimal price = new BigDecimal("19.99");
            Cursor in = new Cursor(SortField.PRICE, Direction.DESC, Navigation.NEXT, price, 7L);

            Cursor out = Cursor.decode(in.encode(), SortField.PRICE, Direction.DESC).orElseThrow();

            assertThat(out).isEqualTo(in);
            assertThat(((BigDecimal) out.value()).scale()).isEqualTo(2);
        }

        @Test
        void preservesBigDecimalForRatingWithOneDecimal() {
            Cursor in =
                    new Cursor(
                            SortField.RATING,
                            Direction.ASC,
                            Navigation.PREV,
                            new BigDecimal("4.0"),
                            3L);

            Cursor out = Cursor.decode(in.encode(), SortField.RATING, Direction.ASC).orElseThrow();

            assertThat(out).isEqualTo(in);
        }

        @Test
        void preservesLocalDateForPublishedAt() {
            Cursor in =
                    new Cursor(
                            SortField.PUBLISHED_AT,
                            Direction.ASC,
                            Navigation.PREV,
                            LocalDate.of(2024, 5, 17),
                            99L);

            Optional<Cursor> out =
                    Cursor.decode(in.encode(), SortField.PUBLISHED_AT, Direction.ASC);

            assertThat(out).contains(in);
        }

        @Test
        void encodedFormIsUrlSafeBase64WithoutPadding() {
            String encoded =
                    new Cursor(SortField.TITLE, Direction.ASC, Navigation.NEXT, "test", 1L)
                            .encode();

            assertThat(encoded).doesNotContain("=").doesNotContain("+").doesNotContain("/");
        }

        @Test
        void wireFormatIsStable() {
            String encoded =
                    new Cursor(SortField.TITLE, Direction.ASC, Navigation.NEXT, "Dune", 42L)
                            .encode();

            String json = new String(Base64.getUrlDecoder().decode(encoded));

            assertThat(json)
                    .isEqualTo(
                            "{\"v\":1,\"sort\":\"title\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"value\":\"Dune\",\"id\":42}");
        }

        @Test
        void wireFormatIsStableForDecimalValue() {
            String encoded =
                    new Cursor(
                                    SortField.PRICE,
                                    Direction.ASC,
                                    Navigation.NEXT,
                                    new BigDecimal("10.50"),
                                    42L)
                            .encode();

            String json = new String(Base64.getUrlDecoder().decode(encoded));

            assertThat(json)
                    .isEqualTo(
                            "{\"v\":1,\"sort\":\"price\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"value\":10.50,\"id\":42}");
        }

        @Test
        void wireFormatIsStableForDateValue() {
            String encoded =
                    new Cursor(
                                    SortField.PUBLISHED_AT,
                                    Direction.ASC,
                                    Navigation.NEXT,
                                    LocalDate.of(2024, 5, 17),
                                    42L)
                            .encode();

            String json = new String(Base64.getUrlDecoder().decode(encoded));

            assertThat(json)
                    .isEqualTo(
                            "{\"v\":1,\"sort\":\"publishedAt\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"value\":\"2024-05-17\",\"id\":42}");
        }
    }

    @Nested
    class Decode {

        @Test
        void returnsEmptyForNullInput() {
            assertThat(Cursor.decode(null, SortField.TITLE, Direction.ASC)).isEmpty();
        }

        @Test
        void returnsEmptyForBlankInput() {
            assertThat(Cursor.decode("   ", SortField.TITLE, Direction.ASC)).isEmpty();
        }

        @Test
        void rejectsSortMismatch() {
            String encoded =
                    new Cursor(SortField.TITLE, Direction.ASC, Navigation.NEXT, "x", 1L).encode();

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.AUTHOR, Direction.ASC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("different sort/direction");
        }

        @Test
        void rejectsDirectionMismatch() {
            String encoded =
                    new Cursor(SortField.TITLE, Direction.ASC, Navigation.NEXT, "x", 1L).encode();

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.DESC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("different sort/direction");
        }

        @Test
        void rejectsTamperedBase64() {
            assertThatThrownBy(
                            () -> Cursor.decode("!!!not-base64!!!", SortField.TITLE, Direction.ASC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsValidBase64WithGarbageJson() {
            assertThatThrownBy(() -> Cursor.decode(b64("not json"), SortField.TITLE, Direction.ASC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsUnknownVersionBeforeLookingAtTheRest() {
            // Deliberately a shape a future version might use: no "value", no "id". The version
            // check must win over "malformed".
            String encoded = b64("{\"v\":999,\"anchor\":{\"a\":1}}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("version 999");
        }

        @Test
        void rejectsMissingVersion() {
            String encoded =
                    b64(
                            "{\"sort\":\"title\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"value\":\"x\",\"id\":1}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsNumericValueForStringSort() {
            String encoded =
                    b64(
                            "{\"v\":1,\"sort\":\"title\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"value\":42,\"id\":1}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsStringValueForDecimalSort() {
            String encoded =
                    b64(
                            "{\"v\":1,\"sort\":\"price\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"value\":\"9.99\",\"id\":1}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.PRICE, Direction.ASC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsMalformedDate() {
            String encoded =
                    b64(
                            "{\"v\":1,\"sort\":\"publishedAt\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"value\":\"not-a-date\",\"id\":1}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.PUBLISHED_AT, Direction.ASC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsUnknownSortOrNavigationName() {
            String encoded =
                    b64(
                            "{\"v\":1,\"sort\":\"isbn\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"value\":\"x\",\"id\":1}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsMissingId() {
            String encoded =
                    b64(
                            "{\"v\":1,\"sort\":\"title\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"value\":\"x\"}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsIdOutOfLongRange() {
            // A syntactically valid, integral JSON number that overflows `long`. Jackson's
            // asLong() throws JsonNodeException (not IllegalArgumentException) for this without
            // the canConvertToLong() guard in requireLong.
            String encoded =
                    b64(
                            "{\"v\":1,\"sort\":\"title\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"value\":\"x\",\"id\":99999999999999999999999}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }
    }
}
