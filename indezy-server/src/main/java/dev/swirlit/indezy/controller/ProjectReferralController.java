package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ProjectReferralDtos;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.ProjectReferralService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Missions' co-optations: who referred them, and the bonuses paid or received for them. */
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Opportunities and missions")
public class ProjectReferralController {

    private final ProjectReferralService referrals;
    private final AccessGuard accessGuard;

    @Operation(summary = "A mission's co-optation and its bonus", description = "204 when none was saved")
    @GetMapping("/{id}/referral")
    public ResponseEntity<ProjectReferralDtos.View> get(@PathVariable Long id) {
        accessGuard.requireProject(id);
        return referrals.get(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Save a mission's co-optation", description = "A bonus fixed, per day worked or a share of what the mission earns")
    @PutMapping("/{id}/referral")
    public ProjectReferralDtos.View save(@PathVariable Long id, @Valid @RequestBody ProjectReferralDtos.Referral request) {
        accessGuard.requireProject(id);
        return referrals.save(id, request);
    }

    @Operation(summary = "Remove a mission's co-optation")
    @DeleteMapping("/{id}/referral")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        accessGuard.requireProject(id);
        referrals.delete(id);
    }

    @Operation(summary = "The account's co-optations", description = "With the bonuses still to pay and to receive")
    @GetMapping("/referrals/{freelanceId}")
    public ProjectReferralDtos.Summary summary(@PathVariable Long freelanceId) {
        accessGuard.requireFreelance(freelanceId);
        return referrals.summary(freelanceId);
    }
}
