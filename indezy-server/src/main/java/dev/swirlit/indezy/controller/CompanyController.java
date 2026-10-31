package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.CompanyDto;
import dev.swirlit.indezy.service.CompanyDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
@Tag(name = "Companies", description = "The French public company directory")
public class CompanyController {

    private final CompanyDirectory directory;

    @Operation(summary = "Look a company up", description = "Up to five companies of the public directory by name or SIREN")
    @GetMapping("/search")
    public List<CompanyDto> search(@RequestParam("q") String query) {
        return directory.search(query);
    }
}
