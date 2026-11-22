package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.ai.ProfileImportAiService;
import dev.swirlit.indezy.service.LinkedInProfileImport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;

/** The profile filled from what the freelancer wrote elsewhere, read without AI and nothing kept (see docs/features.md#profile-import). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Profile import", description = "The profile's fields read from LinkedIn's data archive, to review before saving")
public class ProfileImportController {

    private final Clock clock;

    @Operation(summary = "Read LinkedIn's data archive into the profile's fields", description = "The ZIP of Get a copy of your data, 20 MB at most")
    @PostMapping(value = "/users/profile-import/linkedin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileImportAiService.Reading linkedin(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > LinkedInProfileImport.MAX_BYTES) {
            throw new IllegalArgumentException("LinkedIn's archive holds 20 MB at most");
        }
        return LinkedInProfileImport.read(file.getBytes(), LocalDate.now(clock));
    }
}
