package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.Quote;
import dev.swirlit.indezy.model.enums.BodyBlock;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static dev.swirlit.indezy.model.enums.BodyBlock.LINES;
import static dev.swirlit.indezy.model.enums.BodyBlock.MENTIONS;
import static dev.swirlit.indezy.model.enums.BodyBlock.NOTES;
import static dev.swirlit.indezy.model.enums.BodyBlock.SIGNATURE;
import static org.assertj.core.api.Assertions.assertThat;

/** The body's blocks print in the workspace's order, a quote's signature box only when kept, in the PDF and the Word copy. */
class BodyBlocksTest {

    private static final String ACCEPTANCE = "signature and stamp";
    private static final String MENTION = "No discount for early payment.";

    @Test
    void keepsTheRequiredBlocksWhateverTheOrderGiven() {
        assertThat(BodyBlock.normalize(null)).isEqualTo(BodyBlock.USUAL);
        assertThat(BodyBlock.normalize(Arrays.asList(NOTES, null, NOTES, SIGNATURE))).containsExactly(LINES, MENTIONS, NOTES, SIGNATURE);
        assertThat(BodyBlock.normalize(List.of(MENTIONS))).containsExactly(LINES, MENTIONS, NOTES);
        assertThat(BodyBlock.normalize(List.of(NOTES, LINES))).containsExactly(NOTES, LINES, MENTIONS);
        assertThat(BodyBlock.store(BodyBlock.USUAL)).isNull();
        assertThat(BodyBlock.store(List.of(LINES, NOTES, MENTIONS))).isEqualTo("LINES,NOTES,MENTIONS");
        assertThat(BodyBlock.read("LINES, notes,GONE,MENTIONS")).containsExactly(LINES, NOTES, MENTIONS);
        assertThat(BodyBlock.read(" ")).isEqualTo(BodyBlock.USUAL);
    }

    @Test
    void printsAQuotesBodyInTheChosenOrderWithoutItsSignatureBox() throws Exception {
        InvoiceSeller seller = seller("NOTES,LINES,MENTIONS");
        QuotePdfRenderer renderer = new QuotePdfRenderer();
        String pdf = text(renderer.render(quote(), seller, new InvoiceBuyer(), 30, null));
        assertThat(pdf.indexOf("Thanks for your trust")).isNotNegative().isLessThan(pdf.indexOf("Audit"));
        assertThat(pdf).doesNotContain(ACCEPTANCE);
        String docx = BillingDocxTest.text(BillingDocxTest.parts(renderer.renderDocx(quote(), seller, new InvoiceBuyer(), 30, null, false)));
        assertThat(docx.indexOf("Thanks for your trust")).isNotNegative().isLessThan(docx.indexOf("Audit"));
        assertThat(docx).doesNotContain(ACCEPTANCE);

        String usual = text(renderer.render(quote(), seller(null), new InvoiceBuyer(), 30, null));
        assertThat(usual.indexOf("Audit")).isLessThan(usual.indexOf("Thanks for your trust"));
        assertThat(usual.indexOf("Thanks for your trust")).isLessThan(usual.indexOf(ACCEPTANCE));
    }

    @Test
    void printsAnInvoicesNotesBeforeItsMentionsWhenAsked() throws IOException {
        Invoice invoice = new Invoice();
        invoice.setLanguage(DocumentLanguage.EN);
        invoice.setNumber("F-2026-001");
        invoice.setIssueDate(LocalDate.of(2026, 11, 2));
        invoice.setDueDate(LocalDate.of(2026, 12, 2));
        invoice.setNotes("Thanks for your trust");
        invoice.setLines(List.of(new InvoiceLine("Audit", BigDecimal.ONE, new BigDecimal("900.00"), new BigDecimal("20.00"))));
        InvoicePdfRenderer renderer = new InvoicePdfRenderer();
        String usual = text(renderer.render(invoice, seller(null), new InvoiceBuyer()));
        assertThat(usual.indexOf(MENTION)).isNotNegative().isLessThan(usual.indexOf("Thanks for your trust"));
        String moved = text(renderer.render(invoice, seller("LINES,NOTES,MENTIONS,SIGNATURE"), new InvoiceBuyer()));
        assertThat(moved.indexOf("Thanks for your trust")).isNotNegative().isLessThan(moved.indexOf(MENTION));
    }

    private static Quote quote() {
        Quote quote = new Quote();
        quote.setLanguage(DocumentLanguage.EN);
        quote.setValidityDays(30);
        quote.setNotes("Thanks for your trust");
        quote.setLines(List.of(new InvoiceLine("Audit", BigDecimal.ONE, new BigDecimal("900.00"), new BigDecimal("20.00"))));
        return quote;
    }

    private static InvoiceSeller seller(String blocks) {
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Alice Martin");
        seller.setBodyBlocks(blocks);
        return seller;
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }
}
