package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.NetworkIdeaDtos;
import dev.swirlit.indezy.model.enums.NetworkIdeaStatus;
import dev.swirlit.indezy.service.NetworkIdeaService;
import dev.swirlit.indezy.util.SecurityUtils;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The member network's ideas board: ideas for Indezy that members propose and vote for, answered by the administrators. */
@RestController
@RequestMapping("/network/ideas")
@RequiredArgsConstructor
@Tag(name = "Network", description = "The member network of freelancers, joined by choice")
public class NetworkIdeaController {

    private final NetworkIdeaService ideas;

    @Operation(summary = "A page of the ideas board, 20 ideas", description = "sort TOP (the most voted first) or NEW; status narrows it " +
        "(all but the merged when none); q to the ideas whose title or description holds it")
    @GetMapping
    public NetworkIdeaDtos.Ideas list(@RequestParam(defaultValue = "TOP") NetworkIdeaService.Order sort,
                                      @RequestParam(required = false) NetworkIdeaStatus status,
                                      @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page) {
        return ideas.list(SecurityUtils.getCurrentUserId(), sort, status, q, page);
    }

    @Operation(summary = "Propose an idea, the author's vote counted", description = "409 past 5 a day")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NetworkIdeaDtos.Idea create(@Valid @RequestBody NetworkIdeaDtos.NewIdea request) {
        return ideas.create(SecurityUtils.getCurrentUserId(), request);
    }

    @Operation(summary = "One idea", description = "A merged one names the idea it was merged into")
    @GetMapping("/{id}")
    public NetworkIdeaDtos.Idea get(@PathVariable Long id) {
        return ideas.get(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "Change an idea's title and description: its author, while it is open")
    @PutMapping("/{id}")
    public NetworkIdeaDtos.Idea update(@PathVariable Long id, @Valid @RequestBody NetworkIdeaDtos.NewIdea request) {
        return ideas.update(SecurityUtils.getCurrentUserId(), id, request);
    }

    @Operation(summary = "Delete an idea", description = "Its author while it is open and only they voted for it, or the administrators")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ideas.delete(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "Vote for an idea", description = "409 for one done, declined or merged")
    @PutMapping("/{id}/vote")
    public NetworkIdeaDtos.Idea vote(@PathVariable Long id) {
        return ideas.vote(SecurityUtils.getCurrentUserId(), id, true);
    }

    @Operation(summary = "Take a vote back")
    @DeleteMapping("/{id}/vote")
    public NetworkIdeaDtos.Idea unvote(@PathVariable Long id) {
        return ideas.vote(SecurityUtils.getCurrentUserId(), id, false);
    }

    @Operation(summary = "Answer an idea with a status and a word: the administrators", description = "Its voters are told when the status changes")
    @PutMapping("/{id}/status")
    public NetworkIdeaDtos.Idea status(@PathVariable Long id, @Valid @RequestBody NetworkIdeaDtos.StatusChange change) {
        return ideas.status(SecurityUtils.getCurrentUserId(), id, change);
    }

    @Operation(summary = "Merge a duplicate into the idea it repeats, moving its votes: the administrators", description = "Answers the idea kept")
    @PutMapping("/{id}/merge")
    public NetworkIdeaDtos.Idea merge(@PathVariable Long id, @RequestParam Long into) {
        return ideas.merge(SecurityUtils.getCurrentUserId(), id, into);
    }
}
