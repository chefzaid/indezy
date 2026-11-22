package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.exception.FieldRejectedException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PostingPdfTextTest {

    public static byte[] pdf(boolean protect, String... lines) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (lines.length > 0) {
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    for (String line : lines) {
                        stream.showText(line);
                        stream.newLineAtOffset(0, -16);
                    }
                    stream.endText();
                }
            }
            if (protect) {
                document.protect(new StandardProtectionPolicy("owner", "user", new AccessPermission()));
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void readsAPostingsLines() throws IOException {
        PostingAiService.PdfText text = PostingAiService.pdfText(pdf(false, "Mission Java Senior", "TJM   650 euros", "Courbevoie"));
        assertThat(text.text()).isEqualTo("Mission Java Senior\nTJM 650 euros\nCourbevoie");
        assertThat(text.cut()).isFalse();
    }

    @Test
    void refusesWhatItCannotRead() throws IOException {
        assertThatThrownBy(() -> PostingAiService.pdfText(pdf(false))).isInstanceOf(FieldRejectedException.class)
            .hasMessageContaining("no text to read");
        assertThatThrownBy(() -> PostingAiService.pdfText("not a pdf".getBytes(StandardCharsets.UTF_8)))
            .hasMessageContaining("not a PDF that can be read");
        byte[] locked = pdf(true, "Secret");
        assertThatThrownBy(() -> PostingAiService.pdfText(locked)).hasMessageContaining("protected");
        assertThatThrownBy(() -> PostingAiService.pdfText(new byte[0])).hasMessageContaining("5 MB at most");
        assertThatThrownBy(() -> PostingAiService.pdfText(new byte[PostingAiService.PDF_MAX_BYTES + 1])).hasMessageContaining("5 MB at most");
    }
}
