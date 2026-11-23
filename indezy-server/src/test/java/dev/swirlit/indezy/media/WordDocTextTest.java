package dev.swirlit.indezy.media;

import dev.swirlit.indezy.exception.FieldRejectedException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class WordDocTextTest {

    /** A CV saved as Word 97–2003 by LibreOffice. */
    public static byte[] cv() throws IOException {
        try (InputStream input = Objects.requireNonNull(WordDocTextTest.class.getResourceAsStream("/documents/cv-alice.doc"))) {
            return input.readAllBytes();
        }
    }

    @Test
    void readsTheTextOfAnOldWordDocument() throws IOException {
        byte[] doc = cv();
        assertThat(WordDocText.is("CV-Alice.DOC", doc)).isTrue();
        assertThat(WordDocText.text(doc)).startsWith("Alice Martin\nLead Java freelance\nLyon").contains("Compétences : Java, Kafka, Spring Boot");
    }

    @Test
    void tellsWhatIsNotOne() throws IOException {
        byte[] doc = cv();
        assertThat(WordDocText.is("cv.docx", doc)).isFalse();
        assertThat(WordDocText.is(null, doc)).isFalse();
        assertThat(WordDocText.is("cv.doc", "%PDF-1.7".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(WordDocText.is("cv.doc", new byte[] {1})).isFalse();
        byte[] broken = Arrays.copyOf(doc, 600);
        assertThat(WordDocText.is("cv.doc", broken)).isTrue();
        assertThatThrownBy(() -> WordDocText.text(broken)).isInstanceOf(FieldRejectedException.class).hasMessageContaining("cannot be read");
    }
}
