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

    private static final BlockStyle CENTRED = new BlockStyle(BlockStyle.Size.LARGE, BlockStyle.Align.CENTER, true, true, null, false, null);

    @Test
    void storesOnlyTheStylesOfTheTextBlocksThatDifferFromTheUsual() {
        Map<BodyBlock, BlockStyle> styles = Map.of(BodyBlock.NOTES, CENTRED, BodyBlock.SIGNATURE, BlockStyle.USUAL_SIGNATURE,
            BodyBlock.MENTIONS, BlockStyle.USUAL);
        assertThat(BlockStyle.store(styles)).isEqualTo("NOTES=LARGE,CENTER,FRAMED,SHADED");
        // Of the lines table, only its shaded rows and filled header are kept.
        assertThat(BlockStyle.store(Map.of(BodyBlock.LINES, CENTRED))).isEqualTo("LINES=SMALL,LEFT,FRAMED,SHADED");
        assertThat(BlockStyle.read("NOTES=LARGE,CENTER,FRAMED,SHADED;MENTIONS=NORMAL,RIGHT;GONE=SMALL")).containsOnly(
            Map.entry(BodyBlock.NOTES, CENTRED), Map.entry(BodyBlock.MENTIONS, new BlockStyle(BlockStyle.Size.NORMAL, BlockStyle.Align.RIGHT, false, false, null, false, null)));
        assertThat(BlockStyle.store(null)).isNull();
        assertThat(BlockStyle.read(null)).isEmpty();
        assertThat(new BlockStyle(null, null, false, false, null, false, null)).isEqualTo(BlockStyle.USUAL);
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

    @Test
    void writesABlockInTheAccentColourAndBold() throws IOException {
        BlockStyle accent = new BlockStyle(BlockStyle.Size.NORMAL, BlockStyle.Align.LEFT, false, false, BlockStyle.Color.ACCENT, true, null);
        assertThat(BlockStyle.store(Map.of(BodyBlock.MENTIONS, accent))).isEqualTo("MENTIONS=NORMAL,LEFT,ACCENT,BOLD");
        assertThat(BlockStyle.read("MENTIONS=NORMAL,LEFT,ACCENT,BOLD")).containsEntry(BodyBlock.MENTIONS, accent);
        assertThat(BlockStyle.read("NOTES=SMALL,LEFT,GREY").get(BodyBlock.NOTES).color()).isEqualTo(BlockStyle.Color.GREY);

        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Alice Martin");
        seller.setAccentColor("#1F4E79");
        seller.setBlockStyles(BlockStyle.store(Map.of(BodyBlock.NOTES, accent)));
        TextPosition notes = firstOf(new QuotePdfRenderer().render(quote(), seller, new InvoiceBuyer(), 30, null), "Thanks");
        assertThat(notes.getFont().getName()).containsIgnoringCase("bold");
        try (PDDocument document = Loader.loadPDF(new QuotePdfRenderer().render(quote(), seller, new InvoiceBuyer(), 30, null))) {
            String operators = new String(document.getPage(0).getContents().readAllBytes(), StandardCharsets.ISO_8859_1);
            int thanks = operators.lastIndexOf("rg");
            assertThat(operators).containsPattern("0\\.12\\d* 0\\.30\\d* 0\\.47\\d* rg");
            assertThat(thanks).isPositive();
        }
        String document = new String(BillingDocxTest.parts(new QuotePdfRenderer().renderDocx(quote(), seller, new InvoiceBuyer(), 30, null, false))
            .get("word/document.xml"), StandardCharsets.UTF_8);
        String run = document.substring(document.lastIndexOf("<w:r>", document.indexOf("Thanks for your trust")), document.indexOf("Thanks for your trust"));
        assertThat(run).contains("<w:b/>", "<w:color w:val=\"1F4E79\"/>");
    }

    @Test
    void writesABlockInTheOtherFontFamily() throws IOException {
        BlockStyle serif = new BlockStyle(BlockStyle.Size.SMALL, BlockStyle.Align.LEFT, false, false, null, false, BlockStyle.Family.SERIF);
        assertThat(BlockStyle.store(Map.of(BodyBlock.NOTES, serif))).isEqualTo("NOTES=SMALL,LEFT,SERIF");
        assertThat(BlockStyle.read("NOTES=SMALL,LEFT,SERIF")).containsEntry(BodyBlock.NOTES, serif);
        assertThat(BlockStyle.families("NOTES=SMALL,LEFT,SERIF;MENTIONS=NORMAL,LEFT")).containsExactly(BlockStyle.Family.SERIF);

        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Alice Martin");
        seller.setBlockStyles(BlockStyle.store(Map.of(BodyBlock.NOTES, serif)));
        byte[] pdf = new QuotePdfRenderer().render(quote(), seller, new InvoiceBuyer(), 30, null);
        assertThat(firstOf(pdf, "Thanks").getFont().getName()).contains("Serif");
        assertThat(firstOf(pdf, "Audit").getFont().getName()).doesNotContain("Serif");
        String document = new String(BillingDocxTest.parts(new QuotePdfRenderer().renderDocx(quote(), seller, new InvoiceBuyer(), 30, null, false))
            .get("word/document.xml"), StandardCharsets.UTF_8);
        String run = document.substring(document.lastIndexOf("<w:r>", document.indexOf("Thanks for your trust")), document.indexOf("Thanks for your trust"));
        assertThat(run).contains("w:ascii=\"Liberation Serif\"");
    }

    @Test
    void shadesTheLinesAndFillsTheirHeaderWhateverTheLayout() throws IOException {
        Quote quote = quote();
        quote.setLines(List.of(new InvoiceLine("Audit", BigDecimal.ONE, new BigDecimal("900.00"), new BigDecimal("20.00")),
            new InvoiceLine("Training", BigDecimal.ONE, new BigDecimal("400.00"), new BigDecimal("20.00"))));
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Alice Martin");
        seller.setAccentColor("#1F4E79");
        byte[] plain = new QuotePdfRenderer().render(quote, seller, new InvoiceBuyer(), 30, null);
        seller.setBlockStyles(BlockStyle.store(Map.of(BodyBlock.LINES,
            new BlockStyle(null, null, true, true, null, null, null))));
        byte[] styled = new QuotePdfRenderer().render(quote, seller, new InvoiceBuyer(), 30, null);
        try (PDDocument usual = Loader.loadPDF(plain); PDDocument document = Loader.loadPDF(styled)) {
            String before = new String(usual.getPage(0).getContents().readAllBytes(), StandardCharsets.ISO_8859_1);
            String after = new String(document.getPage(0).getContents().readAllBytes(), StandardCharsets.ISO_8859_1);
            // The header's band and the second line's shading added.
            assertThat(count(after, " re")).isEqualTo(count(before, " re") + 2);
            assertThat(after).contains("1 1 1 rg");
        }
        String document = new String(BillingDocxTest.parts(new QuotePdfRenderer().renderDocx(quote, seller, new InvoiceBuyer(), 30, null, false))
            .get("word/document.xml"), StandardCharsets.UTF_8);
        assertThat(document).contains("w:fill=\"1F4E79\"", "w:fill=\"EDEDED\"");
    }

    @Test
    void drawsTheSignatureBoxTallerAndOnTheLeft() throws IOException {
        assertThat(BlockStyle.of(Map.of(), BodyBlock.SIGNATURE)).isEqualTo(BlockStyle.USUAL_SIGNATURE);
        BlockStyle tall = new BlockStyle(BlockStyle.Size.LARGE, BlockStyle.Align.LEFT, true, true, BlockStyle.Color.GREY, true, null);
        assertThat(BlockStyle.store(Map.of(BodyBlock.SIGNATURE, tall))).isEqualTo("SIGNATURE=LARGE,LEFT");
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Alice Martin");
        float[] usual = signatureBox(new QuotePdfRenderer().render(quote(), seller, new InvoiceBuyer(), 30, null));
        seller.setBlockStyles(BlockStyle.store(Map.of(BodyBlock.SIGNATURE, tall)));
        float[] moved = signatureBox(new QuotePdfRenderer().render(quote(), seller, new InvoiceBuyer(), 30, null));
        // x, width and height of the last rectangle drawn: twice as tall, as wide, from the left margin rather than the middle.
        assertThat(moved[2]).isEqualTo(usual[2]);
        assertThat(moved[3]).isEqualTo(usual[3] * 2);
        assertThat(moved[0]).isLessThan(usual[0] - 200);
        String document = new String(BillingDocxTest.parts(new QuotePdfRenderer().renderDocx(quote(), seller, new InvoiceBuyer(), 30, null, false))
            .get("word/document.xml"), StandardCharsets.UTF_8);
        String table = document.substring(document.lastIndexOf("<w:tbl>"));
        assertThat(table.indexOf("<w:p>")).isLessThan(table.indexOf("<w:p/>"));
    }

    private static float[] signatureBox(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String operators = new String(document.getPage(0).getContents().readAllBytes(), StandardCharsets.ISO_8859_1);
            java.util.regex.Matcher rect = java.util.regex.Pattern.compile("([\\d.-]+) ([\\d.-]+) ([\\d.-]+) ([\\d.-]+) re").matcher(operators);
            float[] last = null;
            while (rect.find()) {
                last = new float[] {Float.parseFloat(rect.group(1)), Float.parseFloat(rect.group(2)), Float.parseFloat(rect.group(3)),
                    Float.parseFloat(rect.group(4))};
            }
            return new float[] {last[0], last[1], last[2], last[3]};
        }
    }
}
