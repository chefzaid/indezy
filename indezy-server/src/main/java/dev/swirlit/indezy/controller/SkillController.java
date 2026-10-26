package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.util.SkillNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/skills")
@Tag(name = "Skills", description = "One name per skill, whatever its spelling")
public class SkillController {

    @Operation(summary = "List the known skills with the other names they go by",
        description = "Spellings of one skill are matched, counted and suggested as one; the interface merges them with this list")
    @GetMapping("/aliases")
    public Map<String, List<String>> aliases() {
        return SkillNames.aliases();
    }
}
