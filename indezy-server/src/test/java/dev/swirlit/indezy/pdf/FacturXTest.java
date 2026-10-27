package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceKind;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The Factur-X file of an issued invoice: a PDF/A-3 with the Cross Industry Invoice of the EN 16931 profile embedded. */
class FacturXTest {

    private static final Map<String, String> NAMESPACES = Map.of(
        "rsm", "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100",
        "ram", "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100",
        "udt", "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100",
        "qdt", "urn:un:unece:uncefact:data:standard:QualifiedDataType:100");

    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();

    @Test
    void embedsTheInvoiceInAPdfA3() throws Exception {
        byte[] pdf = renderer.render(invoice(), seller(false), buyer());

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getVersion()).isEqualTo(1.7f);
            assertThat(document.getDocumentCatalog().getOutputIntents()).singleElement()
                .satisfies(intent -> assertThat(intent.getOutputConditionIdentifier()).isEqualTo("sRGB IEC61966-2.1"));
            String xmp = new String(document.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
            assertThat(xmp).contains("<pdfaid:part>3</pdfaid:part>", "<pdfaid:conformance>B</pdfaid:conformance>",
                "<fx:DocumentType>INVOICE</fx:DocumentType>", "<fx:DocumentFileName>factur-x.xml</fx:DocumentFileName>",
                "<fx:ConformanceLevel>EN 16931</fx:ConformanceLevel>", "Factur-X PDFA Extension Schema", "Facture F2026-0007",
                "<pdf:Producer>Indezy</pdf:Producer>");
            assertThat(document.getDocumentInformation().getProducer()).isEqualTo("Indezy");
            COSArray associated = (COSArray) document.getDocumentCatalog().getCOSObject().getDictionaryObject(COSName.getPDFName("AF"));
            assertThat(associated.size()).isEqualTo(1);
            PDComplexFileSpecification file = embedded(document);
            assertThat(file.getCOSObject().getNameAsString(COSName.getPDFName("AFRelationship"))).isEqualTo("Alternative");
            assertThat(file.getEmbeddedFile().getSubtype()).isEqualTo("text/xml");
            assertThat(file.getEmbeddedFile().getModDate()).isNotNull();

            XPath xpath = xpath();
            Document xml = parse(file.getEmbeddedFile().toByteArray());
            assertThat(value(xpath, xml, "//rsm:ExchangedDocumentContext/ram:GuidelineSpecifiedDocumentContextParameter/ram:ID"))
                .isEqualTo("urn:cen.eu:en16931:2017");
            assertThat(value(xpath, xml, "//rsm:ExchangedDocument/ram:ID")).isEqualTo("F2026-0007");
            assertThat(value(xpath, xml, "//rsm:ExchangedDocument/ram:TypeCode")).isEqualTo("380");
            assertThat(value(xpath, xml, "//rsm:ExchangedDocument/ram:IssueDateTime/udt:DateTimeString")).isEqualTo("20261201");
            assertThat(value(xpath, xml, "//ram:IncludedNote/ram:Content")).isEqualTo("PO <4500012345> & co");
            assertThat(value(xpath, xml, "count(//ram:IncludedSupplyChainTradeLineItem)")).isEqualTo("2");
            assertThat(value(xpath, xml, "//ram:IncludedSupplyChainTradeLineItem[1]/ram:SpecifiedLineTradeDelivery/ram:BilledQuantity"))
                .isEqualTo("18.5");
            assertThat(value(xpath, xml, "//ram:IncludedSupplyChainTradeLineItem[1]//ram:LineTotalAmount")).isEqualTo("12025.00");
            assertThat(value(xpath, xml, "//ram:SellerTradeParty/ram:Name")).isEqualTo("Martin Conseil");
            assertThat(value(xpath, xml, "//ram:SellerTradeParty/ram:SpecifiedLegalOrganization/ram:ID")).isEqualTo("55210055400012");
            assertThat(value(xpath, xml, "//ram:SellerTradeParty/ram:PostalTradeAddress/ram:PostcodeCode")).isEqualTo("75002");
            assertThat(value(xpath, xml, "//ram:SellerTradeParty/ram:PostalTradeAddress/ram:CityName")).isEqualTo("Paris");
            assertThat(value(xpath, xml, "//ram:SellerTradeParty/ram:PostalTradeAddress/ram:LineOne")).isEqualTo("18 rue du Sentier, Bât. B");
            assertThat(value(xpath, xml, "//ram:SellerTradeParty/ram:SpecifiedTaxRegistration/ram:ID[@schemeID='VA']")).isEqualTo("FR40552100554");
            assertThat(value(xpath, xml, "//ram:BuyerTradeParty/ram:PostalTradeAddress/ram:CityName")).isEqualTo("Paris");
            assertThat(value(xpath, xml, "count(//ram:BuyerTradeParty/ram:PostalTradeAddress/ram:PostcodeCode)")).isEqualTo("0");
            assertThat(value(xpath, xml, "//ram:BuyerTradeParty/ram:SpecifiedLegalOrganization/ram:ID")).isEqualTo("552120222");
            assertThat(value(xpath, xml, "//ram:PayeePartyCreditorFinancialAccount/ram:IBANID")).isEqualTo("FR7630006000011234567890189");
            assertThat(value(xpath, xml, "//ram:PayeeSpecifiedCreditorFinancialInstitution/ram:BICID")).isEqualTo("BNPAFRPP");
            // The VAT per rate: 20 % on the services, 0 % (exempt) on the travel costs.
            assertThat(value(xpath, xml, "count(//ram:ApplicableHeaderTradeSettlement/ram:ApplicableTradeTax)")).isEqualTo("2");
            assertThat(value(xpath, xml, "//ram:ApplicableHeaderTradeSettlement/ram:ApplicableTradeTax[ram:CategoryCode='S']/ram:CalculatedAmount"))
                .isEqualTo("2405.00");
            assertThat(value(xpath, xml, "//ram:ApplicableHeaderTradeSettlement/ram:ApplicableTradeTax[ram:CategoryCode='E']/ram:BasisAmount"))
                .isEqualTo("120.40");
            assertThat(value(xpath, xml, "//ram:ApplicableHeaderTradeSettlement/ram:ApplicableTradeTax[ram:CategoryCode='E']/ram:ExemptionReason"))
                .isEqualTo("Exonération de TVA");
            assertThat(value(xpath, xml, "//ram:BillingSpecifiedPeriod/ram:StartDateTime/udt:DateTimeString")).isEqualTo("20261101");
            assertThat(value(xpath, xml, "//ram:SpecifiedTradePaymentTerms/ram:DueDateDateTime/udt:DateTimeString")).isEqualTo("20261231");
            assertThat(value(xpath, xml, "//ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:TaxBasisTotalAmount")).isEqualTo("12145.40");
            assertThat(value(xpath, xml, "//ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:TaxTotalAmount")).isEqualTo("2405.00");
            assertThat(value(xpath, xml, "//ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:GrandTotalAmount")).isEqualTo("14550.40");
            assertThat(value(xpath, xml, "//ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:DuePayableAmount")).isEqualTo("14550.40");
        }
    }

    @Test
    void statesACreditNoteOfAnExemptSellerAndTheInvoiceItCredits() throws Exception {
        Invoice note = invoice();
        note.setKind(InvoiceKind.CREDIT_NOTE);
        note.setNumber("F2026-0009");
        note.setDueDate(null);
        note.setNotes(null);
        note.setPeriodEnd(null);
        note.setLanguage(DocumentLanguage.EN);
        note.setCreditedNumber("F2026-0007");
        note.setCreditedIssueDate(LocalDate.of(2026, 12, 1));
        note.setLines(List.of(new InvoiceLine("Discount", BigDecimal.ONE, new BigDecimal("100"), BigDecimal.ZERO)));
        InvoiceSeller seller = seller(true);
        seller.setVatNumber(null);
        InvoiceBuyer buyer = new InvoiceBuyer();

        try (PDDocument document = Loader.loadPDF(renderer.render(note, seller, buyer))) {
            XPath xpath = xpath();
            Document xml = parse(embedded(document).getEmbeddedFile().toByteArray());
            assertThat(value(xpath, xml, "//rsm:ExchangedDocument/ram:TypeCode")).isEqualTo("381");
            assertThat(value(xpath, xml, "count(//ram:IncludedNote)")).isEqualTo("0");
            assertThat(value(xpath, xml, "//ram:InvoiceReferencedDocument/ram:IssuerAssignedID")).isEqualTo("F2026-0007");
            assertThat(value(xpath, xml, "//ram:InvoiceReferencedDocument/ram:FormattedIssueDateTime/qdt:DateTimeString")).isEqualTo("20261201");
            assertThat(value(xpath, xml, "//ram:ApplicableHeaderTradeSettlement/ram:ApplicableTradeTax/ram:ExemptionReasonCode"))
                .isEqualTo("VATEX-FR-FRANCHISE");
            assertThat(value(xpath, xml, "//ram:ApplicableHeaderTradeSettlement/ram:ApplicableTradeTax/ram:ExemptionReason"))
                .isEqualTo("TVA non applicable, art. 293 B du CGI");
            assertThat(value(xpath, xml, "count(//ram:SpecifiedTradeSettlementPaymentMeans)")).isEqualTo("0");
            assertThat(value(xpath, xml, "count(//ram:BillingSpecifiedPeriod)")).isEqualTo("0");
            assertThat(value(xpath, xml, "count(//ram:DueDateDateTime)")).isEqualTo("0");
            assertThat(value(xpath, xml, "//ram:SpecifiedTradePaymentTerms/ram:Description")).startsWith("Credit note");
            // Without a VAT number, the franchise seller's SIREN is its tax registration.
            assertThat(value(xpath, xml, "//ram:SellerTradeParty/ram:SpecifiedTaxRegistration/ram:ID[@schemeID='FC']")).isEqualTo("552100554");
            assertThat(value(xpath, xml, "count(//ram:SellerTradeParty/ram:SpecifiedTaxRegistration)")).isEqualTo("1");
            assertThat(value(xpath, xml, "//ram:BuyerTradeParty/ram:Name")).isEmpty();
            assertThat(value(xpath, xml, "count(//ram:BuyerTradeParty/ram:SpecifiedLegalOrganization)")).isEqualTo("0");
            assertThat(value(xpath, xml, "//ram:BuyerTradeParty/ram:PostalTradeAddress/ram:CountryID")).isEqualTo("FR");
            assertThat(value(xpath, xml, "//ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:GrandTotalAmount")).isEqualTo("100.00");
        }
    }

    @Test
    void leavesADraftAPlainPdf() throws Exception {
        Invoice draft = invoice();
        draft.setStatus(InvoiceStatus.DRAFT);
        draft.setNumber(null);
        try (PDDocument document = Loader.loadPDF(renderer.render(draft, seller(false), buyer()))) {
            assertThat(document.getDocumentCatalog().getNames()).isNull();
            assertThat(document.getDocumentCatalog().getMetadata()).isNull();
        }
    }

    @Test
    void escapesXmlTextAndDropsForbiddenCharacters() {
        assertThat(FacturXml.escape("a<b>&\"c'\u0001\td")).isEqualTo("a&lt;b&gt;&amp;&quot;c&apos;\td");
    }

    private static PDComplexFileSpecification embedded(PDDocument document) throws Exception {
        PDDocumentNameDictionary names = document.getDocumentCatalog().getNames();
        return names.getEmbeddedFiles().getNames().get("factur-x.xml");
    }

    private static Invoice invoice() {
        Invoice invoice = new Invoice();
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setLanguage(DocumentLanguage.FR);
        invoice.setNumber("F2026-0007");
        invoice.setIssueDate(LocalDate.of(2026, 12, 1));
        invoice.setDueDate(LocalDate.of(2026, 12, 31));
        invoice.setPeriodStart(LocalDate.of(2026, 11, 1));
        invoice.setPeriodEnd(LocalDate.of(2026, 11, 30));
        invoice.setNotes("PO <4500012345> & co");
        invoice.setLines(List.of(new InvoiceLine("Développement Java", new BigDecimal("18.50"), new BigDecimal("650.00"), new BigDecimal("20.00")),
            new InvoiceLine("Frais de déplacement", BigDecimal.ONE, new BigDecimal("120.40"), BigDecimal.ZERO)));
        return invoice;
    }

    private static InvoiceSeller seller(boolean exempt) {
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Martin Conseil");
        seller.setSiret("55210055400012");
        seller.setVatNumber("FR40552100554");
        seller.setVatExempt(exempt);
        seller.setAddress("18 rue du Sentier\nBât. B\n75002 Paris");
        seller.setIban("FR7630006000011234567890189");
        seller.setBic("BNPAFRPP");
        return seller;
    }

    private static InvoiceBuyer buyer() {
        InvoiceBuyer buyer = new InvoiceBuyer();
        buyer.setName("Société Générale");
        buyer.setAddress("29 boulevard Haussmann\nParis");
        buyer.setSiren("552120222");
        buyer.setVatNumber("FR27552120222");
        return buyer;
    }

    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static XPath xpath() {
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return NAMESPACES.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
            }

            @Override
            public String getPrefix(String namespaceUri) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceUri) {
                return null;
            }
        });
        return xpath;
    }

    private static String value(XPath xpath, Document xml, String expression) throws Exception {
        Object result = xpath.evaluate(expression, xml, XPathConstants.STRING);
        String text = (String) result;
        // count() answers a number as text, such as "2" (or "2.0" on some engines).
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
