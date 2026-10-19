package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.service.ActivityReportService.PdfFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Download responses for generated PDF documents. */
final class PdfResponses {

    private PdfResponses() {
    }

    static ResponseEntity<byte[]> attachment(PdfFile file) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(MediaType.APPLICATION_PDF)
            .body(file.content());
    }
}
