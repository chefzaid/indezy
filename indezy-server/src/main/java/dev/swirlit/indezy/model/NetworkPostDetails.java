package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** The facts of a mission to pass on or of an availability, shown on its card. */
@Embeddable
@Getter
@Setter
public class NetworkPostDetails {

    public static final int FIELD_MAX = 100;
    public static final int STACK_MAX = 400;

    @Column(name = "details_role", length = FIELD_MAX)
    private String role;

    /** The daily rate range, in euros. */
    @Column(name = "details_rate_min")
    private Integer rateMin;

    @Column(name = "details_rate_max")
    private Integer rateMax;

    @Column(name = "details_city", length = FIELD_MAX)
    private String city;

    /** Remote days a week, 0 to 5. */
    @Column(name = "details_remote_days")
    private Integer remoteDays;

    /** When the mission starts, or the author is available. */
    @Column(name = "details_start")
    private LocalDate start;

    /** The client, only when the author names it. */
    @Column(name = "details_client", length = FIELD_MAX)
    private String client;

    /** The skills, comma-separated. */
    @Column(name = "details_stack", length = STACK_MAX)
    private String stack;
}
