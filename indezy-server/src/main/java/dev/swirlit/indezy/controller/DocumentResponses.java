package dev.swirlit.indezy.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** Stored documents served so the browser runs nothing in them and keeps no copy. */
final class DocumentResponses {

    private DocumentResponses() {
    }

    static ResponseEntity<byte[]> of(String fileName, String contentType, byte[] content, boolean inline) {
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
            .filename(fileName, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "sandbox; default-src 'none'")
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(content);
    }
}
