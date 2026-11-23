package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.Quote;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DocumentTemplateKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class StarterTemplateTest {

    @Test
    void writesAnInvoiceTemplateWithEveryMentionsPlaceholderThatFillsWhole() throws IOException {
        InvoiceSeller seller = DocxTemplateTest.seller();
        seller.setAccentColor("#1F4E79");
        seller.setFooter("alice.dev");
        byte[] starter = StarterTemplate.write(DocumentTemplateKind.INVOICE, DocumentLanguage.FR, seller);

        DocxTemplate.Analysis analysis = DocxTemplate.analyse(starter);
        assertThat(analysis.unknown()).isEmpty();
        assertThat(analysis.missing()).isEmpty();
        assertThat(analysis.found()).contains("document.number", "seller.details", "client.details", "#lines", "line.amount", "/lines",
            "totals.toPay", "mentions", "logo", "^vatExempt");

        java.util.Map<String, byte[]> parts = BillingDocxTest.parts(DocxTemplate.fill(starter,
            new InvoicePage(null, DocxTemplateTest.invoice(), seller, DocxTemplateTest.buyer()), null));
        assertThat(BillingDocxTest.text(parts)).contains("Facture", "N° F2026-0007", "Martin Conseil", "Société Générale & Fils", "Développement Java",
            "Frais <déplacement>", "TVA : ", "Net à payer : 14 574,00 €", "Pas d'escompte").doesNotContain("{{", "}}");
        // The seller's footer, on every page.
        assertThat(parts.entrySet()).anySatisfy(part -> assertThat(new String(part.getValue(), java.nio.charset.StandardCharsets.UTF_8))
            .contains("alice.dev"));
    }

    @Test
    void writesAQuoteTemplateInEnglishWithItsSignatureBox() throws IOException {
        byte[] starter = StarterTemplate.write(DocumentTemplateKind.QUOTE, DocumentLanguage.EN, DocxTemplateTest.seller());
        assertThat(DocxTemplate.analyse(starter).found()).contains("document.validUntil", "acceptance");
        Quote quote = new Quote();
        quote.setLanguage(DocumentLanguage.EN);
        quote.setLines(DocxTemplateTest.invoice().getLines());
        String text = BillingDocxTest.text(BillingDocxTest.parts(DocxTemplate.fill(starter,
            new QuotePage(null, quote, DocxTemplateTest.seller(), DocxTemplateTest.buyer(), 30, null, false), null)));
        assertThat(text).contains("Valid until: ", "Description", "Amount due: ", "Développement Java").doesNotContain("{{");
    }

    @Test
    void followsTheBodysOrderAndLeavesOutAnUnwantedSignatureBox() throws IOException {
        InvoiceSeller seller = DocxTemplateTest.seller();
        seller.setBodyBlocks("NOTES,LINES,MENTIONS");
        byte[] starter = StarterTemplate.write(DocumentTemplateKind.QUOTE, DocumentLanguage.EN, seller);
        DocxTemplate.Analysis analysis = DocxTemplate.analyse(starter);
        assertThat(analysis.found()).doesNotContain("acceptance");
        assertThat(analysis.missing()).isEmpty();
        String document = new String(BillingDocxTest.parts(starter).get("word/document.xml"), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(document.indexOf("{{notes}}")).isNotNegative().isLessThan(document.indexOf("{{#lines}}"));
        assertThat(document.indexOf("{{totals.toPay}}")).isLessThan(document.indexOf("{{mentions}}"));
    }

    @Test
    void writesEachBuiltInLayoutWithItsPlaceholdersFound() throws IOException {
        for (dev.swirlit.indezy.model.enums.DocumentTheme theme : dev.swirlit.indezy.model.enums.DocumentTheme.values()) {
            InvoiceSeller seller = DocxTemplateTest.seller();
            seller.setAccentColor("#1F4E79");
            byte[] starter = StarterTemplate.write(DocumentTemplateKind.INVOICE, DocumentLanguage.FR, seller, theme);
            DocxTemplate.Analysis analysis = DocxTemplate.analyse(starter);
            assertThat(analysis.unknown()).as(theme.name()).isEmpty();
            assertThat(analysis.missing()).as(theme.name()).isEmpty();
            String document = new String(BillingDocxTest.parts(starter).get("word/document.xml"), java.nio.charset.StandardCharsets.UTF_8);
            String styles = new String(BillingDocxTest.parts(starter).get("word/styles.xml"), java.nio.charset.StandardCharsets.UTF_8);
            switch (theme) {
                // The band, the table header and the amount due filled with the accent colour, the words on it in white.
                case MODERN -> assertThat(document).contains("w:fill=\"1F4E79\"", "<w:color w:val=\"FFFFFF\"/>");
                // A serif font, a rule under the heading and the mentions framed.
                case CLASSIC -> {
                    assertThat(styles).contains("Liberation Serif");
                    assertThat(document).contains("<w:pBdr>", "<w:tblBorders>").doesNotContain("w:fill=");
                }
                default -> assertThat(document).doesNotContain("w:fill=", "<w:pBdr>");
            }
            String filled = BillingDocxTest.text(BillingDocxTest.parts(DocxTemplate.fill(starter,
                new InvoicePage(null, DocxTemplateTest.invoice(), seller, DocxTemplateTest.buyer()), null)));
            assertThat(filled).as(theme.name()).contains("Facture", "N° F2026-0007", "Net à payer : 14 574,00 €").doesNotContain("{{");
        }
    }
}
