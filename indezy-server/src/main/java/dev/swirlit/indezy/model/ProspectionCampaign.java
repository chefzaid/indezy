package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * An announcement sent to contacts at once or over a few days (**Email my ESN contacts**): its emails' exchanges point
 * to it, so its results (answers, opportunities, missions won) are read from them.
 */
@Entity
@Table(name = "prospection_campaigns", indexes = @Index(name = "idx_prospection_campaigns_freelance", columnList = "freelance_id"))
@Getter
@Setter
public class ProspectionCampaign extends BaseEntity {

    public static final int NAME_MAX = 200;

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    /** What it is called: the emails' subject. */
    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;
}
