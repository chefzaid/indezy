package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.service.NetworkFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** The network's files through the signed addresses handed to their readers, so that a page shows them as images. */
@RestController
@RequestMapping("/public/network-files")
@RequiredArgsConstructor
@Tag(name = "Network files", description = "Images and documents of posts and messages")
public class PublicNetworkFileController {

    private final NetworkFileService files;

    @Operation(summary = "A file of the network through its signed address", description = "404 when the address is wrong or expired")
    @GetMapping("/{id}/{variant}")
    public ResponseEntity<byte[]> serve(@PathVariable Long id, @PathVariable String variant, @RequestParam("e") long expires,
                                        @RequestParam("s") String signature) {
        NetworkFileService.Served served = files.serve(id, variant, expires, signature);
        ContentDisposition disposition = (served.inline() ? ContentDisposition.inline() : ContentDisposition.attachment())
            .filename(served.name(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(served.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "sandbox; default-src 'none'")
            .header("Cross-Origin-Resource-Policy", "same-site")
            .header("Referrer-Policy", "no-referrer")
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(served.maxAgeSeconds())).cachePrivate())
            .body(served.content());
    }
}
