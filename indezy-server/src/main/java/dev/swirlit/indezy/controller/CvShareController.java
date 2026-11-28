package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.CvShareDtos;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.CvShareService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The links the workspace's CV profiles are shared through. Another workspace's link answers 404. */
@RestController
@RequiredArgsConstructor
@Tag(name = "CV profiles", description = "Named positionings of the profile, each drawn as its own dossier de compétences")
public class CvShareController {

    private final CvShareService shares;
    private final AccessGuard accessGuard;

    @Operation(summary = "The links CV profiles were shared through, the latest first")
    @GetMapping("/users/cv-shares")
    public List<CvShareDtos.Share> list() {
        return shares.list(accessGuard.requireWorkspace(null));
    }

    @Operation(summary = "Share a CV profile through a link of its own", description = "1 to 30 days, 7 by default; the token is shown this once")
    @PostMapping("/users/cv-shares")
    @ResponseStatus(HttpStatus.CREATED)
    public CvShareDtos.CreatedShare create(@Valid @RequestBody CvShareDtos.ShareRequest request) {
        return shares.create(SecurityUtils.getCurrentUserId(), accessGuard.requireWorkspace(null), request);
    }

    @Operation(summary = "Close a link for good", description = "It stays listed, revoked")
    @PutMapping("/users/cv-shares/{id}/revoke")
    public CvShareDtos.Share revoke(@PathVariable Long id) {
        return shares.revoke(accessGuard.requireWorkspace(null), id);
    }
}
