package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.BillingIdentityDto;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.BillingIdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The seller's identity on the signed-in workspace's invoices. */
@RestController
@RequestMapping("/users/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "The seller's identity on invoices")
public class BillingController {

    private final BillingIdentityService billing;
    private final AccessGuard accessGuard;

    @Operation(summary = "The seller's identity, its defaults drawn from the profile")
    @GetMapping
    public BillingIdentityDto get() {
        return billing.get(workspace());
    }

    @Operation(summary = "Set the seller's identity")
    @PutMapping
    public BillingIdentityDto update(@Valid @RequestBody BillingIdentityDto request) {
        return billing.update(workspace(), request);
    }

    private Long workspace() {
        return accessGuard.currentFreelanceId().orElseThrow(() -> new AccessDeniedException("No workspace for this account"));
    }
}
