package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.NetworkFileDtos;
import dev.swirlit.indezy.service.NetworkFileService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/network/files")
@RequiredArgsConstructor
@Tag(name = "Network files", description = "Images and documents of posts and messages")
public class NetworkFileController {

    private final NetworkFileService files;

    @Operation(summary = "Upload an image or a document, then attach it to a post or a message",
        description = "Images (PNG, JPEG, GIF) are made anew, upright, 2048 pixels at most; documents are PDF, slides or spreadsheets " +
            "without macros, 50 MB at most; the answer names the workspace's clients, ESNs and contacts the document's text holds. " +
            "400 for another type, 409 beyond 60 a day")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public NetworkFileDtos.Uploaded upload(@RequestParam("file") MultipartFile file) {
        return files.upload(SecurityUtils.getCurrentUserId(), file);
    }

    @Operation(summary = "Drop one of the member's files not attached yet")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable Long id) {
        files.discard(SecurityUtils.getCurrentUserId(), id);
    }
}
