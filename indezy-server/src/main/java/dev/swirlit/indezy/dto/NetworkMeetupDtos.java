package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.NetworkMeetupAttendance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** The member network's events, as the API reads and writes them. */
public final class NetworkMeetupDtos {

    private NetworkMeetupDtos() {
    }

    /**
     * An event as a member reads it: when and where, its tags, who organizes it, how many go and are interested, the
     * reader's answer, and whether they organize it or may change it (its organizer, or an administrator).
     */
    public record Meetup(Long id, String title, String description, LocalDateTime startsAt, LocalDateTime endsAt, boolean online, String place,
                         String link, List<String> tags, NetworkDtos.MemberCard organizer, long going, long interested,
                         NetworkMeetupAttendance mine, boolean own, boolean editable, boolean cancelled) {

        public Meetup {
            tags = List.copyOf(tags);
        }
    }

    /** A page of events, 20 of them. */
    public record Meetups(List<Meetup> meetups, int page, boolean more) {

        public Meetups {
            meetups = List.copyOf(meetups);
        }
    }

    /** An event to organize, or its details changed. */
    public record NewMeetup(@NotBlank @Size(min = 3, max = 120) String title, @Size(max = 2000) String description, @NotNull LocalDateTime startsAt,
                            LocalDateTime endsAt, boolean online, @Size(max = 200) String place, @Size(max = 500) String link,
                            @Size(max = 10) List<@Size(max = 30) String> tags) {

        public NewMeetup {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    /** Who answered an event: those going, then those interested. */
    public record Attendees(List<NetworkDtos.MemberCard> going, List<NetworkDtos.MemberCard> interested) {

        public Attendees {
            going = List.copyOf(going);
            interested = List.copyOf(interested);
        }
    }
}
