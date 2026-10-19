package dev.swirlit.indezy.scraping;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonTest {

    @Test
    void readsValuesLeniently() throws IOException {
        JsonNode node = Json.parse("""
            {"text": "  Paris ", "blank": " ", "number": 550, "numeric": "600.5", "comma": "12,5", "bad": "n/a",
             "object": {"a": 1}, "nothing": null, "at": "2026-10-19T18:48:39+02:00", "day": "2026-10-14T00:00:00",
             "short": "2026", "list": ["a", null, "b"]}
            """);

        assertThat(Json.text(node.path("text"))).isEqualTo("Paris");
        assertThat(Json.text(node.path("blank"))).isNull();
        assertThat(Json.text(node.path("object"))).isNull();
        assertThat(Json.text(node.path("nothing"))).isNull();
        assertThat(Json.text(node.path("missing"))).isNull();
        assertThat(Json.integer(node.path("number"))).isEqualTo(550);
        assertThat(Json.integer(node.path("numeric"))).isEqualTo(601);
        assertThat(Json.decimal(node.path("comma"))).isEqualTo(12.5);
        assertThat(Json.decimal(node.path("bad"))).isNull();
        assertThat(Json.integer(node.path("missing"))).isNull();
        assertThat(Json.instant(node.path("at"))).isEqualTo(Instant.parse("2026-10-19T16:48:39Z"));
        assertThat(Json.instant(node.path("day"))).isNull();
        assertThat(Json.instant(node.path("missing"))).isNull();
        assertThat(Json.date(node.path("day"))).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(Json.date(node.path("short"))).isNull();
        assertThat(Json.date(node.path("bad"))).isNull();
        assertThat(Json.each(node.path("list"), Json::text)).containsExactly("a", "b");
        assertThat(Json.each(node.path("text"), Json::text)).isEmpty();
    }

    @Test
    void reportsUnreadableJson() {
        assertThatThrownBy(() -> Json.parse("{not json")).isInstanceOf(IOException.class).hasMessageStartingWith("Unreadable JSON");
    }
}
