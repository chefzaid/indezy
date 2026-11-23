package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.BlockStyle;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.Quote;
import dev.swirlit.indezy.model.enums.BodyBlock;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The mentions and the notes in their own size, alignment, frame and shading, in the PDF and the Word copy. */
class BlockStylesTest {

    private static final BlockStyle CENTRED = new BlockStyle(BlockStyle.Size.LARGE, BlockStyle.Align.CENTER, true, true);

    @Test
    void storesOnlyTheStylesOfTheTextBlocksThatDifferFromTheUsual() {
        Map<BodyBlock, BlockStyle> styles = Map.of(BodyBlock.NOTES, CENTRED, BodyBlock.LINES, CENTRED, BodyBlock.MENTIONS, BlockStyle.USUAL);
        assertThat(BlockStyle.store(styles)).isEqualTo("NOTES=LARGE,CENTER,FRAMED,SHADED");
        assertThat(BlockStyle.read("NOTES=LARGE,CENTER,FRAMED,SHADED;MENTIONS=NORMAL,RIGHT;GONE=SMALL")).containsOnly(
            Map.entry(BodyBlock.NOTES, CENTRED), Map.entry(BodyBlock.MENTIONS, new BlockStyle(BlockStyle.Size.NORMAL, BlockStyle.Align.RIGHT, false, false)));
        assertThat(BlockStyle.store(null)).isNull();
        assertThat(BlockStyle.read(null)).isEmpty();
        assertThat(new BlockStyle(null, null, false, false)).isEqualTo(BlockStyle.USUAL);
    }

    @Test
    void drawsTheNotesCentredLargeFramedAndShaded() throws IOException {
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Alice Martin");
        byte[] usual = new QuotePdfRenderer().render(quote(), seller, new InvoiceBuyer(), 30, null);
        seller.setBlockStyles(BlockStyle.store(Map.of(BodyBlock.NOTES, CENTRED)));
        byte[] styled = new QuotePdfRenderer().render(quote(), seller, new InvoiceBuyer(), 30, null);
        TextPosition plain = firstOf(usual, "Thanks");
        TextPosition centred = firstOf(styled, "Thanks");
        assertThat(plain.getFontSizeInPt()).isEqualTo(8f);
        assertThat(centred.getFontSizeInPt()).isEqualTo(11f);
        // Centred on an A4 page, its first letter well right of the left margin.
        assertThat(centred.getXDirAdj()).isGreaterThan(plain.getXDirAdj() + 150);
        try (PDDocument document = Loader.loadPDF(styled)) {
            String operators = new String(document.getPage(0).getContents().readAllBytes(), StandardCharsets.ISO_8859_1);
            try (PDDocument plainDocument = Loader.loadPDF(usual)) {
                String plainOperators = new String(plainDocument.getPage(0).getContents().readAllBytes(), StandardCharsets.ISO_8859_1);
                assertThat(count(operators, " re")).isGreaterThan(count(plainOperators, " re") + 1);
            }
        }
    }

    @Test
    void writesTheStyledNotesInAFramedShadedCellOfTheWordCopy() throws IOException {
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Alice Martin");
        seller.setBlockStyles(BlockStyle.store(Map.of(BodyBlock.NOTES, CENTRED)));
        String document = new String(BillingDocxTest.parts(new QuotePdfRenderer().renderDocx(quote(), seller, new InvoiceBuyer(), 30, null, false))
            .get("word/document.xml"), StandardCharsets.UTF_8);
        int notes = document.indexOf("Thanks for your trust");
        assertThat(notes).isPositive();
        String before = document.substring(0, notes);
        assertThat(before.substring(before.lastIndexOf("<w:tbl>"))).contains("<w:tblBorders>", "w:fill=\"EDEDED\"", "<w:jc w:val=\"center\"/>");
    }

    private static Quote quote() {
        Quote quote = new Quote();
        quote.setLanguage(DocumentLanguage.EN);
        quote.setNotes("Thanks for your trust");
        quote.setLines(List.of(new InvoiceLine("Audit", BigDecimal.ONE, new BigDecimal("900.00"), new BigDecimal("20.00"))));
        return quote;
    }

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    private static TextPosition firstOf(byte[] pdf, String word) throws IOException {
        List<TextPosition> found = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) {
                if (found.isEmpty() && text.startsWith(word)) {
                    found.add(positions.getFirst());
                }
            }
        };
        try (PDDocument document = Loader.loadPDF(pdf)) {
            stripper.getText(document);
        }
        assertThat(found).as(word).isNotEmpty();
        return found.getFirst();
    }
}
