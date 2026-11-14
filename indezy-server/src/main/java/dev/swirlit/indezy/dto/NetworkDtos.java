package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.NetworkAvailability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;

/** The member network: an account's membership, its member card, and the requests that join, change and leave. */
public final class NetworkDtos {

    /** Lower-case letters, digits and underscores, 3 to 30, starting with a letter. */
    public static final String HANDLE = "^[a-z][a-z0-9_]{2,29}$";

    private NetworkDtos() {
    }

    /**
     * The account's membership: whether it joined, its handle (a suggested one before joining), the card's fields shown,
     * and the card as members see it (null before joining).
     */
    public record Membership(boolean joined, String handle, Fields fields, Availability availability, MemberCard card) {
    }

    /** How the card tells the availability: from the won missions, or set by hand with the day it names, if any. */
    public record Availability(@NotNull NetworkAvailability mode, LocalDate date) {
    }

    /** What the card shows of the profile, each on or off, and whether the member works remotely. */
    public record Fields(boolean photo, boolean headline, boolean stack, boolean location, boolean availability, boolean languages,
                         boolean remote) {
    }

    /** Joins, or changes the membership: the handle and the card's fields. */
    public record Join(@NotNull @Pattern(regexp = HANDLE) String handle, @NotNull Fields fields, @Valid Availability availability) {
    }

    public record HandleAvailability(String handle, boolean available) {
    }

    /**
     * A member's page as a member sees it: the card, how many members follow them and they follow, the skills they share
     * with the viewer, whether the viewer follows them and they follow the viewer, and whether it is the viewer's own.
     */
    public record MemberPage(MemberCard card, int followers, int following, List<String> sharedSkills, boolean followed,
                             boolean followsYou, boolean own) {

        public MemberPage {
            sharedSkills = List.copyOf(sharedSkills);
        }
    }

    /**
     * What members see of a member: the name always, the rest as the member chose; a hidden field is null or empty. The
     * availability is a status (available, on a mission, open to hear of one) with the day it names, if any.
     */
    public record MemberCard(String handle, String firstName, String lastName, String avatar, String headline, List<String> stack,
                             String city, boolean remote, NetworkAvailability availability, LocalDate availabilityDate,
                             List<String> languages) {

        public MemberCard {
            stack = List.copyOf(stack);
            languages = List.copyOf(languages);
        }

        // Spring MVC logs bodies through toString at DEBUG: nothing about the person (see docs/security.md).
        @Override
        public String toString() {
            return "MemberCard[handle=" + handle + "]";
        }
    }
}
