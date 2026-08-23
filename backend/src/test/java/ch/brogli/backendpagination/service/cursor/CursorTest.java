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

    private static final String FP = "fp000000000";

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
                                            FP,
                                            new BigDecimal("1"),
                                            1L))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    new Cursor(
                                            SortField.PRICE,
                                            Direction.ASC,
                                            Navigation.NEXT,
                                            FP,
                                            "9.99",
                                            1L))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    new Cursor(
                                            SortField.PUBLISHED_AT,
                                            Direction.ASC,
                                            Navigation.NEXT,
                                            FP,
                                            "2020-01-01",
                                            1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNullFiltersAtConstruction() {
            assertThatThrownBy(
                            () ->
                                    new Cursor(
                                            SortField.TITLE,
                                            Direction.ASC,
                                            Navigation.NEXT,
                                            null,
                                            "x",
                                            1L))
                    .isInstanceOf(NullPointerException.class);
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
                            FP,
                            "Hitchhiker's Guide",
                            42L);

            Optional<Cursor> out = Cursor.decode(in.encode(), SortField.TITLE, Direction.ASC, FP);

            assertThat(out).contains(in);
        }

        @Test
        void preservesBigDecimalScaleForPrice() {
            BigDecimal price = new BigDecimal("19.99");
            Cursor in = new Cursor(SortField.PRICE, Direction.DESC, Navigation.NEXT, FP, price, 7L);

            Cursor out =
                    Cursor.decode(in.encode(), SortField.PRICE, Direction.DESC, FP).orElseThrow();

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
                            FP,
                            new BigDecimal("4.0"),
                            3L);

            Cursor out =
                    Cursor.decode(in.encode(), SortField.RATING, Direction.ASC, FP).orElseThrow();

            assertThat(out).isEqualTo(in);
        }

        @Test
        void preservesLocalDateForPublishedAt() {
            Cursor in =
                    new Cursor(
                            SortField.PUBLISHED_AT,
                            Direction.ASC,
                            Navigation.PREV,
                            FP,
                            LocalDate.of(2024, 5, 17),
                            99L);

            Optional<Cursor> out =
                    Cursor.decode(in.encode(), SortField.PUBLISHED_AT, Direction.ASC, FP);

            assertThat(out).contains(in);
        }

        @Test
        void encodedFormIsUrlSafeBase64WithoutPadding() {
            String encoded =
                    new Cursor(SortField.TITLE, Direction.ASC, Navigation.NEXT, FP, "test", 1L)
                            .encode();

            assertThat(encoded).doesNotContain("=").doesNotContain("+").doesNotContain("/");
        }

        @Test
        void wireFormatIsStable() {
            String encoded =
                    new Cursor(SortField.TITLE, Direction.ASC, Navigation.NEXT, FP, "Dune", 42L)
                            .encode();

            String json = new String(Base64.getUrlDecoder().decode(encoded));

            assertThat(json)
                    .isEqualTo(
                            "{\"v\":2,\"sort\":\"title\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"filters\":\"fp000000000\",\"value\":\"Dune\",\"id\":42}");
        }

        @Test
        void wireFormatIsStableForDecimalValue() {
            String encoded =
                    new Cursor(
                                    SortField.PRICE,
                                    Direction.ASC,
                                    Navigation.NEXT,
                                    FP,
                                    new BigDecimal("10.50"),
                                    42L)
                            .encode();

            String json = new String(Base64.getUrlDecoder().decode(encoded));

            assertThat(json)
                    .isEqualTo(
                            "{\"v\":2,\"sort\":\"price\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"filters\":\"fp000000000\",\"value\":10.50,\"id\":42}");
        }

        @Test
        void wireFormatIsStableForDateValue() {
            String encoded =
                    new Cursor(
                                    SortField.PUBLISHED_AT,
                                    Direction.ASC,
                                    Navigation.NEXT,
                                    FP,
                                    LocalDate.of(2024, 5, 17),
                                    42L)
                            .encode();

            String json = new String(Base64.getUrlDecoder().decode(encoded));

            assertThat(json)
                    .isEqualTo(
                            "{\"v\":2,\"sort\":\"publishedAt\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"filters\":\"fp000000000\",\"value\":\"2024-05-17\",\"id\":42}");
        }
    }

    @Nested
    class Decode {

        @Test
        void returnsEmptyForNullInput() {
            assertThat(Cursor.decode(null, SortField.TITLE, Direction.ASC, FP)).isEmpty();
        }

        @Test
        void returnsEmptyForBlankInput() {
            assertThat(Cursor.decode("   ", SortField.TITLE, Direction.ASC, FP)).isEmpty();
        }

        @Test
        void rejectsSortMismatch() {
            String encoded =
                    new Cursor(SortField.TITLE, Direction.ASC, Navigation.NEXT, FP, "x", 1L)
                            .encode();

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.AUTHOR, Direction.ASC, FP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("different sort/direction");
        }

        @Test
        void rejectsDirectionMismatch() {
            String encoded =
                    new Cursor(SortField.TITLE, Direction.ASC, Navigation.NEXT, FP, "x", 1L)
                            .encode();

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.DESC, FP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("different sort/direction");
        }

        @Test
        void rejectsFilterFingerprintMismatch() {
            String encoded =
                    new Cursor(SortField.TITLE, Direction.ASC, Navigation.NEXT, FP, "x", 1L)
                            .encode();

            assertThatThrownBy(
                            () ->
                                    Cursor.decode(
                                            encoded, SortField.TITLE, Direction.ASC, "other000000"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("different filter set");
        }

        @Test
        void rejectsVersionOneCursor() {
            String encoded =
                    b64(
                            "{\"v\":1,\"sort\":\"title\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"value\":\"x\",\"id\":1}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC, FP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("version 1");
        }

        @Test
        void rejectsTamperedBase64() {
            assertThatThrownBy(
                            () ->
                                    Cursor.decode(
                                            "!!!not-base64!!!", SortField.TITLE, Direction.ASC, FP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsValidBase64WithGarbageJson() {
            assertThatThrownBy(
                            () ->
                                    Cursor.decode(
                                            b64("not json"), SortField.TITLE, Direction.ASC, FP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsUnknownVersionBeforeLookingAtTheRest() {
            // Deliberately a shape a future version might use: no "value", no "id". The version
            // check must win over "malformed".
            String encoded = b64("{\"v\":999,\"anchor\":{\"a\":1}}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC, FP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("version 999");
        }

        @Test
        void rejectsMissingVersion() {
            String encoded =
                    b64(
                            "{\"sort\":\"title\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"filters\":\"fp000000000\",\"value\":\"x\",\"id\":1}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC, FP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsNumericValueForStringSort() {
            String encoded =
                    b64(
                            "{\"v\":2,\"sort\":\"title\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"filters\":\"fp000000000\",\"value\":42,\"id\":1}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC, FP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsStringValueForDecimalSort() {
            String encoded =
                    b64(
                            "{\"v\":2,\"sort\":\"price\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"filters\":\"fp000000000\",\"value\":\"9.99\",\"id\":1}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.PRICE, Direction.ASC, FP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsMalformedDate() {
            String encoded =
                    b64(
                            "{\"v\":2,\"sort\":\"publishedAt\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"filters\":\"fp000000000\",\"value\":\"not-a-date\",\"id\":1}");

            assertThatThrownBy(
                            () -> Cursor.decode(encoded, SortField.PUBLISHED_AT, Direction.ASC, FP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsUnknownSortOrNavigationName() {
            String encoded =
                    b64(
                            "{\"v\":2,\"sort\":\"isbn\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"filters\":\"fp000000000\",\"value\":\"x\",\"id\":1}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC, FP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsMissingId() {
            String encoded =
                    b64(
                            "{\"v\":2,\"sort\":\"title\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"filters\":\"fp000000000\",\"value\":\"x\"}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC, FP))
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
                            "{\"v\":2,\"sort\":\"title\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"filters\":\"fp000000000\",\"value\":\"x\",\"id\":99999999999999999999999}");

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC, FP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }
    }
}
