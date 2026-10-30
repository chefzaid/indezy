package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A term a profile measures offers by (a folded spelling of one of its skills), whose mentions are recorded for every
 * offer ({@link OfferMention}): once {@code indexed}, the offers recorded so far all have their mentions of it.
 */
@Entity
@Table(name = "offer_terms")
@Getter
@Setter
@NoArgsConstructor
public class OfferTerm {

    @Id
    @Column(name = "term", length = 100)
    private String term;

    @Column(name = "indexed", nullable = false)
    private boolean indexed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
