package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.SupportDtos;
import dev.swirlit.indezy.model.SupportAttachment;
import dev.swirlit.indezy.service.SupportService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** Support requests: a member's own, and the administrators' list and answers. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Support", description = "Requests to support sent from the app, and their answers")
public class SupportController {

    private final SupportService support;

    @Operation(summary = "Send a request to support")
    @PostMapping("/support/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public SupportDtos.Request create(@Valid @RequestBody SupportDtos.NewRequest request) {
        return support.create(SecurityUtils.getCurrentUserId(), request);
    }

    @Operation(summary = "Add a screenshot or a PDF to a request just sent", description = "Up to 3 files of 5 MB, within an hour of sending")
    @PostMapping(value = "/support/requests/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SupportDtos.Attachment attach(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        if (file.getSize() > SupportAttachment.MAX_BYTES) {
            throw new IllegalArgumentException("A file holds 5 MB at most");
        }
        return support.attach(SecurityUtils.getCurrentUserId(), id, file.getOriginalFilename(), file.getBytes());
    }

    @Operation(summary = "A request's file", description = "Its author or an administrator")
    @GetMapping("/support/requests/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> file(@PathVariable Long id, @PathVariable Long attachmentId) {
        SupportAttachment file = support.file(SecurityUtils.getCurrentUserId(), id, attachmentId);
        return DocumentResponses.of(file.getFileName(), file.getContentType(), file.getContent(), file.getContentType().startsWith("image/"));
    }

    @Operation(summary = "The account's requests to support, the newest first, with their answers")
    @GetMapping("/support/requests")
    public List<SupportDtos.Request> mine() {
        return support.mine(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Every request to support, open ones first", description = "Administrators only")
    @GetMapping("/admin/support/requests")
    public List<SupportDtos.Received> received() {
        return support.received(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Answer a request to support, closing it on request", description = "Administrators only; emails the member")
    @PutMapping("/admin/support/requests/{id}")
    public SupportDtos.Request answer(@PathVariable Long id, @Valid @RequestBody SupportDtos.Answer answer) {
        return support.answer(SecurityUtils.getCurrentUserId(), id, answer);
    }
}
