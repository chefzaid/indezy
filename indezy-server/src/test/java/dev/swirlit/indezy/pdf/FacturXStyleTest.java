package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.DocumentFontFile;
import dev.swirlit.indezy.model.DocumentLogo;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.DocumentFont;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.model.enums.LogoPlacement;
import dev.swirlit.indezy.model.enums.LogoSize;
import dev.swirlit.indezy.repository.DocumentFontFileRepository;
import dev.swirlit.indezy.repository.DocumentLogoRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An issued invoice stays a Factur-X file whatever its style: a translucent logo, an accent colour, a header line, a
 * footer, several pages and each font, the uploaded one too. Every font it draws with is embedded, it declares itself
 * PDF/A-3B, and its {@code factur-x.xml} is written from the invoice's data. (The rest of PDF/A and EN 16931 is the Mustang
 * validator's to check, run by hand: it is not a dependency of the build.)
 */
class FacturXStyleTest {

    private static InvoicePdfRenderer renderer() throws IOException {
        BufferedImage picture = new BufferedImage(300, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = picture.createGraphics();
        graphics.setColor(new Color(30, 80, 120, 160));
        graphics.fillOval(0, 0, 300, 100);
        graphics.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(picture, "png", png);
        DocumentLogo logo = new DocumentLogo();
        logo.setContent(png.toByteArray());
        DocumentLogoRepository logos = Mockito.mock(DocumentLogoRepository.class);
        Mockito.when(logos.findById(5L)).thenReturn(Optional.of(logo));
        DocumentFontFile font = new DocumentFontFile();
        font.setFamily("Liberation Serif");
        font.setRegular(FontCheckTest.serif());
        font.setBold(FontCheckTest.serifBold());
        DocumentFontFileRepository fonts = Mockito.mock(DocumentFontFileRepository.class);
        Mockito.when(fonts.findById(8L)).thenReturn(Optional.of(font));
        InvoicePdfRenderer renderer = new InvoicePdfRenderer();
        renderer.setLogos(logos);
        renderer.setFonts(fonts);
        return renderer;
    }

    private static InvoiceSeller seller(DocumentFont font) {
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Martin Conseil");
        seller.setSiret("55210055400012");
        seller.setVatNumber("FR40552100554");
        seller.setVatExempt(false);
        seller.setAddress("18 rue du Sentier\n75002 Paris");
        seller.setIban("FR7630006000011234567890189");
        seller.setLogoId(5L);
        seller.setLogoPlacement(LogoPlacement.CENTER);
        seller.setLogoSize(LogoSize.LARGE);
        seller.setAccentColor("#1F4E79");
        seller.setHeader("Conseil Java et Kafka");
        seller.setFooter("alice.dev · 06 12 34 56 78");
        seller.setFont(font);
        seller.setFontFileId(8L);
        seller.setFontFamily("Liberation Serif");
        return seller;
    }

    private static Invoice invoice() {
        Invoice invoice = new Invoice();
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setLanguage(DocumentLanguage.FR);
        invoice.setNumber("F2026-0007");
        invoice.setIssueDate(LocalDate.of(2026, 11, 1));
        invoice.setDueDate(LocalDate.of(2026, 12, 1));
        List<InvoiceLine> lines = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            lines.add(new InvoiceLine("Développement Java, lot " + index, new BigDecimal("1.5"), new BigDecimal("650.00"), new BigDecimal("20.00")));
        }
        invoice.setLines(lines);
        return invoice;
    }

    @Test
    void staysAFacturXFileWhateverItsStyle() throws IOException {
        InvoicePdfRenderer renderer = renderer();
        InvoiceBuyer buyer = new InvoiceBuyer();
        buyer.setName("Société Générale");
        for (DocumentFont font : DocumentFont.values()) {
          for (dev.swirlit.indezy.model.enums.DocumentTheme theme : dev.swirlit.indezy.model.enums.DocumentTheme.values()) {
            InvoiceSeller seller = seller(font);
            seller.setTheme(theme);
            try (PDDocument document = Loader.loadPDF(renderer.render(invoice(), seller, buyer))) {
                assertThat(document.getNumberOfPages()).as(font.name()).isGreaterThan(1);
                assertThat(document.isEncrypted()).isFalse();
                for (PDPage page : document.getPages()) {
                    PDResources resources = page.getResources();
                    for (COSName name : resources.getFontNames()) {
                        PDFont used = resources.getFont(name);
                        assertThat(used.isEmbedded()).as("%s embedded with %s", used.getName(), font).isTrue();
                    }
                }
                assertThat(document.getDocumentCatalog().getOutputIntents()).hasSize(1);
                String xmp = new String(document.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
                assertThat(xmp).contains("<pdfaid:part>3</pdfaid:part>", "<pdfaid:conformance>B</pdfaid:conformance>");
                PDComplexFileSpecification file = document.getDocumentCatalog().getNames().getEmbeddedFiles().getNames().get("factur-x.xml");
                String xml = new String(file.getEmbeddedFile().toByteArray(), StandardCharsets.UTF_8);
                // Written from the invoice's data: its number and seller, none of the style.
                assertThat(xml).contains("F2026-0007", "Martin Conseil").doesNotContain("Conseil Java et Kafka", "alice.dev");
            }
          }
        }
    }
}
