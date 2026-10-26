package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.InteractionChannel;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;

/**
 * One exchange with a contact: a message sent or received, a call, a meeting. The contact's last exchange, response
 * rate and preferred channel are read from them. They go with their contact, whichever way it is deleted.
 */
@Entity
@Table(name = "contact_interactions", indexes = @Index(name = "idx_contact_interactions_contact", columnList = "contact_id, occurred_on"))
@Getter
@Setter
public class ContactInteraction extends BaseEntity {

    public static final int SUMMARY_MAX = 500;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Contact contact;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private InteractionChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 20)
    private InteractionDirection direction;

    /** For an exchange the freelancer started: whether the contact answered; null while waiting or unknown. */
    @Column(name = "replied")
    private Boolean replied;

    @Column(name = "summary", length = SUMMARY_MAX)
    private String summary;
}
