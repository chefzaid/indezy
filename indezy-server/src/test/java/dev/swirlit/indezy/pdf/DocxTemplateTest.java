package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DocxTemplateTest {

    /** A template laid out in a word processor (LibreOffice), lacking the legal mentions. */
    public static byte[] template() throws IOException {
        try (InputStream input = Objects.requireNonNull(DocxTemplateTest.class.getResourceAsStream("/documents/modele-facture.docx"))) {
            return input.readAllBytes();
        }
    }

    static Invoice invoice() {
        Invoice invoice = new Invoice();
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setLanguage(DocumentLanguage.FR);
        invoice.setNumber("F2026-0007");
        invoice.setIssueDate(LocalDate.of(2026, 12, 1));
        invoice.setDueDate(LocalDate.of(2026, 12, 31));
        invoice.setLines(List.of(new InvoiceLine("Développement Java", new BigDecimal("18.50"), new BigDecimal("650.00"), new BigDecimal("20.00")),
            new InvoiceLine("Frais <déplacement>", BigDecimal.ONE, new BigDecimal("120.00"), new BigDecimal("20.00"))));
        return invoice;
    }

    static InvoiceSeller seller() {
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Martin Conseil");
        seller.setSiret("55210055400012");
        seller.setVatExempt(false);
        seller.setVatNumber("FR40552100554");
        seller.setAddress("18 rue du Sentier\n75002 Paris");
        return seller;
    }

    static InvoiceBuyer buyer() {
        InvoiceBuyer buyer = new InvoiceBuyer();
        buyer.setName("Société Générale & Fils");
        buyer.setAddress("29 boulevard Haussmann\nParis");
        return buyer;
    }

    @Test
    void readsTheTemplatesPlaceholdersAndTheMentionsItLacks() throws IOException {
        DocxTemplate.Analysis analysis = DocxTemplate.analyse(template());
        assertThat(analysis.found()).contains("invoice.number", "client.name", "#lines", "line.amount", "/lines", "totals.toPay", "logo");
        assertThat(analysis.unknown()).containsExactly("iban.inconnu");
        assertThat(analysis.missing()).containsExactly("mentions");
    }

    @Test
    void fillsTheTemplateRepeatingTheLinesAndAddingTheMissingMentions() throws IOException {
        InvoicePage page = new InvoicePage(null, invoice(), seller(), buyer());
        Map<String, byte[]> parts = BillingDocxTest.parts(DocxTemplate.fill(template(), page, null));
        String text = BillingDocxTest.text(parts);

        assertThat(text).contains("Facture F2026-0007", "1 décembre 2026", "31 décembre 2026", "Martin Conseil", "SIRET : 55210055400012",
            "Société Générale & Fils", "29 boulevard Haussmann", "Développement Java", "12 025,00 €", "Frais <déplacement>", "120,00 €",
            "14 574,00 €", "{{iban.inconnu}}");
        assertThat(text).doesNotContain("{{invoice.number}}", "{{#lines}}", "{{/lines}}", "{{line.description}}", "{{logo}}");
        // One table row per line.
        String document = new String(parts.get("word/document.xml"), StandardCharsets.UTF_8);
        assertThat(document.split("<w:tr[ >]", -1)).hasSize(4);
        // The mentions the template leaves out, added at its end.
        assertThat(text).contains("Pas d'escompte pour paiement anticipé", "40 €");
        assertThat(text.indexOf("Pas d'escompte")).isGreaterThan(text.indexOf("14 574,00 €"));
    }

    @Test
    void drawsTheLogoWhereTheTemplateAsks() throws IOException {
        java.awt.image.BufferedImage picture = new java.awt.image.BufferedImage(300, 100, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(picture, "png", png);
        InvoicePage page = new InvoicePage(null, invoice(), seller(), buyer());
        Map<String, byte[]> parts = BillingDocxTest.parts(DocxTemplate.fill(template(), page, png.toByteArray()));
        assertThat(parts).containsKey("word/media/indezy-logo.png");
        assertThat(new String(parts.get("word/document.xml"), StandardCharsets.UTF_8)).contains("<w:drawing>", "rIdIndezyLogo");
        assertThat(new String(parts.get("word/_rels/document.xml.rels"), StandardCharsets.UTF_8)).contains("media/indezy-logo.png");
        assertThat(new String(parts.get("[Content_Types].xml"), StandardCharsets.UTF_8)).contains("Extension=\"png\"");
    }

    @Test
    void readsAPlaceholderWordSplitAcrossRuns() {
        String paragraph = "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>N° {{inv</w:t></w:r><w:r><w:t>oice.num</w:t></w:r><w:r><w:t xml:space=\"preserve\">ber}} </w:t>" +
            "</w:r></w:p><w:p><w:r><w:t>Sans</w:t></w:r><w:r><w:t> rien</w:t></w:r></w:p>";
        String merged = DocxTemplate.merged(paragraph);
        assertThat(merged).contains("<w:b/></w:rPr><w:t xml:space=\"preserve\">N° {{invoice.number}} </w:t>")
            .contains("<w:t>Sans</w:t></w:r><w:r><w:t> rien</w:t>");
        assertThat(DocxTemplate.substitute(merged, Map.of("document.number", "F-1\nbis"))).contains("N° F-1</w:t><w:br/><w:t xml:space=\"preserve\">bis");
        assertThat(DocxTemplate.canonical("quote.validUntil")).isEqualTo("document.validUntil");
        // A break typed between the runs stays.
        assertThat(DocxTemplate.merged("<w:p><w:r><w:t>{{seller.name}}</w:t></w:r><w:r><w:br/></w:r><w:r><w:t>{{seller.details}}</w:t></w:r></w:p>"))
            .contains("{{seller.name}}</w:t><w:br/><w:t xml:space=\"preserve\">{{seller.details}}");
    }

    @Test
    void refusesWhatIsNotATemplate() {
        assertThatThrownBy(() -> DocxTemplate.analyse("hello".getBytes(StandardCharsets.UTF_8))).hasMessageContaining("not a Word document");
        assertThatThrownBy(() -> DocxTemplate.analyse(new DocxDocument("Liberation Sans", null).save("t", "a")))
            .hasMessageContaining("no placeholder");
    }

    @Test
    void showsAPartOnlyInItsCase() {
        String xml = "<w:p><w:r><w:t>{{#vatExempt}}TVA non applicable{{/vatExempt}}{{^vatExempt}}TVA {{seller.vatNumber}}{{/vatExempt}}</w:t></w:r></w:p>" +
            "<w:p><w:r><w:t>{{^vatExempt}}Taux</w:t></w:r></w:p><w:p><w:r><w:t>20 %{{/vatExempt}}</w:t></w:r></w:p>";
        assertThat(DocxTemplate.cases(xml, Map.of("vatExempt", true))).isEqualTo("<w:p><w:r><w:t>TVA non applicable</w:t></w:r></w:p>" +
            "<w:p><w:r><w:t></w:t></w:r></w:p><w:p><w:r><w:t></w:t></w:r></w:p>");
        assertThat(DocxTemplate.cases(xml, Map.of("vatExempt", false))).isEqualTo("<w:p><w:r><w:t>TVA {{seller.vatNumber}}</w:t></w:r></w:p>" +
            "<w:p><w:r><w:t>Taux</w:t></w:r></w:p><w:p><w:r><w:t>20 %</w:t></w:r></w:p>");
        // A case left open loses its marker only.
        assertThat(DocxTemplate.cases("<w:t>{{#vatExempt}}seul</w:t>", Map.of())).isEqualTo("<w:t>seul</w:t>");
    }
}
