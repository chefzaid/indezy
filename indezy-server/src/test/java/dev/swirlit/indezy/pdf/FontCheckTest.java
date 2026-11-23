package dev.swirlit.indezy.pdf;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FontCheckTest {

    /** A bundled TrueType font, as if uploaded. */
    public static byte[] serif() {
        return PdfFonts.read(PdfFonts.SERIF_REGULAR);
    }

    public static byte[] serifBold() {
        return PdfFonts.read(PdfFonts.SERIF_BOLD);
    }

    /** The font with its OS/2 table's embedding flags set to {@code fsType}. */
    static byte[] withFsType(byte[] font, int fsType) {
        byte[] copy = font.clone();
        ByteBuffer buffer = ByteBuffer.wrap(copy);
        int tables = buffer.getShort(4) & 0xFFFF;
        for (int index = 0; index < tables; index++) {
            int record = 12 + index * 16;
            String tag = new String(copy, record, 4, StandardCharsets.US_ASCII);
            if (tag.equals("OS/2")) {
                int offset = buffer.getInt(record + 8);
                buffer.putShort(offset + 8, (short) fsType);
                return copy;
            }
        }
        throw new IllegalStateException("No OS/2 table");
    }

    @Test
    void acceptsAnEmbeddableTrueTypeFontAndNamesItsFamily() {
        assertThat(FontCheck.family(serif())).isEqualTo("Liberation Serif");
        // Preview and print, or editable: both may be embedded.
        assertThat(FontCheck.family(withFsType(serif(), 0x0004))).isEqualTo("Liberation Serif");
        assertThat(FontCheck.family(withFsType(serif(), 0x0008))).isEqualTo("Liberation Serif");
    }

    @Test
    void refusesWhatCannotBeEmbedded() {
        assertThatThrownBy(() -> FontCheck.family(withFsType(serif(), 0x0002))).hasMessageContaining("licence does not allow embedding");
        assertThatThrownBy(() -> FontCheck.family(withFsType(serif(), 0x0200))).hasMessageContaining("licence does not allow embedding");
        byte[] cff = serif();
        cff[0] = 'O';
        cff[1] = 'T';
        cff[2] = 'T';
        cff[3] = 'O';
        assertThatThrownBy(() -> FontCheck.family(cff)).hasMessageContaining("CFF outlines");
        assertThatThrownBy(() -> FontCheck.family("not a font".getBytes(StandardCharsets.UTF_8))).hasMessageContaining("not a font");
    }
}
