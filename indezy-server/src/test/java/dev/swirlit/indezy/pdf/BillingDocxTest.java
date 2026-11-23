package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.DocumentLogo;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.Quote;
import dev.swirlit.indezy.model.enums.DocumentFont;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.model.enums.LegalForm;
import dev.swirlit.indezy.repository.DocumentLogoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class BillingDocxTest {

    /** The parts of a docx by name, each checked to be well-formed XML. */
    public static Map<String, byte[]> parts(byte[] docx) throws IOException {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                byte[] content = zip.readAllBytes();
                parts.put(entry.getName(), content);
                if (entry.getName().endsWith(".xml") || entry.getName().endsWith(".rels")) {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setNamespaceAware(true);
                    try {
                        factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
                    } catch (javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException e) {
                        throw new IOException(entry.getName() + " is not well-formed", e);
                    }
                }
            }
        }
        return parts;
    }

    /** The document's words, its runs joined. */
    public static String text(Map<String, byte[]> parts) {
        return new String(parts.get("word/document.xml"), StandardCharsets.UTF_8).replaceAll("<w:p[ >].*?", "\n").replaceAll("<[^>]+>", "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
    }

    private static InvoiceSeller seller() {
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Martin Conseil");
        seller.setLegalForm(LegalForm.SASU);
        seller.setShareCapital(1000);
        seller.setSiret("55210055400012");
        seller.setVatNumber("FR40552100554");
        seller.setVatExempt(false);
        seller.setAddress("18 rue du Sentier\n75002 Paris");
        seller.setIban("FR7630006000011234567890189");
        seller.setAccentColor("#1F4E79");
        seller.setFooter("Martin Conseil – SASU au capital de 1 000 €");
        return seller;
    }

    private static InvoiceBuyer buyer() {
        InvoiceBuyer buyer = new InvoiceBuyer();
        buyer.setName("Société Générale & Fils");
        buyer.setAddress("29 boulevard Haussmann\nParis");
        return buyer;
    }

    @Test
    void writesAnInvoiceLikeItsPdf() throws Exception {
        Invoice invoice = new Invoice();
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setLanguage(DocumentLanguage.FR);
        invoice.setNumber("F2026-0007");
        invoice.setIssueDate(LocalDate.of(2026, 12, 1));
        invoice.setDueDate(LocalDate.of(2026, 12, 31));
        invoice.setNotes("Bon de commande <4500012345>");
        invoice.setLines(List.of(new InvoiceLine("Développement Java", new BigDecimal("18.50"), new BigDecimal("650.00"), new BigDecimal("20.00"))));

        Map<String, byte[]> parts = parts(new InvoicePdfRenderer().renderDocx(invoice, seller(), buyer()));

        assertThat(parts).containsKeys("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/styles.xml", "word/footer1.xml",
            "docProps/core.xml");
        String text = text(parts);
        assertThat(text).contains("Facture", "N° F2026-0007", "Martin Conseil", "SIRET : 55210055400012", "Facturé à", "Société Générale & Fils",
            "Développement Java", "12 025,00 €", "14 430,00 €", "Bon de commande <4500012345>", "Date d'émission : 1 décembre 2026");
        String document = new String(parts.get("word/document.xml"), StandardCharsets.UTF_8);
        // The heading in the accent colour; no picture without a logo.
        assertThat(document).contains("<w:color w:val=\"1F4E79\"/>").doesNotContain("<w:drawing>");
        assertThat(new String(parts.get("word/footer1.xml"), StandardCharsets.UTF_8)).contains("SASU au capital de 1 000 €", "PAGE");
        assertThat(new String(parts.get("docProps/core.xml"), StandardCharsets.UTF_8)).contains("Facture F2026-0007");
        assertThat(new String(parts.get("word/styles.xml"), StandardCharsets.UTF_8)).contains("Liberation Sans");
    }

    @Test
    void writesAQuoteWithItsLogoSignatureBoxAndTerms() throws Exception {
        BufferedImage picture = new BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(picture, "png", png);
        DocumentLogo logo = new DocumentLogo();
        logo.setContent(png.toByteArray());
        DocumentLogoRepository logos = Mockito.mock(DocumentLogoRepository.class);
        Mockito.when(logos.findById(5L)).thenReturn(Optional.of(logo));
        QuotePdfRenderer renderer = new QuotePdfRenderer();
        renderer.setLogos(logos);
        InvoiceSeller seller = seller();
        seller.setLogoId(5L);
        seller.setFont(DocumentFont.SERIF);
        Quote quote = new Quote();
        quote.setLanguage(DocumentLanguage.EN);
        quote.setValidityDays(30);
        quote.setLines(List.of(new InvoiceLine("Audit", BigDecimal.ONE, new BigDecimal("900.00"), new BigDecimal("20.00"))));

        Map<String, byte[]> parts = parts(renderer.renderDocx(quote, seller, buyer(), 30, "Article 1\nObject.\n\nArticle 2\nPayment.", false));

        assertThat(parts).containsKey("word/media/logo.png");
        String text = text(parts);
        assertThat(text).contains("Quote", "Audit", "Article 1", "Payment.");
        String document = new String(parts.get("word/document.xml"), StandardCharsets.UTF_8);
        assertThat(document).contains("<w:drawing>", "r:embed=\"rIdLogo\"", "<w:br w:type=\"page\"/>", "<w:tblBorders>");
        assertThat(new String(parts.get("word/styles.xml"), StandardCharsets.UTF_8)).contains("Liberation Serif");
        assertThat(new String(parts.get("[Content_Types].xml"), StandardCharsets.UTF_8)).contains("image/png");
    }

    @Test
    void leavesOutWhatXmlCannotHold() throws IOException {
        assertThat(DocxDocument.escape("a\u0001b & <c> \"d\"")).isEqualTo("ab &amp; &lt;c&gt; &quot;d&quot;");
        assertThat(DocxDocument.escape(null)).isEmpty();
        // A picture that cannot be read is left out, the document still written.
        DocxDocument docx = new DocxDocument("Liberation Sans", (String) null);
        docx.picture(new byte[] {1, 2, 3}, 100, 40, DocxDocument.Align.LEFT);
        docx.paragraph("Bonjour", false, DocxDocument.BODY, DocxDocument.Align.CENTER);
        assertThat(new String(docx.save("t", "a"), StandardCharsets.ISO_8859_1)).doesNotContain("media/logo");
    }

    @Test
    void printsInTheUploadedFontWhenChosen() throws IOException {
        dev.swirlit.indezy.model.DocumentFontFile font = new dev.swirlit.indezy.model.DocumentFontFile();
        font.setFamily("Liberation Serif");
        font.setRegular(FontCheckTest.serif());
        dev.swirlit.indezy.repository.DocumentFontFileRepository fonts = Mockito.mock(dev.swirlit.indezy.repository.DocumentFontFileRepository.class);
        Mockito.when(fonts.findById(8L)).thenReturn(Optional.of(font));
        InvoicePdfRenderer renderer = new InvoicePdfRenderer();
        renderer.setFonts(fonts);
        InvoiceSeller seller = seller();
        seller.setFont(DocumentFont.CUSTOM);
        seller.setFontFileId(8L);
        seller.setFontFamily("Liberation Serif");
        Invoice invoice = new Invoice();
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setLanguage(DocumentLanguage.FR);
        invoice.setLines(List.of(new InvoiceLine("Audit", BigDecimal.ONE, new BigDecimal("900.00"), new BigDecimal("20.00"))));
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(renderer.render(invoice, seller, buyer()))) {
            java.util.Set<String> names = new java.util.HashSet<>();
            org.apache.pdfbox.pdmodel.PDResources resources = document.getPage(0).getResources();
            for (org.apache.pdfbox.cos.COSName name : resources.getFontNames()) {
                names.add(resources.getFont(name).getName());
            }
            // The regular face prints the bold text too, the file having no bold one.
            assertThat(names).isNotEmpty().allMatch(name -> name.contains("LiberationSerif"));
        }
        assertThat(new String(parts(renderer.renderDocx(invoice, seller, buyer())).get("word/styles.xml"), StandardCharsets.UTF_8))
            .contains("Liberation Serif");

        // The file gone, the document prints in the sans-serif font.
        seller.setFontFileId(9L);
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(renderer.render(invoice, seller, buyer()))) {
            org.apache.pdfbox.pdmodel.PDResources resources = document.getPage(0).getResources();
            assertThat(resources.getFont(resources.getFontNames().iterator().next()).getName()).contains("LiberationSans");
        }
    }
}
