package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.service.ActivityReportService.PdfFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Download responses for generated documents: PDFs, and other files such as accounting exports. */
final class PdfResponses {

    private PdfResponses() {
    }

    static ResponseEntity<byte[]> attachment(PdfFile file) {
        return attachment(file.fileName(), MediaType.APPLICATION_PDF_VALUE, file.content());
    }

    static ResponseEntity<byte[]> attachment(String fileName, String contentType, byte[] content) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(MediaType.parseMediaType(contentType))
            .body(content);
    }
}
