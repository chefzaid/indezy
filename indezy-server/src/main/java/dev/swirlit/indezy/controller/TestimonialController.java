package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.TestimonialDtos;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.TestimonialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The workspace's testimonials. Another workspace's testimonial or contact answers 404. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Testimonials", description = "Recommendations asked of contacts through a link, shown on the public profile once approved")
public class TestimonialController {

    private final TestimonialService testimonials;
    private final AccessGuard accessGuard;

    @Operation(summary = "The workspace's testimonials, the latest asked first")
    @GetMapping("/testimonials")
    public List<TestimonialDtos.Testimonial> list() {
        return testimonials.list(workspace());
    }

    @Operation(summary = "Ask a testimonial", description = "Of a contact or of someone named; the link's token is answered once")
    @PostMapping("/testimonials")
    @ResponseStatus(HttpStatus.CREATED)
    public TestimonialDtos.Link request(@Valid @RequestBody TestimonialDtos.Request request) {
        Long freelanceId = workspace();
        accessGuard.requireContactIfPresent(request.contactId());
        return testimonials.request(freelanceId, request);
    }

    @Operation(summary = "Show a received testimonial on the public profile, or hide it")
    @PutMapping("/testimonials/{id}/status")
    public TestimonialDtos.Testimonial setStatus(@PathVariable Long id, @Valid @RequestBody TestimonialDtos.StatusChange change) {
        return testimonials.setStatus(workspace(), id, change.status());
    }

    @Operation(summary = "Delete a testimonial, which its link then no longer opens")
    @DeleteMapping("/testimonials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        testimonials.delete(workspace(), id);
    }

    private Long workspace() {
        return accessGuard.requireWorkspace(null);
    }
}
