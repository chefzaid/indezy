package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.service.EsnScorecardService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The ESN scorecards across members, for the network's members: named ESNs, figures once five freelancers back them. */
@RestController
@RequestMapping("/network/esn-scorecards")
@RequiredArgsConstructor
@Tag(name = "ESN scorecards", description = "How ESNs deal with freelancers, from the records members share")
public class EsnScorecardController {

    private final EsnScorecardService scorecards;

    @Operation(summary = "The ESNs five freelancers or more back, with their offer rate, process length, margin, answer time and payment delay")
    @GetMapping
    public EsnScorecardService.Board board() {
        return scorecards.board(SecurityUtils.getCurrentUserId());
    }
}
