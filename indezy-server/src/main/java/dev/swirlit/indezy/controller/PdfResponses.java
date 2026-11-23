package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.service.ActivityReportService.PdfFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Download responses for generated documents: PDFs, and other files such as accounting exports. */
public final class PdfResponses {

    private PdfResponses() {
    }

    /** The media type of a Word document. */
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** A copy from the freelancer's template: a Word document, or a PDF when the template is a fillable PDF form. */
    public static ResponseEntity<byte[]> copy(PdfFile file) {
        if (dev.swirlit.indezy.pdf.PdfFormTemplate.isPdf(file.content())) {
            return attachment(file.fileName().replaceFirst("\\.docx$", ".pdf"), MediaType.APPLICATION_PDF_VALUE, file.content());
        }
        return attachment(file.fileName(), DOCX, file.content());
    }

    public static ResponseEntity<byte[]> attachment(PdfFile file) {
        return attachment(file.fileName(), MediaType.APPLICATION_PDF_VALUE, file.content());
    }

    public static ResponseEntity<byte[]> attachment(String fileName, String contentType, byte[] content) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(MediaType.parseMediaType(contentType))
            .body(content);
    }
}
