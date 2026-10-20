package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.OfferPublisherBlockDto;
import dev.swirlit.indezy.dto.OfferPublisherBlockRequest;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.OfferPublisherBlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The publishers whose offers the caller's workspace keeps out of its feed. {@code freelanceId} is only read when
 * security checks are off (tests).
 */
@RestController
@RequestMapping("/offers/blocked-publishers")
@RequiredArgsConstructor
@Tag(name = "Offers", description = "Freelance missions collected from the job boards")
public class OfferPublisherBlockController {

    private final OfferPublisherBlockService blocks;
    private final AccessGuard accessGuard;

    @Operation(summary = "List the publishers the workspace blocked")
    @GetMapping
    public List<OfferPublisherBlockDto> list(@RequestParam(required = false) Long freelanceId) {
        return blocks.list(accessGuard.requireWorkspace(freelanceId));
    }

    @Operation(summary = "Keep a publisher's offers out of the workspace's feed; blocking it again answers the same block")
    @PostMapping
    public OfferPublisherBlockDto block(@Valid @RequestBody OfferPublisherBlockRequest request, @RequestParam(required = false) Long freelanceId) {
        return blocks.block(accessGuard.requireWorkspace(freelanceId), request.name());
    }

    @Operation(summary = "Bring a blocked publisher's offers back")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unblock(@PathVariable Long id, @RequestParam(required = false) Long freelanceId) {
        blocks.unblock(accessGuard.requireWorkspace(freelanceId), id);
        return ResponseEntity.noContent().build();
    }
}
