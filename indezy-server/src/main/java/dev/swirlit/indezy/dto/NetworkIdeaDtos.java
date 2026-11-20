package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.NetworkIdeaStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** The ideas board of the member network, as the API reads and writes it. */
public final class NetworkIdeaDtos {

    private NetworkIdeaDtos() {
    }

    /**
     * An idea as a member reads it: its votes and whether they voted, whether they proposed it, who did (none once that
     * member is gone), the administrators' status and word, and the idea it was merged into.
     */
    public record Idea(Long id, String title, String description, NetworkIdeaStatus status, String statusNote, long votes, boolean voted,
                       boolean own, NetworkDtos.MemberCard author, LocalDateTime createdAt, LocalDateTime statusChangedAt, NetworkDtos.IdeaRef mergedInto) {
    }

    /** A page of the board, 20 ideas, and whether the reader administers it (sets statuses, merges duplicates). */
    public record Ideas(List<Idea> ideas, int page, boolean more, boolean administrator) {

        public Ideas {
            ideas = List.copyOf(ideas);
        }
    }

    /** An idea to propose, or its title and description changed. */
    public record NewIdea(@NotBlank @Size(min = 5, max = 120) String title, @Size(max = 2000) String description) {
    }

    /** The administrators' answer: a status (not merged, which merging sets) and an optional word. */
    public record StatusChange(@NotNull NetworkIdeaStatus status, @Size(max = 500) String note) {
    }
}
