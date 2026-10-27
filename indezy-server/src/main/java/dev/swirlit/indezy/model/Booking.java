package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A call a recruiter booked through the workspace's booking link, until the freelancer cancels it. */
@Entity
@Table(name = "bookings")
@Getter
@Setter
public class Booking extends BaseEntity {

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, length = 200)
    private String email;

    @Column(name = "company", length = 100)
    private String company;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Override
    public String toString() {
        return "Booking[" + getId() + "]";
    }
}
