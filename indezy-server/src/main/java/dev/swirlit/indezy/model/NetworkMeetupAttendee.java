package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.NetworkMeetupAttendance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A member's answer to an event: going or interested, and whether they were reminded the day before. */
@Entity
@Table(name = "network_meetup_attendees",
    uniqueConstraints = @UniqueConstraint(name = "uk_network_meetup_attendees", columnNames = {"meetup_id", "user_id"}))
@Getter
@Setter
public class NetworkMeetupAttendee extends BaseEntity {

    @Column(name = "meetup_id", nullable = false)
    private Long meetupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private NetworkMeetupAttendance status;

    @Column(name = "reminded")
    private Boolean reminded;
}
