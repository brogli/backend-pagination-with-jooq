package ch.brogli.backendpagination.service.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.SortField;
import ch.brogli.backendpagination.presentation.exception.BadRequestException;
import ch.brogli.backendpagination.service.cursor.Cursor.DateCursor;
import ch.brogli.backendpagination.service.cursor.Cursor.DecimalCursor;
import ch.brogli.backendpagination.service.cursor.Cursor.StringCursor;
import ch.brogli.backendpagination.service.cursor.SortValue.DateValue;
import ch.brogli.backendpagination.service.cursor.SortValue.DecimalValue;
import ch.brogli.backendpagination.service.cursor.SortValue.StringValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CursorTest {

    @Nested
    class RoundTrip {

        @Test
        void preservesStringValueForTitle() {
            Cursor in =
                    new StringCursor(
                            SortField.TITLE,
                            Direction.ASC,
                            Navigation.NEXT,
                            new StringValue("Hitchhiker's Guide"),
                            42L);

            Optional<Cursor> out = Cursor.decode(in.encode(), SortField.TITLE, Direction.ASC);

            assertThat(out).containsInstanceOf(StringCursor.class);
            Cursor decoded = out.orElseThrow();
            assertThat(decoded.value()).isEqualTo(new StringValue("Hitchhiker's Guide"));
            assertThat(decoded.id()).isEqualTo(42L);
            assertThat(decoded.sort()).isEqualTo(SortField.TITLE);
            assertThat(decoded.direction()).isEqualTo(Direction.ASC);
            assertThat(decoded.navigation()).isEqualTo(Navigation.NEXT);
        }

        @Test
        void preservesBigDecimalScaleForPrice() {
            BigDecimal price = new BigDecimal("19.99");
            Cursor in =
                    new DecimalCursor(
                            SortField.PRICE,
                            Direction.DESC,
                            Navigation.NEXT,
                            new DecimalValue(price),
                            7L);

            Optional<Cursor> out = Cursor.decode(in.encode(), SortField.PRICE, Direction.DESC);

            assertThat(out).containsInstanceOf(DecimalCursor.class);
            BigDecimal decoded = ((DecimalCursor) out.orElseThrow()).value().value();
            assertThat(decoded).isEqualByComparingTo(price);
            assertThat(decoded.scale()).isEqualTo(2);
        }

        @Test
        void preservesLocalDateForPublishedAt() {
            LocalDate date = LocalDate.of(2024, 5, 17);
            Cursor in =
                    new DateCursor(
                            SortField.PUBLISHED_AT,
                            Direction.ASC,
                            Navigation.PREV,
                            new DateValue(date),
                            99L);

            Optional<Cursor> out =
                    Cursor.decode(in.encode(), SortField.PUBLISHED_AT, Direction.ASC);

            assertThat(out).containsInstanceOf(DateCursor.class);
            Cursor decoded = out.orElseThrow();
            assertThat(decoded.value()).isEqualTo(new DateValue(date));
            assertThat(decoded.navigation()).isEqualTo(Navigation.PREV);
        }

        @Test
        void encodedFormIsUrlSafeBase64WithoutPadding() {
            Cursor in =
                    new StringCursor(
                            SortField.TITLE,
                            Direction.ASC,
                            Navigation.NEXT,
                            new StringValue("test"),
                            1L);

            String encoded = in.encode();

            assertThat(encoded).doesNotContain("=").doesNotContain("+").doesNotContain("/");
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
        void rejectsSortMismatchWith400() {
            String encoded =
                    new StringCursor(
                                    SortField.TITLE,
                                    Direction.ASC,
                                    Navigation.NEXT,
                                    new StringValue("x"),
                                    1L)
                            .encode();

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.AUTHOR, Direction.ASC))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("different sort/direction");
        }

        @Test
        void rejectsDirectionMismatchWith400() {
            String encoded =
                    new StringCursor(
                                    SortField.TITLE,
                                    Direction.ASC,
                                    Navigation.NEXT,
                                    new StringValue("x"),
                                    1L)
                            .encode();

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.DESC))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("different sort/direction");
        }

        @Test
        void rejectsTamperedBase64With400() {
            assertThatThrownBy(
                            () -> Cursor.decode("!!!not-base64!!!", SortField.TITLE, Direction.ASC))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsValidBase64WithGarbageJsonWith400() {
            String junk =
                    Base64.getUrlEncoder().withoutPadding().encodeToString("not json".getBytes());

            assertThatThrownBy(() -> Cursor.decode(junk, SortField.TITLE, Direction.ASC))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsUnknownVersionWith400() {
            // Hand-craft an envelope with v: 999 to simulate a newer client / future version.
            String json =
                    "{\"v\":999,\"sort\":\"title\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"value\":\"x\",\"id\":1}";
            String encoded =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("version 999");
        }

        @Test
        void rejectsValueTypeMismatchWith400() {
            // Numeric value in cursor but sort=title expects a string.
            String json =
                    "{\"v\":1,\"sort\":\"title\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"value\":42,\"id\":1}";
            String encoded =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.TITLE, Direction.ASC))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void rejectsMalformedDateWith400() {
            String json =
                    "{\"v\":1,\"sort\":\"publishedAt\",\"direction\":\"asc\",\"navigation\":\"NEXT\",\"value\":\"not-a-date\",\"id\":1}";
            String encoded =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());

            assertThatThrownBy(() -> Cursor.decode(encoded, SortField.PUBLISHED_AT, Direction.ASC))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("malformed");
        }
    }
}
