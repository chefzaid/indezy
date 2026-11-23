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
}
