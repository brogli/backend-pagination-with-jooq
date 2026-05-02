package ch.brogli.backendpagination.service.cursor;

import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.SortField;
import ch.brogli.backendpagination.presentation.exception.BadRequestException;
import ch.brogli.backendpagination.service.cursor.SortValue.DateValue;
import ch.brogli.backendpagination.service.cursor.SortValue.DecimalValue;
import ch.brogli.backendpagination.service.cursor.SortValue.StringValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opaque pagination cursor. Wire format: base64url-without-padding JSON envelope carrying version,
 * sort, direction, navigation hint, anchor sort value, and tiebreaker id.
 *
 * <p>{@link Navigation#NEXT} cursors anchor the last row of the current page; {@link
 * Navigation#PREV} cursors anchor the first.
 */
public sealed interface Cursor {

    int CURRENT_VERSION = 1;

    SortField sort();

    Direction direction();

    Navigation navigation();

    SortValue value();

    long id();

    String encode();

    record StringCursor(
            SortField sort, Direction direction, Navigation navigation, StringValue value, long id)
            implements Cursor {
        public StringCursor {
            requireSortMatchesValueType(sort, SortField.TITLE, SortField.AUTHOR);
        }

        @Override
        public String encode() {
            return Codec.encode(
                    new Envelope<>(
                            CURRENT_VERSION, sort, direction, navigation, value.value(), id));
        }
    }

    record DecimalCursor(
            SortField sort, Direction direction, Navigation navigation, DecimalValue value, long id)
            implements Cursor {
        public DecimalCursor {
            requireSortMatchesValueType(sort, SortField.PRICE, SortField.RATING);
        }

        @Override
        public String encode() {
            return Codec.encode(
                    new Envelope<>(
                            CURRENT_VERSION, sort, direction, navigation, value.value(), id));
        }
    }

    record DateCursor(
            SortField sort, Direction direction, Navigation navigation, DateValue value, long id)
            implements Cursor {
        public DateCursor {
            requireSortMatchesValueType(sort, SortField.PUBLISHED_AT);
        }

        @Override
        public String encode() {
            return Codec.encode(
                    new Envelope<>(
                            CURRENT_VERSION, sort, direction, navigation, value.value(), id));
        }
    }

    /** Builds the variant matching {@code value}'s type. */
    static Cursor of(
            SortField sort, Direction direction, Navigation navigation, SortValue value, long id) {
        return switch (value) {
            case StringValue s -> new StringCursor(sort, direction, navigation, s, id);
            case DecimalValue d -> new DecimalCursor(sort, direction, navigation, d, id);
            case DateValue d -> new DateCursor(sort, direction, navigation, d, id);
        };
    }

    /**
     * Returns empty if {@code encoded} is null/blank. Throws {@link BadRequestException} on
     * malformed base64/JSON, unknown version, or sort/direction mismatch.
     */
    static Optional<Cursor> decode(
            @Nullable String encoded, SortField expectedSort, Direction expectedDirection) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        Cursor decoded = Codec.decode(encoded, expectedSort);
        if (decoded.sort() != expectedSort || decoded.direction() != expectedDirection) {
            throw new BadRequestException("cursor is for a different sort/direction");
        }
        return Optional.of(decoded);
    }

    private static void requireSortMatchesValueType(SortField sort, SortField... allowed) {
        for (SortField a : allowed) {
            if (a == sort) {
                return;
            }
        }
        throw new IllegalArgumentException("sort " + sort + " does not match this cursor variant");
    }

    /** Wire envelope. Generic so each cursor variant binds Jackson to a statically-typed value. */
    record Envelope<V>(
            @JsonProperty("v") int version,
            @JsonProperty("sort") SortField sort,
            @JsonProperty("direction") Direction direction,
            @JsonProperty("navigation") Navigation navigation,
            @JsonProperty("value") V value,
            @JsonProperty("id") long id) {}

    final class Codec {
        private static final ObjectMapper MAPPER =
                JsonMapper.builder()
                        .withCoercionConfigDefaults(
                                cfg -> {
                                    cfg.setCoercion(
                                            CoercionInputShape.Integer, CoercionAction.Fail);
                                    cfg.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                                    cfg.setCoercion(
                                            CoercionInputShape.Boolean, CoercionAction.Fail);
                                    cfg.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
                                })
                        .build();
        private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
        private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

        private Codec() {}

        static <V> String encode(Envelope<V> envelope) {
            return ENCODER.encodeToString(MAPPER.writeValueAsBytes(envelope));
        }

        static Cursor decode(String encoded, SortField expectedSort) {
            byte[] json;
            try {
                json = DECODER.decode(encoded);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("cursor is malformed");
            }
            return switch (expectedSort) {
                case TITLE, AUTHOR ->
                        readVariant(
                                json,
                                String.class,
                                (sort, dir, nav, value, id) ->
                                        new StringCursor(
                                                sort, dir, nav, new StringValue(value), id));
                case PRICE, RATING ->
                        readVariant(
                                json,
                                BigDecimal.class,
                                (sort, dir, nav, value, id) ->
                                        new DecimalCursor(
                                                sort, dir, nav, new DecimalValue(value), id));
                case PUBLISHED_AT ->
                        readVariant(
                                json,
                                LocalDate.class,
                                (sort, dir, nav, value, id) ->
                                        new DateCursor(sort, dir, nav, new DateValue(value), id));
            };
        }

        private static <V> Cursor readVariant(
                byte[] json, Class<V> valueType, VariantBuilder<V> builder) {
            JavaType envelopeType =
                    MAPPER.getTypeFactory().constructParametricType(Envelope.class, valueType);
            Envelope<V> envelope;
            try {
                envelope = MAPPER.readValue(json, envelopeType);
            } catch (JacksonException e) {
                throw new BadRequestException("cursor is malformed");
            }
            if (envelope == null
                    || envelope.sort() == null
                    || envelope.direction() == null
                    || envelope.navigation() == null
                    || envelope.value() == null) {
                throw new BadRequestException("cursor is malformed");
            }
            if (envelope.version() != CURRENT_VERSION) {
                throw new BadRequestException(
                        "cursor version " + envelope.version() + " is not supported");
            }
            try {
                return builder.build(
                        envelope.sort(),
                        envelope.direction(),
                        envelope.navigation(),
                        envelope.value(),
                        envelope.id());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("cursor is malformed");
            }
        }

        @FunctionalInterface
        private interface VariantBuilder<V> {
            Cursor build(
                    SortField sort, Direction direction, Navigation navigation, V value, long id);
        }
    }
}
