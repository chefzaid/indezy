package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.UserDto;
import dev.swirlit.indezy.service.UserService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * What the browser extension fills a job board's application form with (see docs/features.md#browser-extension): the
 * name, the contact details, the links, the title, the target daily rate and the availability, read with a personal API
 * token (with no part ticked: this path belongs to none), the rest of the profile left out.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Profile", description = "The account's profile")
public class ApplicationProfileController {

    private final UserService users;

    /** The profile values an application form asks for. */
    public record ApplicationProfile(String firstName, String lastName, String email, String phone, String city, String linkedin,
                                     String github, String website, String position, Integer targetDailyRate, LocalDate availabilityDate) {
    }

    @Operation(summary = "The profile values an application form asks for", description = "Readable by an API token with no part ticked")
    @GetMapping("/application-profile")
    public ApplicationProfile applicationProfile() {
        UserDto user = users.getUserProfile(SecurityUtils.getCurrentUserId());
        return new ApplicationProfile(user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone(), user.getCity(),
            user.getLinkedin(), user.getGithub(), user.getWebsite(), user.getPosition(), user.getTargetDailyRate(), user.getAvailabilityDate());
    }
}
