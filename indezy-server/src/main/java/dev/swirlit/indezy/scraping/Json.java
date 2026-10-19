package dev.swirlit.indezy.scraping;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Lenient reads of job board JSON: a missing, null, blank or malformed value is {@code null}, never an error. */
public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final ObjectReader FIRST_VALUE = MAPPER.readerFor(JsonNode.class).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private Json() {
    }

    public static JsonNode parse(String json) throws IOException {
        try {
            return MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new IOException("Unreadable JSON: " + e.getOriginalMessage(), e);
        }
    }

    /** The first JSON value of {@code text}, ignoring what follows it (a JSON array inside a script, say). */
    public static JsonNode parseFirst(String text) throws IOException {
        try (JsonParser parser = MAPPER.createParser(text)) {
            JsonNode value = FIRST_VALUE.readValue(parser);
            if (value == null) {
                throw new IOException("No JSON value");
            }
            return value;
        } catch (JacksonException e) {
            throw new IOException("Unreadable JSON: " + e.getOriginalMessage(), e);
        }
    }

    public static String text(JsonNode node) {
        if (node == null || !node.isValueNode() || node.isNull()) {
            return null;
        }
        String value = node.asString().strip();
        return value.isEmpty() ? null : value;
    }

    /** A whole number, from a JSON number or a numeric string ("550", "550.00"). */
    public static Integer integer(JsonNode node) {
        Double value = decimal(node);
        return value == null ? null : (int) Math.round(value);
    }

    public static Double decimal(JsonNode node) {
        if (node != null && node.isNumber()) {
            return node.asDouble();
        }
        String text = text(node);
        if (text == null) {
            return null;
        }
        try {
            return Double.valueOf(text.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** An ISO-8601 timestamp with an offset ("2026-10-19T18:48:39+02:00", "2026-10-19T16:55:04.968Z"). */
    public static Instant instant(JsonNode node) {
        String text = text(node);
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** The calendar date at the start of an ISO-8601 value ("2026-10-14T00:00:00" is 2026-10-14). */
    public static LocalDate date(JsonNode node) {
        String text = text(node);
        if (text == null || text.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(text.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** The non-null results of {@code read} over an array's elements; empty when the node is no array. */
    public static <T> List<T> each(JsonNode array, Function<JsonNode, T> read) {
        List<T> values = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode element : array) {
                T value = read.apply(element);
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return values;
    }
}
