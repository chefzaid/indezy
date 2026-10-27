package dev.swirlit.indezy.pdf;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.GregorianCalendar;
import java.util.Map;

/**
 * Makes a rendered invoice a Factur-X file: PDF/A-3B (sRGB output intent, XMP metadata matching the document
 * information) with its Cross Industry Invoice embedded as {@code factur-x.xml}, related as the alternative of the
 * printed invoice, and the Factur-X extension schema declared in the XMP.
 */
final class FacturXPdf {

    private static final String SRGB = "sRGB IEC61966-2.1";
    private static final String FX_NAMESPACE = "urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#";

    private FacturXPdf() {
    }

    static void apply(PDDocument document, String title, String author, byte[] xml, ZonedDateTime when) throws IOException {
        // The document information and the XMP must state the same date, which PDF dates give to the second.
        ZonedDateTime now = when.truncatedTo(ChronoUnit.SECONDS);
        document.setVersion(1.7f);
        GregorianCalendar date = GregorianCalendar.from(now);
        PDDocumentInformation information = document.getDocumentInformation();
        information.setTitle(title);
        information.setAuthor(author);
        information.setCreator("Indezy");
        information.setProducer("Indezy");
        information.setCreationDate(date);
        information.setModificationDate(date);

        PDDocumentCatalog catalog = document.getDocumentCatalog();
        PDMetadata metadata = new PDMetadata(document);
        metadata.importXMPMetadata(xmp(title, author, now).getBytes(StandardCharsets.UTF_8));
        catalog.setMetadata(metadata);

        PDOutputIntent intent = new PDOutputIntent(document, new ByteArrayInputStream(ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData()));
        intent.setInfo(SRGB);
        intent.setOutputCondition(SRGB);
        intent.setOutputConditionIdentifier(SRGB);
        intent.setRegistryName("http://www.color.org");
        catalog.addOutputIntent(intent);

        PDEmbeddedFile embedded = new PDEmbeddedFile(document, new ByteArrayInputStream(xml));
        embedded.setSubtype("text/xml");
        embedded.setSize(xml.length);
        embedded.setCreationDate(date);
        embedded.setModDate(date);
        PDComplexFileSpecification file = new PDComplexFileSpecification();
        file.setFile(FacturXml.FILE_NAME);
        file.setFileUnicode(FacturXml.FILE_NAME);
        file.setFileDescription("Factur-X invoice");
        file.setEmbeddedFile(embedded);
        file.setEmbeddedFileUnicode(embedded);
        // EN 16931: the XML says the same as the printed invoice.
        file.getCOSObject().setName(COSName.getPDFName("AFRelationship"), "Alternative");
        PDEmbeddedFilesNameTreeNode files = new PDEmbeddedFilesNameTreeNode();
        files.setNames(Map.of(FacturXml.FILE_NAME, file));
        PDDocumentNameDictionary names = new PDDocumentNameDictionary(catalog);
        names.setEmbeddedFiles(files);
        catalog.setNames(names);
        COSArray associated = new COSArray();
        associated.add(file);
        catalog.getCOSObject().setItem(COSName.getPDFName("AF"), associated);
    }

    private static String xmp(String title, String author, ZonedDateTime now) {
        String date = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n" +
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n" +
            "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
            "<rdf:Description rdf:about=\"\" xmlns:pdfaid=\"http://www.aiim.org/pdfa/ns/id/\">\n" +
            "<pdfaid:part>3</pdfaid:part>\n<pdfaid:conformance>B</pdfaid:conformance>\n</rdf:Description>\n" +
            "<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
            "<dc:format>application/pdf</dc:format>\n" +
            "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">" + FacturXml.escape(title) + "</rdf:li></rdf:Alt></dc:title>\n" +
            "<dc:creator><rdf:Seq><rdf:li>" + FacturXml.escape(author) + "</rdf:li></rdf:Seq></dc:creator>\n</rdf:Description>\n" +
            "<rdf:Description rdf:about=\"\" xmlns:pdf=\"http://ns.adobe.com/pdf/1.3/\">\n<pdf:Producer>Indezy</pdf:Producer>\n" +
            "</rdf:Description>\n" +
            "<rdf:Description rdf:about=\"\" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">\n<xmp:CreatorTool>Indezy</xmp:CreatorTool>\n" +
            "<xmp:CreateDate>" + date + "</xmp:CreateDate>\n<xmp:ModifyDate>" + date + "</xmp:ModifyDate>\n</rdf:Description>\n" +
            "<rdf:Description rdf:about=\"\" xmlns:fx=\"" + FX_NAMESPACE + "\">\n" +
            // A credit note is an INVOICE document too: its XML's type code (381) says it credits.
            "<fx:DocumentType>INVOICE</fx:DocumentType>\n" +
            "<fx:DocumentFileName>" + FacturXml.FILE_NAME + "</fx:DocumentFileName>\n<fx:Version>1.0</fx:Version>\n" +
            "<fx:ConformanceLevel>EN 16931</fx:ConformanceLevel>\n</rdf:Description>\n" +
            "<rdf:Description rdf:about=\"\" xmlns:pdfaExtension=\"http://www.aiim.org/pdfa/ns/extension/\"" +
            " xmlns:pdfaSchema=\"http://www.aiim.org/pdfa/ns/schema#\" xmlns:pdfaProperty=\"http://www.aiim.org/pdfa/ns/property#\">\n" +
            "<pdfaExtension:schemas><rdf:Bag><rdf:li rdf:parseType=\"Resource\">\n" +
            "<pdfaSchema:schema>Factur-X PDFA Extension Schema</pdfaSchema:schema>\n" +
            "<pdfaSchema:namespaceURI>" + FX_NAMESPACE + "</pdfaSchema:namespaceURI>\n<pdfaSchema:prefix>fx</pdfaSchema:prefix>\n" +
            "<pdfaSchema:property><rdf:Seq>\n" +
            property("DocumentFileName", "The name of the embedded XML document") +
            property("DocumentType", "The type of the hybrid document in capital letters, e.g. INVOICE or ORDER") +
            property("Version", "The actual version of the standard applying to the embedded XML document") +
            property("ConformanceLevel", "The conformance level of the embedded XML document") +
            "</rdf:Seq></pdfaSchema:property>\n</rdf:li></rdf:Bag></pdfaExtension:schemas>\n</rdf:Description>\n" +
            "</rdf:RDF>\n</x:xmpmeta>\n<?xpacket end=\"w\"?>";
    }

    private static String property(String name, String description) {
        return "<rdf:li rdf:parseType=\"Resource\"><pdfaProperty:name>" + name + "</pdfaProperty:name>" +
            "<pdfaProperty:valueType>Text</pdfaProperty:valueType><pdfaProperty:category>external</pdfaProperty:category>" +
            "<pdfaProperty:description>" + description + "</pdfaProperty:description></rdf:li>\n";
    }
}
