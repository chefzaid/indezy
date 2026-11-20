package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The member network's communities, as the API reads and writes them. */
public final class NetworkCommunityDtos {

    private NetworkCommunityDtos() {
    }

    /** A community as a member reads it: how many are in it, whether they are, and whether they moderate it. */
    public record Community(String slug, String name, String description, int members, boolean joined, boolean moderator) {
    }

    /** A community's page: the community, its moderators and whether the reader created it or administers the network (who names moderators and deletes it). */
    public record Page(Community community, List<NetworkDtos.MemberCard> moderators, boolean creator) {

        public Page {
            moderators = List.copyOf(moderators);
        }
    }

    /** A community to create, or its name and description changed. */
    public record NewCommunity(@NotBlank @Size(max = 60) String name, @Size(max = 500) String description) {
    }

    /** A post in a community. */
    public record NewCommunityPost(@NotBlank @Size(max = 3000) String text) {
    }

    /** A member of a community, for its moderators: their card and whether they moderate it. */
    public record CommunityMember(NetworkDtos.MemberCard card, boolean moderator) {
    }
}
