package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ProjectAttachmentDto;
import dev.swirlit.indezy.model.ProjectAttachment;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.ProjectAttachmentService;
import dev.swirlit.indezy.util.AttachmentTypes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@Tag(name = "Project attachments", description = "Files kept with an opportunity")
public class ProjectAttachmentController {

    private final ProjectAttachmentService attachmentService;
    private final AccessGuard accessGuard;

    @Operation(summary = "An opportunity's files, newest first, without their content")
    @GetMapping("/{projectId}/attachments")
    public List<ProjectAttachmentDto> list(@PathVariable Long projectId) {
        accessGuard.requireProject(projectId);
        return attachmentService.list(projectId);
    }

    @Operation(summary = "Attach a PDF, image, Word, OpenDocument or text file of up to 10 MB to an opportunity")
    @PostMapping(value = "/{projectId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectAttachmentDto> upload(@PathVariable Long projectId, @RequestParam("file") MultipartFile file)
        throws IOException {
        accessGuard.requireProject(projectId);
        return ResponseEntity.status(HttpStatus.CREATED).body(attachmentService.upload(projectId, file));
    }

    /**
     * A file's content: PDF and images to show in the page, anything else to download. The type is the one read from
     * the content when it was attached, and the response may not run anything in the browser.
     */
    @Operation(summary = "A file's content, inline for PDF and images")
    @GetMapping("/attachments/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long id) {
        accessGuard.requireProjectAttachment(id);
        ProjectAttachment attachment = attachmentService.get(id);
        ContentDisposition disposition = (AttachmentTypes.isInline(attachment.getContentType()) ?
            ContentDisposition.inline() : ContentDisposition.attachment())
            .filename(attachment.getFileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(attachment.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "sandbox; default-src 'none'")
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(attachment.getContent());
    }

    @Operation(summary = "Delete a file of an opportunity")
    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        accessGuard.requireProjectAttachment(id);
        attachmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
