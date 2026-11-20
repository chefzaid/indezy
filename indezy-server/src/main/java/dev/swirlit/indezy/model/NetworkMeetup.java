package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * An event of the member network (a meetup in a place, or a webinar online) that a member organizes and others answer
 * Going or Interested; its times are Paris times.
 */
@Entity
@Table(name = "network_meetups")
@Getter
@Setter
public class NetworkMeetup extends BaseEntity {

    public static final int TITLE_MAX = 120;
    public static final int DESCRIPTION_MAX = 2000;
    public static final int PLACE_MAX = 200;
    public static final int LINK_MAX = 500;
    public static final int TAGS_MAX = 300;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @Column(name = "title", nullable = false, length = TITLE_MAX)
    private String title;

    @Column(name = "description", length = DESCRIPTION_MAX)
    private String description;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    /** When it ends; none reads as two hours after it starts. */
    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    /** Held online, at its link, rather than in a place. */
    @Column(name = "online_event")
    private Boolean online;

    /** The address, for an event in a place. */
    @Column(name = "place", length = PLACE_MAX)
    private String place;

    /** Where it is held online, or its page. */
    @Column(name = "link", length = LINK_MAX)
    private String link;

    /** The tags it names, lower-case and comma-separated, for their pages. */
    @Column(name = "tags", length = TAGS_MAX)
    private String tags;

    /** Cancelled by its organizer or the administrators: its attendees were told. */
    @Column(name = "cancelled")
    private Boolean cancelled;

    public boolean isOnline() {
        return Boolean.TRUE.equals(online);
    }

    public boolean isCancelled() {
        return Boolean.TRUE.equals(cancelled);
    }

    /** When it ends, two hours after it starts when no end is set. */
    public LocalDateTime endsOrDefault() {
        return endsAt != null ? endsAt : startsAt.plusHours(2);
    }

    public List<String> tagList() {
        return tags == null || tags.isBlank() ? List.of() : Arrays.stream(tags.split(",")).filter(tag -> !tag.isBlank()).toList();
    }
}
