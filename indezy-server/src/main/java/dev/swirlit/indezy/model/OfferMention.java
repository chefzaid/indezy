package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;

/**
 * An offer mentions a profile term ({@link OfferTerm}), as {@code OfferMatch.mentions} reads it: profile matching joins
 * these rows instead of reading every offer's text.
 */
@Entity
@Table(name = "offer_mentions", indexes = @Index(name = "idx_offer_mentions_term", columnList = "term, offer_id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OfferMention {

    /** The offer and the term. */
    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "offer_id", nullable = false)
        private Long offerId;

        @Column(name = "term", nullable = false, length = 100)
        private String term;
    }

    @EmbeddedId
    private Key id;

    // Only for the foreign key: the rows go with their offer.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_offer_mentions_offer"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private JobOffer offer;

    public OfferMention(Long offerId, String term) {
        this(new Key(offerId, term), null);
    }
}
