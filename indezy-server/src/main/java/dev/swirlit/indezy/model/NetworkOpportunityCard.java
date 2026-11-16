package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * An opportunity shared in a message, as a card: the facts the sender's opportunity held when sent (never its client),
 * and the opportunity it became in the recipient's pipeline once they added it.
 */
@Embeddable
@Getter
@Setter
public class NetworkOpportunityCard {

    public static final int FIELD_MAX = 255;

    @Column(name = "card_role", length = FIELD_MAX)
    private String role;

    /** The daily rate, in euros. */
    @Column(name = "card_rate")
    private Integer dailyRate;

    @Column(name = "card_city", length = FIELD_MAX)
    private String city;

    @Column(name = "card_start")
    private LocalDate startDate;

    @Column(name = "card_remote_days")
    private Integer remoteDaysPerMonth;

    @Column(name = "card_stack", length = FIELD_MAX)
    private String techStack;

    /** The recipient's opportunity made from the card; none until they add it to their pipeline. */
    @Column(name = "card_taken_project_id")
    private Long takenProjectId;
}
