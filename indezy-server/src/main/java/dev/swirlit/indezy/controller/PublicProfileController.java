package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.PublicProfileDto;
import dev.swirlit.indezy.service.PublicProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/profiles")
@RequiredArgsConstructor
@Tag(name = "Public profiles", description = "Freelancers' profiles shown to anyone with the link")
public class PublicProfileController {

    private final PublicProfileService profiles;

    @Operation(summary = "A public profile by its address name", description = "404 for an unknown name and a profile turned off alike")
    @GetMapping("/{slug}")
    public PublicProfileDto view(@PathVariable String slug) {
        return profiles.view(slug);
    }
}
