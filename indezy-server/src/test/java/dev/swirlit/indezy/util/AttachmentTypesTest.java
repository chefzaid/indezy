package dev.swirlit.indezy.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentTypesTest {

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String type(String name, byte[] content) {
        return AttachmentTypes.detect(name, content).map(AttachmentTypes.Type::contentType).orElse(null);
    }

    @Test
    void recognisesPdfAndImagesFromTheirContentWhateverTheirName() {
        assertThat(type("brief.bin", bytes("%PDF-1.7\n..."))).isEqualTo("application/pdf");
        assertThat(type("x", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0})).isEqualTo("image/png");
        assertThat(type("x", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0})).isEqualTo("image/jpeg");
        assertThat(type("x", bytes("GIF89a...."))).isEqualTo("image/gif");
        assertThat(type("x", bytes("RIFF\0\0\0\0WEBPVP8 "))).isEqualTo("image/webp");
        assertThat(AttachmentTypes.detect("x.pdf", bytes("%PDF-")).orElseThrow().inline()).isTrue();
    }

    @Test
    void recognisesWordAndOpenDocumentArchivesByNameAndTextByContent() {
        byte[] zip = new byte[] {'P', 'K', 3, 4, 20, 0};
        assertThat(type("Contract.DOCX", zip)).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(type("contract.odt", zip)).isEqualTo("application/vnd.oasis.opendocument.text");
        assertThat(type("archive.zip", zip)).isNull();
        assertThat(type("notes.md", "Préparer l'entretien".getBytes(StandardCharsets.UTF_8))).isEqualTo("text/plain; charset=UTF-8");
        assertThat(AttachmentTypes.detect("notes.txt", bytes("x")).orElseThrow().inline()).isFalse();
    }

    @Test
    void refusesEverythingElse() {
        assertThat(type("page.html", bytes("<html><script>alert(1)</script>"))).isNull();
        assertThat(type("logo.svg", bytes("<svg xmlns=\"http://www.w3.org/2000/svg\"/>"))).isNull();
        assertThat(type("setup.exe", bytes("MZ\u0090\0"))).isNull();
        assertThat(type("fake.docx", bytes("not a zip"))).isNull();
        assertThat(type("binary.txt", new byte[] {'a', 0, 'b'})).isNull();
        assertThat(type("latin1.txt", new byte[] {(byte) 0xe9, ' ', 'x'})).isNull();
        assertThat(type("empty.pdf", new byte[0])).isNull();
        assertThat(type(null, bytes("RIFF\0\0\0\0WAVE"))).isNull();
    }

    @Test
    void showsOnlyPdfAndImagesInThePage() {
        assertThat(AttachmentTypes.isInline("application/pdf")).isTrue();
        assertThat(AttachmentTypes.isInline("image/webp")).isTrue();
        assertThat(AttachmentTypes.isInline("text/plain; charset=UTF-8")).isFalse();
        assertThat(AttachmentTypes.isInline("application/vnd.oasis.opendocument.text")).isFalse();
    }
}
