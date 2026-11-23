package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ProfileCvDto;
import dev.swirlit.indezy.model.ProfileCv;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.ProfileCvService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** The CV kept in the profile (see docs/features.md#profile-import). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Profile CV", description = "The CV kept in the profile, ready to download and attach")
public class ProfileCvController {

    private final ProfileCvService cvs;
    private final AccessGuard accessGuard;

    @Operation(summary = "Keep a CV in the profile, replacing the one kept", description = "A PDF, docx or odt of 10 MB at most")
    @PostMapping(value = "/users/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileCvDto keep(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.getSize() > ProfileCv.MAX_BYTES) {
            throw new IllegalArgumentException("A CV of 10 MB at most");
        }
        return cvs.keep(workspace(), file.getOriginalFilename(), file.getBytes());
    }

    @Operation(summary = "The CV kept, without its content; 204 when none is")
    @GetMapping("/users/cv/info")
    public ResponseEntity<ProfileCvDto> info() {
        return cvs.info(workspace()).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Download the CV kept")
    @GetMapping("/users/cv")
    public ResponseEntity<byte[]> download() {
        ProfileCv cv = cvs.file(workspace());
        return DocumentResponses.of(cv.getFileName(), cv.getContentType(), cv.getContent(), false);
    }

    @Operation(summary = "Remove the CV kept")
    @DeleteMapping("/users/cv")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove() {
        cvs.remove(workspace());
    }

    private Long workspace() {
        return accessGuard.requireWorkspace(null);
    }
}
