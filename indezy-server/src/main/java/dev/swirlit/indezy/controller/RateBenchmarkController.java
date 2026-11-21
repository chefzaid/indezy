package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.RateBenchmarkDtos;
import dev.swirlit.indezy.model.enums.RateRegion;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.service.RateBenchmarkService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The network's rate benchmark, for its members: shared rates, anonymized, shown once five freelancers back a figure. */
@RestController
@RequestMapping("/network/benchmark")
@RequiredArgsConstructor
@Tag(name = "Rate benchmark", description = "Daily rates members share, by skill, seniority and region")
public class RateBenchmarkController {

    private final RateBenchmarkService benchmark;

    @Operation(summary = "The skills enough freelancers back to give a figure")
    @GetMapping("/skills")
    public RateBenchmarkDtos.Skills skills() {
        return benchmark.skills(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "The median daily rate and the middle half of a skill, for a seniority and a region or all of them")
    @GetMapping
    public RateBenchmarkDtos.Figure figure(@RequestParam @NotBlank @Size(max = 60) String skill, @RequestParam(required = false) Seniority seniority,
                                           @RequestParam(required = false) RateRegion region) {
        return benchmark.figure(SecurityUtils.getCurrentUserId(), skill, seniority, region);
    }
}
