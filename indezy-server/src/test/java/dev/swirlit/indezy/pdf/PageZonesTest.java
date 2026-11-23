package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The footer's three zones on every page and the header of the following pages, placeholders filled, in the PDF and the Word copy. */
class PageZonesTest {

    private static Invoice invoice() {
        Invoice invoice = new Invoice();
        invoice.setLanguage(DocumentLanguage.EN);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setNumber("F2026-0042");
        invoice.setIssueDate(LocalDate.of(2026, 11, 2));
        invoice.setDueDate(LocalDate.of(2026, 12, 2));
        invoice.setLines(Collections.nCopies(60, new InvoiceLine("Development", BigDecimal.ONE, new BigDecimal("600.00"), new BigDecimal("20.00"))));
        return invoice;
    }

    private static InvoiceSeller seller() {
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Alice Martin");
        seller.setSiret("55210055400012");
        seller.setFooter("alice.dev");
        seller.setFooterCenter("SIRET {{seller.siret}}");
        seller.setFooterRight("Page {{page}} of {{pages}}");
        seller.setPageHeaderLeft("{{seller.name}}");
        seller.setPageHeaderRight("{{document.title}} {{document.number}}");
        return seller;
    }

    @Test
    void writesTheZonesOnEveryPageAndTheHeaderFromTheSecond() throws IOException {
        try (PDDocument document = Loader.loadPDF(new InvoicePdfRenderer().render(invoice(), seller(), new InvoiceBuyer()))) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            int pages = document.getNumberOfPages();
            String first = page(document, 1);
            assertThat(first).contains("alice.dev", "SIRET 55210055400012", "Page 1 of " + pages).doesNotContain("Invoice F2026-0042 ")
                .doesNotContain("1/" + pages);
            assertThat(page(document, 2)).contains("Alice Martin", "Invoice F2026-0042", "Page 2 of " + pages);
        }
    }

    @Test
    void keepsThePageNumberOnTheRightWithoutARightZone() throws IOException {
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Alice Martin");
        try (PDDocument document = Loader.loadPDF(new InvoicePdfRenderer().render(invoice(), seller, new InvoiceBuyer()))) {
            assertThat(page(document, 1)).contains("1/" + document.getNumberOfPages());
        }
    }

    @Test
    void writesTheZonesInTheWordCopyWithItsPageFields() throws IOException {
        Map<String, byte[]> parts = BillingDocxTest.parts(new InvoicePdfRenderer().renderDocx(invoice(), seller(), new InvoiceBuyer()));
        String footer = new String(parts.get("word/footer1.xml"), StandardCharsets.UTF_8);
        assertThat(footer).contains("alice.dev", "SIRET 55210055400012", "Page ", "w:instr=\"PAGE\"", "w:instr=\"NUMPAGES\"");
        assertThat(new String(parts.get("word/header1.xml"), StandardCharsets.UTF_8)).contains("Alice Martin", "Invoice F2026-0042");
        assertThat(new String(parts.get("word/document.xml"), StandardCharsets.UTF_8)).contains("<w:titlePg/>", "w:type=\"first\"");
        assertThat(new String(parts.get("[Content_Types].xml"), StandardCharsets.UTF_8)).contains("/word/header1.xml");

        Map<String, byte[]> plain = BillingDocxTest.parts(new InvoicePdfRenderer().renderDocx(invoice(), new InvoiceSeller(), new InvoiceBuyer()));
        assertThat(plain).doesNotContainKey("word/header1.xml");
        assertThat(new String(plain.get("word/footer1.xml"), StandardCharsets.UTF_8)).contains("w:instr=\"PAGE\"");
    }

    private static String page(PDDocument document, int number) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(number);
        stripper.setEndPage(number);
        return stripper.getText(document).replaceAll("\\s+", " ");
    }
}
