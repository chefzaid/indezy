package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.EmailTemplateDto;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.EmailTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/email-templates")
@RequiredArgsConstructor
@Tag(name = "Email templates", description = "Messages written again and again, with variables filled in by the interface")
public class EmailTemplateController {

    private final EmailTemplateService templateService;
    private final AccessGuard accessGuard;

    @Operation(summary = "The workspace's email templates, by name")
    @GetMapping
    public List<EmailTemplateDto> list(@RequestParam(required = false) Long freelanceId) {
        return templateService.list(accessGuard.requireWorkspace(freelanceId));
    }

    @Operation(summary = "Save an email template")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<EmailTemplateDto> create(@Valid @RequestBody EmailTemplateDto request,
                                                   @RequestParam(required = false) Long freelanceId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.create(accessGuard.requireWorkspace(freelanceId), request));
    }

    @Operation(summary = "Change an email template")
    @PutMapping("/{id}")
    public EmailTemplateDto update(@PathVariable Long id, @Valid @RequestBody EmailTemplateDto request,
                                   @RequestParam(required = false) Long freelanceId) {
        return templateService.update(accessGuard.requireWorkspace(freelanceId), id, request);
    }

    @Operation(summary = "Delete an email template")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam(required = false) Long freelanceId) {
        templateService.delete(accessGuard.requireWorkspace(freelanceId), id);
        return ResponseEntity.noContent().build();
    }
}
