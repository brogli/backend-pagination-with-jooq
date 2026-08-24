package ch.brogli.backendpagination.service.cursor;

import ch.brogli.backendpagination.api.model.BookDto;
import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.SortField;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Opaque pagination cursor. Wire format is a base64url (no padding) JSON object:
 *
 * <pre>
 * {"v":2,"sort":"title","direction":"asc","navigation":"NEXT","filters":"Qm9va3MhISE","value":"Dune","id":42}
 * </pre>
 *
 * <p>{@code value} is the sort-column value of the anchor row and {@code id} the tiebreaker. The
 * runtime type of {@code value} is fixed by {@code sort}: {@link String} for title and author,
 * {@link BigDecimal} for price and rating, {@link LocalDate} for publishedAt. The compact
 * constructor enforces that pairing.
 *
 * <p>{@code filters} is a fingerprint of the filter parameters the cursor was issued under, so a
 * cursor cannot be replayed against a different filter set.
 *
 * <p>{@link Navigation#NEXT} cursors anchor the last row of the current page, {@link
 * Navigation#PREV} cursors anchor the first.
 *
 * <p>All decode failures throw {@link IllegalArgumentException}. The controller maps that to a 400.
 */
public record Cursor(
        SortField sort,
        Direction direction,
        Navigation navigation,
        String filters,
        Object value,
        long id) {

    public static final int CURRENT_VERSION = 2;

    private static final ObjectMapper MAPPER =
            JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public Cursor {
        Objects.requireNonNull(filters, "filters fingerprint");
        Class<?> expected = valueTypeFor(sort);
        if (!expected.isInstance(value)) {
            throw new IllegalArgumentException(
                    "sort " + sort + " needs a " + expected.getSimpleName() + " value");
        }
    }

    /** Anchors {@code row} for the given sort and navigation. */
    public static Cursor fromRow(
            BookDto row,
            SortField sort,
            Direction direction,
            String filters,
            Navigation navigation) {
        Object value =
                switch (sort) {
                    case TITLE -> row.getTitle();
                    case AUTHOR -> row.getAuthor();
                    case PRICE -> row.getPrice();
                    case RATING -> row.getRating();
                    case PUBLISHED_AT -> row.getPublishedAt();
                };
        return new Cursor(sort, direction, navigation, filters, value, row.getId());
    }

    public String encode() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("v", CURRENT_VERSION);
        node.put("sort", sort.getValue());
        node.put("direction", direction.getValue());
        node.put("navigation", navigation.name());
        node.put("filters", filters);
        node.putPOJO("value", value);
        node.put("id", id);
        return ENCODER.encodeToString(MAPPER.writeValueAsBytes(node));
    }

    /**
     * Returns empty if {@code encoded} is null or blank. Throws {@link IllegalArgumentException} on
     * malformed input, unsupported version, sort/direction mismatch, or filter fingerprint
     * mismatch.
     */
    public static Optional<Cursor> decode(
            @Nullable String encoded,
            SortField expectedSort,
            Direction expectedDirection,
            String expectedFilters) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        JsonNode node = readTree(encoded);
        long version = requireLong(node, "v");
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("cursor version " + version + " is not supported");
        }
        SortField sort = parseEnum(requireText(node, "sort"), SortField::fromValue);
        Direction direction = parseEnum(requireText(node, "direction"), Direction::fromValue);
        Navigation navigation = parseEnum(requireText(node, "navigation"), Navigation::valueOf);
        if (sort != expectedSort || direction != expectedDirection) {
            throw new IllegalArgumentException("cursor is for a different sort/direction");
        }
        String filters = requireText(node, "filters");
        if (!filters.equals(expectedFilters)) {
            throw new IllegalArgumentException("cursor is for a different filter set");
        }
        Object value = parseValue(node.path("value"), sort);
        long id = requireLong(node, "id");
        return Optional.of(new Cursor(sort, direction, navigation, filters, value, id));
    }

    private static Class<?> valueTypeFor(SortField sort) {
        return switch (sort) {
            case TITLE, AUTHOR -> String.class;
            case PRICE, RATING -> BigDecimal.class;
            case PUBLISHED_AT -> LocalDate.class;
        };
    }

    private static JsonNode readTree(String encoded) {
        JsonNode node;
        try {
            node = MAPPER.readTree(DECODER.decode(encoded));
        } catch (IllegalArgumentException | JacksonException e) {
            throw malformed();
        }
        if (node == null || !node.isObject()) {
            throw malformed();
        }
        return node;
    }

    private static Object parseValue(JsonNode valueNode, SortField sort) {
        return switch (sort) {
            case TITLE, AUTHOR -> {
                if (!valueNode.isString()) {
                    throw malformed();
                }
                yield valueNode.asString();
            }
            case PRICE, RATING -> {
                if (!valueNode.isNumber()) {
                    throw malformed();
                }
                yield valueNode.decimalValue();
            }
            case PUBLISHED_AT -> {
                if (!valueNode.isString()) {
                    throw malformed();
                }
                try {
                    yield LocalDate.parse(valueNode.asString());
                } catch (DateTimeParseException e) {
                    throw malformed();
                }
            }
        };
    }

    private static <E> E parseEnum(String text, Function<String, E> parser) {
        try {
            return parser.apply(text);
        } catch (IllegalArgumentException e) {
            throw malformed();
        }
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (!child.isString()) {
            throw malformed();
        }
        return child.asString();
    }

    private static long requireLong(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (!child.canConvertToLong()) {
            throw malformed();
        }
        return child.asLong();
    }

    private static IllegalArgumentException malformed() {
        return new IllegalArgumentException("cursor is malformed");
    }
}
