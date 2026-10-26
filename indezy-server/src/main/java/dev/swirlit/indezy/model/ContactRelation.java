package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Two contacts who work together, often across companies (an ESN's account manager and the client's project lead).
 * The link goes both ways and is stored once, the lower id first; it goes with either contact.
 */
@Entity
@Table(name = "contact_relations", uniqueConstraints = @UniqueConstraint(name = "uk_contact_relation",
    columnNames = {"first_contact_id", "second_contact_id"}))
@Getter
@Setter
public class ContactRelation extends BaseEntity {

    public static final int NOTE_MAX = 100;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "first_contact_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Contact first;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "second_contact_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Contact second;

    /** How they work together ("account manager for the project"), optional. */
    @Column(name = "note", length = NOTE_MAX)
    private String note;

    /** The contact of the two that is not {@code contactId}. */
    public Contact other(Long contactId) {
        return first.getId().equals(contactId) ? second : first;
    }
}
