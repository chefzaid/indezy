package dev.swirlit.indezy.media;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkDocumentsTest {

    @Test
    void readsAPdfItsTextAndDrawsItsFirstPage() throws Exception {
        byte[] pdf = NetworkFixtures.pdf("Mission chez Banque Martin", 3);
        NetworkDocuments.Type type = NetworkDocuments.detect("cv.PDF", pdf);
        assertThat(type.contentType()).isEqualTo("application/pdf");
        assertThat(NetworkDocuments.text(type, pdf)).contains("Mission chez Banque Martin");
        NetworkDocuments.Preview preview = NetworkDocuments.preview(type, pdf).orElseThrow();
        assertThat(preview.pages()).isEqualTo(3);
        assertThat(preview.png()).isNotEmpty();
        // A PDF that cannot be read keeps no preview and no text.
        byte[] broken = "%PDF-1.7 broken".getBytes(StandardCharsets.US_ASCII);
        assertThat(NetworkDocuments.preview(NetworkDocuments.detect("x.pdf", broken), broken)).isEmpty();
        assertThat(NetworkDocuments.text(NetworkDocuments.PDF, broken)).isEmpty();
    }

    @Test
    void readsTheTextOfSlidesSpreadsheetsAndTextDocuments() throws Exception {
        byte[] pptx = NetworkFixtures.archive(Map.of("ppt/slides/slide1.xml", "<p:sld><a:t>Retour Kafka &amp; Spark</a:t></p:sld>",
            "ppt/presentation.xml", "<p:presentation>ignored</p:presentation>"));
        NetworkDocuments.Type slides = NetworkDocuments.detect("talk.pptx", pptx);
        assertThat(slides.extension()).isEqualTo("pptx");
        assertThat(NetworkDocuments.text(slides, pptx)).isEqualTo("Retour Kafka & Spark");
        assertThat(NetworkDocuments.preview(slides, pptx)).isEmpty();
        byte[] xlsx = NetworkFixtures.archive(Map.of("xl/sharedStrings.xml", "<sst><si><t>TJM</t></si><si><t>650</t></si></sst>"));
        assertThat(NetworkDocuments.text(NetworkDocuments.detect("rates.xlsx", xlsx), xlsx)).isEqualTo("TJM 650");
        byte[] docx = NetworkFixtures.archive(Map.of("word/document.xml", "<w:t>Contrat &lt;Martin&gt;</w:t>"));
        assertThat(NetworkDocuments.text(NetworkDocuments.detect("c.docx", docx), docx)).isEqualTo("Contrat <Martin>");
        byte[] ods = NetworkFixtures.archive(Map.of("content.xml", "<office:text>Planning</office:text>"));
        assertThat(NetworkDocuments.text(NetworkDocuments.detect("plan.ods", ods), ods)).isEqualTo("Planning");
        assertThat(NetworkDocuments.text(NetworkDocuments.detect("plan.ods", ods), "PK\3\4 broken".getBytes(StandardCharsets.ISO_8859_1)))
            .isEmpty();
    }

    @Test
    void refusesMacrosOlderFormatsAndOtherFiles() throws Exception {
        byte[] withMacros = NetworkFixtures.archive(Map.of("word/document.xml", "<w:t>x</w:t>", "word/vbaProject.bin", "macro"));
        assertThatThrownBy(() -> NetworkDocuments.detect("c.docx", withMacros)).hasMessageContaining("macros");
        byte[] odfMacros = NetworkFixtures.archive(Map.of("content.xml", "x", "Basic/Standard/Module1.xml", "macro"));
        assertThatThrownBy(() -> NetworkDocuments.detect("c.odt", odfMacros)).hasMessageContaining("macros");
        assertThatThrownBy(() -> NetworkDocuments.detect("rates.xlsm", NetworkFixtures.archive(Map.of("a", "b"))))
            .hasMessageContaining("as xlsx");
        assertThatThrownBy(() -> NetworkDocuments.detect("song.zip", NetworkFixtures.archive(Map.of("a", "b"))))
            .hasMessageContaining("Send a PDF");
        assertThatThrownBy(() -> NetworkDocuments.detect("old.doc", new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}))
            .hasMessageContaining("Send a PDF");
        assertThatThrownBy(() -> NetworkDocuments.detect(null, new byte[0])).hasMessageContaining("Send a PDF");
        assertThatThrownBy(() -> NetworkDocuments.detect("c.docx", "PK\3\4 broken".getBytes(StandardCharsets.ISO_8859_1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stopsAnArchiveThatUnpacksToTooMuch() throws Exception {
        Map<String, String> many = new LinkedHashMap<>();
        for (int i = 0; i <= NetworkDocuments.MAX_ENTRIES; i++) {
            many.put("part" + i, "");
        }
        assertThatThrownBy(() -> NetworkDocuments.detect("big.docx", NetworkFixtures.archive(many))).hasMessageContaining("too many");
        byte[] bomb = NetworkFixtures.archive(Map.of("word/document.xml", "a".repeat((int) NetworkDocuments.MAX_UNPACKED + 1)));
        assertThatThrownBy(() -> NetworkDocuments.detect("bomb.docx", bomb)).hasMessageContaining("too much");
    }
}
